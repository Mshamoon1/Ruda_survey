"""Uniform API error envelope + domain exceptions.

Every error response has the shape:
    {"error": {"code": <STABLE_CODE>, "message": <human text>, "details": {...}}}

No stack traces are ever exposed; unexpected exceptions become SERVER_ERROR.
"""
import logging

from django.core.exceptions import PermissionDenied as DjangoPermissionDenied
from django.http import Http404
from rest_framework import exceptions, status
from rest_framework.response import Response
from rest_framework.views import exception_handler as drf_exception_handler

logger = logging.getLogger("surveys.api")


class ApiError(Exception):
    """Domain-level API error carrying a stable code."""

    status = status.HTTP_400_BAD_REQUEST
    default_message = "Request failed."
    code = "VALIDATION_ERROR"

    def __init__(self, message=None, *, code=None, details=None, status=None):
        self.message = message or self.default_message
        self.code = code or self.code
        self.details = details or {}
        if status is not None:
            self.status = status
        super().__init__(self.message)


class ParcelNotFound(ApiError):
    status = status.HTTP_404_NOT_FOUND
    code = "PARCEL_NOT_FOUND"
    default_message = "Parcel was not found."


class RevisionNotFound(ApiError):
    status = status.HTTP_404_NOT_FOUND
    code = "INVALID_REVISION"
    default_message = "Revision was not found for this parcel."


class RevisionConflict(ApiError):
    status = status.HTTP_409_CONFLICT
    code = "REVISION_CONFLICT"
    default_message = "Revision numbering conflict; please retry."


class ImageAlreadyExists(ApiError):
    status = status.HTTP_409_CONFLICT
    code = "IMAGE_ALREADY_EXISTS"
    default_message = "An image of this type already exists for the revision."


class InvalidImage(ApiError):
    status = status.HTTP_400_BAD_REQUEST
    code = "INVALID_IMAGE"
    default_message = "Uploaded file is not an acceptable survey image."


class PdfNotReady(ApiError):
    status = status.HTTP_503_SERVICE_UNAVAILABLE
    code = "PDF_GENERATOR_UNAVAILABLE"
    default_message = (
        "PDF generation is not available yet (scheduled for Phase 9); "
        "use /sheet/ for the presentation payload."
    )


_STATUS_TO_CODE = {
    status.HTTP_400_BAD_REQUEST: "VALIDATION_ERROR",
    status.HTTP_401_UNAUTHORIZED: "AUTHENTICATION_REQUIRED",
    status.HTTP_403_FORBIDDEN: "PERMISSION_DENIED",
    status.HTTP_404_NOT_FOUND: "NOT_FOUND",
    status.HTTP_405_METHOD_NOT_ALLOWED: "METHOD_NOT_ALLOWED",
    status.HTTP_409_CONFLICT: "REVISION_CONFLICT",
    status.HTTP_415_UNSUPPORTED_MEDIA_TYPE: "UNSUPPORTED_MEDIA_TYPE",
    status.HTTP_429_TOO_MANY_REQUESTS: "THROTTLED",
}


def _code_for_drf_exception(exc: exceptions.APIException) -> str:
    detail = getattr(exc, "detail", None)
    extra = getattr(detail, "code", None)
    if isinstance(exc, exceptions.AuthenticationFailed):
        return "INVALID_CREDENTIALS"
    if exc.status_code == status.HTTP_401_UNAUTHORIZED:
        return "TOKEN_EXPIRED" if extra == "token_not_valid" else "AUTHENTICATION_REQUIRED"
    if isinstance(exc, exceptions.PermissionDenied) or isinstance(
            exc, DjangoPermissionDenied):
        return "PERMISSION_DENIED"
    if isinstance(exc, exceptions.NotFound) or isinstance(exc, Http404):
        return "NOT_FOUND"
    return _STATUS_TO_CODE.get(exc.status_code, "SERVER_ERROR")


def api_exception_handler(exc, context):
    if isinstance(exc, ApiError):
        return Response(
            {"error": {
                "code": exc.code,
                "message": exc.message,
                "details": exc.details,
            }},
            status=exc.status,
        )

    drf_response = drf_exception_handler(exc, context)
    if drf_response is not None:
        if isinstance(drf_response.data, dict) and "error" in drf_response.data:
            return drf_response
        detail = getattr(exc, "detail", drf_response.data)
        code = _code_for_drf_exception(exc) if hasattr(exc, "status_code") else "SERVER_ERROR"
        # ValidationError carries a field map -> expose under details
        details = detail if isinstance(detail, dict) and not hasattr(detail, "code") else {}
        message = _stringify_detail(detail)
        return Response(
            {"error": {"code": code, "message": message, "details": details}},
            status=drf_response.status_code,
        )

    logger.exception("Unhandled API exception", exc_info=exc)
    return Response(
        {"error": {
            "code": "SERVER_ERROR",
            "message": "Internal server error.",
            "details": {},
        }},
        status=status.HTTP_500_INTERNAL_SERVER_ERROR,
    )


def _stringify_detail(detail):
    try:
        from rest_framework.exceptions import ErrorDetail

        if isinstance(detail, (list, tuple)):
            return "; ".join(str(x) for x in detail)
        if isinstance(detail, dict):
            parts = []
            for key, value in detail.items():
                if isinstance(value, (list, tuple)):
                    value = "; ".join(str(x) for x in value)
                parts.append(f"{key}: {value}" if key != "detail" else str(value))
            return " ".join(parts) or "Validation failed."
        return str(detail)
    except Exception:  # pragma: no cover
        return "Validation failed."
