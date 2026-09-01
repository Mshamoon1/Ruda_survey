"""PHASE 4 — TEST GROUP A (authentication) + GROUP B (authorization)."""
import uuid
from datetime import timedelta

from django.contrib.auth import get_user_model

User = get_user_model()

from django.test import TestCase
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken

from surveys.models import AuditLog
from surveys.models.user_profile import UserRole
from surveys.tests import api_base


class AuthTests(TestCase):
    """TEST GROUP A."""

    def setUp(self):
        self.user = api_base.make_api_user("surveyor_a", UserRole.SURVEYOR)
        self.client = api_base.anon_client()

    def _login(self, username=None, password="test-pass-123"):
        return self.client.post("/api/v1/auth/login/",
                                {"username": username or self.user.username,
                                 "password": password}, format="json")

    def test_a1_valid_login_returns_tokens_and_role(self):
        response = self._login()
        self.assertEqual(response.status_code, 200)
        body = response.json()
        for key in ("access", "refresh", "user"):
            self.assertIn(key, body)
        self.assertEqual(body["user"]["role"], UserRole.SURVEYOR)
        self.assertNotIn("password", body["user"])
        self.assertNotIn("password", str(body))

    def test_a2_invalid_password_rejected_with_audit(self):
        response = self._login(password="wrong-password")
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["error"]["code"], "INVALID_CREDENTIALS")
        event = AuditLog.objects.filter(action="LOGIN_FAILURE").latest("id")
        self.assertNotIn("wrong-password", str(event.details))

    def test_a3_inactive_account_rejected(self):
        self.user.is_active = False
        self.user.save()
        response = self._login()
        self.assertEqual(response.status_code, 401)

    def test_a4_missing_credentials_rejected(self):
        response = self.client.post("/api/v1/auth/login/", {}, format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "VALIDATION_ERROR")

    def test_a5_token_refresh_round_trip(self):
        login = self._login()
        refresh = login.json()["refresh"]
        response = self.client.post("/api/v1/auth/refresh/",
                                    {"refresh": refresh}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertIn("access", response.json())

    def test_a6_invalid_refresh_token_rejected(self):
        response = self.client.post("/api/v1/auth/refresh/",
                                    {"refresh": "not-a-token"}, format="json")
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["error"]["code"], "INVALID_CREDENTIALS")

    def test_a7_expired_access_token_rejected(self):
        token = RefreshToken.for_user(self.user)
        access = token.access_token
        try:
            access.set_exp(lifetime=timedelta(seconds=-30))
        except TypeError:  # pragma: no cover - simplejwt signature drift
            self.skipTest("set_exp lifetime unsupported")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
        response = self.client.get("/api/v1/surveys/parcel/RUDA-P14-R00005/")
        self.assertEqual(response.status_code, 401)

    def test_a8_unauthenticated_api_access_rejected(self):
        response = self.client.get("/api/v1/surveys/parcel/whatever/")
        self.assertEqual(response.status_code, 401)
        self.assertIn(response.json()["error"]["code"],
                      ("AUTHENTICATION_REQUIRED", "TOKEN_EXPIRED"))

    def test_logout_blacklists_refresh(self):
        login = self._login()
        refresh = login.json()["refresh"]
        authed = api_base.client_for(self.user)
        response = authed.post("/api/v1/auth/logout/",
                               {"refresh": refresh}, format="json")
        self.assertEqual(response.status_code, 200)
        with self.assertRaises(TokenError):
            RefreshToken(refresh)


class AuthorizationTests(TestCase):
    """TEST GROUP B."""

    def setUp(self):
        self.parcel_seed()
        self.surveyor = api_base.make_api_user("sv", UserRole.SURVEYOR)
        self.supervisor = api_base.make_api_user("sup", UserRole.SUPERVISOR)
        self.admin = api_base.make_api_user("adm", UserRole.ADMIN)
        self.outsider_password = None

    def parcel_seed(self):
        from surveys.tests import base as tb

        self.parcel = tb.make_parcel(code="RUDA-P14-B00001", nid=7)
        tb.make_master_line(parcel=self.parcel)

    def test_b1_surveyor_can_read_and_create_but_not_moderate(self):
        client = api_base.client_for(self.surveyor)
        ok = client.get(f"/api/v1/surveys/{self.parcel.parcel_code}/original/")
        self.assertEqual(ok.status_code, 200)
        created = api_base.create_revision_via_api(client, self.parcel.parcel_code,
                                                   {"area_sqft": 150})
        self.assertEqual(created.status_code, 201)
        denied = client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/1/status/",
            {"target": "synced"}, format="json")
        self.assertEqual(denied.status_code, 403)
        self.assertEqual(denied.json()["error"]["code"], "PERMISSION_DENIED")

    def test_b2_supervisor_can_change_status(self):
        surveyor_client = api_base.client_for(self.surveyor)
        api_base.create_revision_via_api(surveyor_client, self.parcel.parcel_code,
                                         {"area_sqft": 160})
        supervisor_client = api_base.client_for(self.supervisor)
        response = supervisor_client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/1/status/",
            {"target": "synced"}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "synced")

    def test_b3_admin_can_access_audit_and_stats(self):
        client = api_base.client_for(self.admin)
        logs = client.get("/api/v1/admin/audit-logs/")
        stats = client.get("/api/v1/admin/stats/")
        self.assertEqual(logs.status_code, 200)
        self.assertEqual(stats.status_code, 200)

    def test_b4_surveyor_denied_audit_access(self):
        client = api_base.client_for(self.surveyor)
        response = client.get("/api/v1/admin/audit-logs/")
        self.assertEqual(response.status_code, 403)

    def test_b5_object_level_wrong_revision_parcel_is_404(self):
        # supervisor passes the role gate; a bogus parcel must then 404
        client = api_base.client_for(self.supervisor)
        api_base.create_revision_via_api(api_base.client_for(self.surveyor),
                                         self.parcel.parcel_code,
                                         {"area_sqft": 170})
        other_parcel = f"{self.parcel.parcel_code}-X"
        response = client.post(
            f"/api/v1/surveys/{other_parcel}/revisions/1/status/",
            {"target": "synced"}, format="json")
        self.assertEqual(response.status_code, 404)
        self.assertEqual(response.json()["error"]["code"], "PARCEL_NOT_FOUND")
