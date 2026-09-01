"""Revision workflow service (append-only, idempotent, concurrency-safe).

Implements the Phase 4 brief's ENDPOINT-7 contract:
    authenticate -> validate parcel -> load master -> load latest revision ->
    merge -> validate snapshot -> diff -> allocate revision_no under row lock ->
    INSERT survey_changes -> audit event.

survey_master is never touched; prior revisions are never modified.
"""
import uuid
from dataclasses import dataclass

from django.db import transaction
from django.utils import timezone

from surveys.api.errors import (
    ApiError,
    ParcelNotFound,
)
from surveys.models import Parcel, SurveyChange

# WB2-schema fields a client may set (doc 02 Part E). Everything else is
# server-derived; unknown/protected keys are rejected as VALIDATION_ERROR.
ALLOWED_PAYLOAD_FIELDS = {
    "rd_value", "latitude", "longitude", "package_no", "village",
    "owner_name", "father_name", "cnic_no", "khasra_number", "mauza_number",
    "contact_number", "land_owner_doc", "electricity_connection_name", "land_area",
    "structure_status", "structure_name", "length_ft", "width_ft",
    "area_sqft", "construction_nature",
}

NUMERIC_PAYLOAD_FIELDS = {
    "rd_value", "latitude", "longitude", "land_area",
    "length_ft", "width_ft", "area_sqft",
}
TEXT_LIMITS = {
    "package_no": 32, "village": 128, "owner_name": 255, "father_name": 255,
    "cnic_no": 15, "khasra_number": 64, "mauza_number": 64, "contact_number": 20,
    "land_owner_doc": 128, "electricity_connection_name": 128,
    "structure_status": 64, "structure_name": 255, "construction_nature": 32,
}
MAX_CHANGE_REASON = 255


@dataclass
class RevisionOutcome:
    created: bool                 # False => replayed by client_uuid
    revision: SurveyChange
    diff: dict


def master_baseline(parcel: Parcel) -> dict:
    """Effective ORIGINAL state mapped into the WB2 payload schema."""
    first = parcel.master_lines.order_by("sr_no").first()
    if first is None:
        return {key: None for key in ALLOWED_PAYLOAD_FIELDS} | {
            "package_no": "PKG-14",
            "village": parcel.village,
            "owner_name": parcel.owner_name_current,
        }
    return {
        "rd_value": None,
        "latitude": float(first.latitude) if first.latitude is not None else None,
        "longitude": float(first.longitude) if first.longitude is not None else None,
        "package_no": "PKG-14",
        "village": first.village,
        "owner_name": first.owner_name,
        "father_name": first.father_name,
        "cnic_no": None,
        "khasra_number": None,
        "mauza_number": None,
        "contact_number": None,
        "land_owner_doc": first.ownership_documents,
        "electricity_connection_name": None,
        "land_area": None,
        "structure_status": first.structure_status,
        "structure_name": first.structure_name,
        "length_ft": float(first.length_ft) if first.length_ft is not None else None,
        "width_ft": float(first.width_ft) if first.width_ft is not None else None,
        "area_sqft": float(first.area_value) if first.area_value is not None else None,
        "construction_nature": first.construction_nature,
    }


def current_state(parcel: Parcel) -> tuple[dict, SurveyChange | None]:
    """(effective state, latest revision-or-None)."""
    latest = parcel.revisions.select_related("changed_by").order_by(
        "-revision_no").first()
    if latest is None:
        return master_baseline(parcel), None
    return latest.full_payload, latest


def _validate_payload(data: dict) -> None:
    if not isinstance(data, dict):
        raise ApiError("`data` must be an object.", code="VALIDATION_ERROR")
    unknown = sorted(set(data) - ALLOWED_PAYLOAD_FIELDS)
    if unknown:
        raise ApiError(
            "Unknown or protected fields submitted.",
            code="VALIDATION_ERROR",
            details={"unknown_fields": unknown},
        )
    for key, value in data.items():
        if value is None:
            continue
        limit = TEXT_LIMITS.get(key)
        if limit and not isinstance(value, str):
            raise ApiError(f"{key} must be a string.", code="VALIDATION_ERROR",
                           details={key: "string expected"})
        if limit and len(value) > limit:
            raise ApiError(f"{key} exceeds {limit} characters.",
                           code="VALIDATION_ERROR", details={key: "too_long"})
        if key in NUMERIC_PAYLOAD_FIELDS:
            try:
                num = float(value)
            except (TypeError, ValueError):
                raise ApiError(f"{key} must be numeric.",
                               code="VALIDATION_ERROR", details={key: str(value)})
            if num < 0 or num > 1_000_000:
                raise ApiError(f"{key} out of range.",
                               code="VALIDATION_ERROR", details={key: "range"})


def compute_diff(old_state: dict, new_state: dict) -> dict:
    """Field-level diff with explicit NULL transitions. Only real changes."""
    diff = {}
    for key in sorted(set(old_state) | set(new_state)):
        old_value, new_value = old_state.get(key), new_state.get(key)
        if old_value != new_value:
            diff[key] = {"old": old_value, "new": new_value}
    return diff


def create_revision(*, parcel_code: str, user, client_uuid, data: dict,
                    change_reason: str | None = None, device_info=None,
                    parent_revision_no=None, request=None) -> RevisionOutcome:
    """Create one append-only revision. Idempotent on client_uuid."""
    parcel = Parcel.objects.filter(parcel_code=parcel_code.strip()).first()
    if parcel is None:
        raise ParcelNotFound(details={"parcel_code": parcel_code})

    # ---- idempotency (outside the lock; unique index is the backstop) ----
    try:
        client_uuid = uuid.UUID(str(client_uuid))
    except (ValueError, AttributeError, TypeError):
        raise ApiError("client_uuid must be a valid UUID.",
                       code="VALIDATION_ERROR")
    existing = SurveyChange.objects.filter(client_uuid=client_uuid).first()
    if existing is not None:
        return RevisionOutcome(created=False, revision=existing, diff={})

    if change_reason and len(change_reason) > MAX_CHANGE_REASON:
        raise ApiError(
            f"change_reason exceeds {MAX_CHANGE_REASON} characters.",
            code="VALIDATION_ERROR")
    _validate_payload(data)

    latest = parcel.revisions.order_by("-revision_no").first()
    base_state, base_revision = current_state(parcel)

    if parent_revision_no is not None and (
            latest is None or latest.revision_no != parent_revision_no):
        raise ApiError(
            "Stale parent revision; refresh current state and resubmit.",
            code="REVISION_CONFLICT", status=409,
            details={"expected_parent": latest.revision_no if latest else None},
        )

    merged = {**base_state, **data}
    _validate_payload(merged)
    diff = compute_diff(base_state, merged)
    if not diff:
        raise ApiError("Submitted data produced no changes.",
                       code="VALIDATION_ERROR", details={"diff_empty": True})

    master_ids = list(parcel.master_lines.values_list("id", flat=True))

    # ---- serialised numbering: row-lock the parcel ----------------------
    for attempt in range(3):
        try:
            with transaction.atomic():
                locked = Parcel.objects.select_for_update().get(pk=parcel.pk)
                last_no = (locked.revisions.order_by("-revision_no")
                           .values_list("revision_no", flat=True).first()) or 0
                revision = SurveyChange.objects.create(
                    client_uuid=client_uuid,
                    parcel=locked,
                    revision_no=last_no + 1,
                    parent_revision=base_revision,
                    base_master_row_ids=master_ids,
                    changes=diff,
                    full_payload=merged,
                    change_reason=change_reason or None,
                    changed_by=user,
                    changed_at=timezone.now(),
                    accepted_at=timezone.now(),
                    status=SurveyChange.Status.SUBMITTED,
                    device_info=device_info or None,
                )
            break
        except Exception as exc:  # unique-violation race -> retry with new number
            replayed = SurveyChange.objects.filter(client_uuid=client_uuid).first()
            if replayed is not None:
                return RevisionOutcome(created=False, revision=replayed, diff={})
            if attempt == 2:
                raise ApiError("Revision numbering conflict; please retry.",
                               code="REVISION_CONFLICT", status=409) from exc
            latest = parcel.revisions.order_by("-revision_no").first()
            base_state = latest.full_payload if latest else base_state
            merged = {**base_state, **data}
            diff = compute_diff(base_state, merged)
            if not diff:
                raise ApiError("Concurrent identical submission detected.",
                               code="REVISION_CONFLICT", status=409)

    from surveys.services import audit as audit_service

    audit_service.record(
        action="REVISION_CREATED",
        user=user,
        parcel=parcel,
        revision=revision,
        entity_type="revision",
        entity_id=revision.pk,
        details={"revision_no": revision.revision_no,
                 "changed_fields": sorted(diff.keys())},
        device_info=device_info,
        request=request,
    )

    # Denormalize khasra_number and mauza_number to parcel for search indexing.
    _denormalize_search_keys(parcel, merged)

    return RevisionOutcome(created=True, revision=revision, diff=diff)


def _denormalize_search_keys(parcel: Parcel, payload: dict) -> None:
    """Update parcel's denormalized search fields from latest revision payload."""
    updates = {}
    khasra = payload.get("khasra_number")
    mauza = payload.get("mauza_number")
    if khasra is not None and khasra != parcel.khasra_number:
        updates["khasra_number"] = khasra
    if mauza is not None and mauza != parcel.mauza_number:
        updates["mauza_number"] = mauza
    if updates:
        Parcel.objects.filter(pk=parcel.pk).update(**updates)
