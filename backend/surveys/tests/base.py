"""Shared fixtures and guards for RUDA Survey Phase 2 tests."""
import copy
import hashlib
import itertools
import unittest
import uuid
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.db import connection

from surveys.models import ImportBatch, Parcel, SurveyChange, SurveyImage, SurveyMaster

User = get_user_model()

# PostgreSQL-specific trigger enforcement (migration 0002).
requires_postgresql = unittest.skipUnless(
    connection.vendor == "postgresql",
    "Database-level trigger protection exists only on PostgreSQL",
)

_user_counter = itertools.count(1)
_parcel_counter = itertools.count(1)


def make_user(username=None):
    return User.objects.create_user(
        username=username or f"surveyor{next(_user_counter)}",
        password="test-pass-123",
        first_name="Field",
        last_name="Surveyor",
    )


def make_parcel(code=None, nid=None, **extra):
    params = {
        "parcel_code": code or f"RUDA-P14-{next(_parcel_counter):05d}",
        "source_nid": nid,
        "village": "Arya Nagar",
        "tehsil": "Ferozwala",
        "district": "Sheikhupura",
        "owner_name_current": "Rana Bashir",
    }
    params.update(extra)
    return Parcel.objects.create(**params)


def make_batch(status=ImportBatch.Status.COMPLETED, **extra):
    params = {
        "source_filename": "02_Annex 4.1_Affected Residential & Commercial Structures.xlsx",
        "file_checksum": hashlib.sha256(str(next(_parcel_counter)).encode()).hexdigest(),
        "sheet_name": "Anex 4.1 Str(Rpr)Dec25Hsn (F)",
        "total_rows": 0,
        "successful_rows": 0,
        "failed_rows": 0,
        "warning_count": 0,
        "status": status,
    }
    params.update(extra)
    return ImportBatch.objects.create(**params)


def make_master_line(parcel=None, batch=None, **overrides):
    """Create an immutable survey_master line with realistic NOT NULL values."""
    if parcel is None:
        parcel = make_parcel(nid=606)
    if batch is None:
        batch = make_batch()
    existing = batch.master_rows.count()
    params = {
        "import_batch": batch,
        "source_row_number": 5 + existing,
        "sr_no": 1 + existing,
        "parcel": parcel,
        "source_nid": parcel.source_nid,
        "chainage_m": Decimal("15250"),
        "affected_persons_count": None,          # continuation-row NULL by design
        "phase_code": "Ph#1 ",
        "latitude": Decimal("31.70384951"),
        "longitude": Decimal("74.4159358"),
        "project_component": "Dam",
        "owner_name": "Rana Bashir",
        "father_name": "-",                       # genuine source sentinel
        "caste": "Rajput",
        "village": "Arya Nagar",
        "tehsil": "Ferozwala",
        "district": "Sheikhupura",
        "ownership_documents": "Intiqal",
        "structure_status": "Cattle Farm",
        "structure_name": "Hawaili i.e. rooms, verenda",
        "structure_count": 4,
        "tenure_status": "Owner",
        "length_ft": Decimal("27.10"),
        "width_ft": Decimal("31.70"),
        "area_value": Decimal("100.00"),          # Business-rule story: master area = 100
        "construction_nature": "Semi-Pacca",
        "unit_rate_rs": 2200,
        "compensation_million": Decimal("0.220000"),
        "impact_extent": "Major Impact",
        "river_location": "Right ",
        "in_row_yn": "Yes",
        "cl_offset_m": 439,
        "extra_note": None,
        "is_formula_area": True,
        "is_formula_compensation": True,
        "raw_data": {"A": str(1 + existing), "B": "606", "AD": None},
    }
    params.update(overrides)
    return SurveyMaster.objects.create(**params)


def base_payload(**overrides):
    """Complete WB2-schema survey state (doc 02 Part E)."""
    payload = {
        "rd_value": None,
        "latitude": 31.70384951,
        "longitude": 74.4159358,
        "package_no": "PKG-14",
        "village": "Arya Nagar",
        "owner_name": "Rana Bashir",
        "father_name": "-",
        "cnic_no": None,
        "khasra_number": None,
        "contact_number": None,
        "land_owner_doc": "Intiqal",
        "electricity_connection_name": None,
        "land_area": None,
        "structure_status": "Residential",
        "structure_name": "House",
        "length_ft": 45,
        "width_ft": 25,
        "area_sqft": 100,
        "construction_nature": "Pacca",
    }
    payload.update(overrides)
    return payload


def make_revision(parcel, changed_by, revision_no=None, *, parent=None, **overrides):
    """Insert one revision row; revision_no defaults to max+1 for the parcel."""
    if revision_no is None:
        last = parcel.revisions.order_by("-revision_no").first()
        revision_no = (last.revision_no + 1) if last else 1
    params = {
        "parcel": parcel,
        "revision_no": revision_no,
        "parent_revision": parent,
        "base_master_row_ids": list(parcel.master_lines.values_list("id", flat=True)),
        "changes": {},
        "full_payload": base_payload(),
        "changed_by": changed_by,
        "status": SurveyChange.Status.SYNCED,
    }
    params.update(overrides)
    return SurveyChange.objects.create(**params)


def make_image(revision, image_type=SurveyImage.ImageType.FRONT, **overrides):
    digest = hashlib.sha256(uuid.uuid4().bytes).hexdigest()
    params = {
        "revision": revision,
        "parcel": revision.parcel,
        "image_type": image_type,
        "file_path": (
            f"surveys/{revision.parcel.parcel_code}/"
            f"{revision.revision_no:04d}/{image_type}_{digest[:10]}.jpg"
        ),
        "checksum_sha256": digest,
        "content_type": "image/jpeg",
        "file_size": 102_400,
        "uploaded_by": revision.changed_by,
    }
    params.update(overrides)
    return SurveyImage.objects.create(**params)
