"""Audit trail writer for API mutations (insert-only audit_logs).

Never logs passwords, JWTs, or other credentials.
"""
from django.utils import timezone

from surveys.models import AuditLog


def client_ip(request) -> str | None:
    forwarded = request.META.get("HTTP_X_FORWARDED_FOR")
    if forwarded:
        return forwarded.split(",")[0].strip() or None
    return request.META.get("REMOTE_ADDR") or None


def correlation_id(request):
    raw = (
        request.headers.get("X-Request-ID")
        or request.headers.get("X-Correlation-ID")
    )
    if not raw:
        return None
    import uuid as _uuid

    try:
        return _uuid.UUID(str(raw))
    except ValueError:
        return None


def record(*, action: str, user=None, parcel=None, revision=None,
           entity_type=None, entity_id=None, details=None, device_info=None,
           request=None):
    """Insert one immutable audit entry. Best-effort: never breaks the caller."""
    try:
        return AuditLog.objects.create(
            user=getattr(user, "pk", None) and user or None,
            parcel=parcel,
            revision=revision,
            action=action,
            entity_type=entity_type,
            entity_id=entity_id,
            details=details,
            device_info=device_info,
            ip_address=client_ip(request) if request is not None else None,
            correlation_id=correlation_id(request) if request is not None else None,
        )
    except Exception:  # pragma: no cover - auditing must not break business flow
        import logging

        logging.getLogger("surveys.api").exception("audit write failed for %s", action)
        return None


def now():
    return timezone.now()
