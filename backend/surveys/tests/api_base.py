"""Shared helpers for Phase 4 API tests."""
import io
import uuid

from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

from surveys.models.user_profile import UserRole
from surveys.tests import base


def make_api_user(username=None, role=UserRole.SURVEYOR):
    """Create a user whose profile carries the given role."""
    user = base.make_user(username=username)
    if role != UserRole.SURVEYOR:
        user.survey_profile.role = role
        user.survey_profile.save()
    return user


def client_for(user) -> APIClient:
    client = APIClient()
    refresh = RefreshToken.for_user(user)
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {refresh.access_token}")
    return client


def anon_client() -> APIClient:
    return APIClient()


def tiny_png() -> bytes:
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (4, 4), color=(200, 30, 30)).save(buffer, format="PNG")
    return buffer.getvalue()


def tiny_jpeg() -> bytes:
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (4, 4), color=(30, 200, 30)).save(buffer, format="JPEG")
    return buffer.getvalue()


def upload_multipart(client, parcel_code, revision_no, image_type="FRONT",
                     content=None, filename="evidence.png"):
    content = content if content is not None else tiny_png()
    return client.post(
        f"/api/v1/surveys/{parcel_code}/revisions/{revision_no}/images/",
        {"image_type": image_type,
         "file": (io.BytesIO(content), filename)},
        format="multipart",
    )


def create_revision_via_api(client, parcel_code, data_overrides=None,
                            client_uuid=None, change_reason=None):
    payload = {
        "client_uuid": str(client_uuid or uuid.uuid4()),
        "data": {"area_sqft": 120, **(data_overrides or {})},
    }
    if change_reason is not None:
        payload["change_reason"] = change_reason
    return client.post(f"/api/v1/surveys/{parcel_code}/revisions/",
                       payload, format="json")
