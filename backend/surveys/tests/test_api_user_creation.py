"""PHASE 11 — User Creation API tests."""
from django.contrib.auth import get_user_model
from django.test import TestCase

from surveys.models import AuditLog
from surveys.models.user_profile import UserRole
from surveys.tests import api_base

User = get_user_model()

URL = "/api/v1/auth/users/"
VALID_PAYLOAD = {
    "username": "newuser",
    "password": "StrongPass123!",
    "first_name": "Test",
    "last_name": "User",
    "email": "test@example.com",
    "role": UserRole.SURVEYOR,
}


class UserCreationSuccessTests(TestCase):
    """Admin creates user successfully."""

    def setUp(self):
        self.admin = api_base.make_api_user("admin_u", UserRole.ADMIN)
        self.client = api_base.client_for(self.admin)

    def test_01_admin_creates_user_201(self):
        resp = self.client.post(URL, VALID_PAYLOAD, format="json")
        self.assertEqual(resp.status_code, 201)
        body = resp.json()
        self.assertEqual(body["username"], "newuser")
        self.assertEqual(body["role"], UserRole.SURVEYOR)
        self.assertTrue(body["is_active"])
        self.assertNotIn("password", body)

    def test_02_password_is_hashed(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        user = User.objects.get(username="newuser")
        self.assertTrue(user.check_password("StrongPass123!"))
        self.assertNotEqual(user.password, "StrongPass123!")

    def test_03_raw_password_not_returned(self):
        resp = self.client.post(URL, VALID_PAYLOAD, format="json")
        raw = resp.content.decode()
        self.assertNotIn("StrongPass123!", raw)
        self.assertNotIn("password", raw)

    def test_04_created_user_can_login(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        login_resp = api_base.anon_client().post(
            "/api/v1/auth/login/",
            {"username": "newuser", "password": "StrongPass123!"},
            format="json",
        )
        self.assertEqual(login_resp.status_code, 200)
        self.assertIn("access", login_resp.json())
        self.assertIn("refresh", login_resp.json())

    def test_05_created_user_me_endpoint(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        login = api_base.anon_client().post(
            "/api/v1/auth/login/",
            {"username": "newuser", "password": "StrongPass123!"},
            format="json",
        )
        token = login.json()["access"]
        authed = api_base.APIClient()
        authed.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")
        me = authed.get("/api/v1/auth/me/")
        self.assertEqual(me.status_code, 200)
        self.assertEqual(me.json()["username"], "newuser")
        self.assertEqual(me.json()["role"], UserRole.SURVEYOR)

    def test_06_user_profile_auto_created(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        user = User.objects.get(username="newuser")
        self.assertTrue(hasattr(user, "survey_profile"))
        self.assertEqual(user.survey_profile.role, UserRole.SURVEYOR)

    def test_07_audit_log_recorded(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        log = AuditLog.objects.filter(action="USER_CREATED").latest("id")
        self.assertEqual(log.user, self.admin)
        self.assertEqual(log.entity_type, "User")
        self.assertIsNotNone(log.entity_id)

    def test_08_audit_log_no_credentials(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        log = AuditLog.objects.filter(action="USER_CREATED").latest("id")
        self.assertNotIn("StrongPass123!", str(log.details))
        self.assertNotIn("password", str(log.details))

    def test_09_create_admin_role(self):
        payload = {**VALID_PAYLOAD, "username": "newadmin", "role": UserRole.ADMIN}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.json()["role"], UserRole.ADMIN)

    def test_10_create_supervisor_role(self):
        payload = {**VALID_PAYLOAD, "username": "newsup", "role": UserRole.SUPERVISOR}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.json()["role"], UserRole.SUPERVISOR)

    def test_11_optional_fields_defaults(self):
        minimal = {"username": "minimal", "password": "Pass123!"}
        resp = self.client.post(URL, minimal, format="json")
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.json()["first_name"], "")
        self.assertEqual(resp.json()["last_name"], "")


class UserCreationAuthTests(TestCase):
    """Authentication and authorization checks."""

    def test_12_unauthenticated_returns_401(self):
        resp = api_base.anon_client().post(URL, VALID_PAYLOAD, format="json")
        self.assertEqual(resp.status_code, 401)

    def test_13_surveyor_returns_403(self):
        surveyor = api_base.make_api_user("sv_create", UserRole.SURVEYOR)
        client = api_base.client_for(surveyor)
        resp = client.post(URL, VALID_PAYLOAD, format="json")
        self.assertEqual(resp.status_code, 403)
        self.assertEqual(resp.json()["error"]["code"], "PERMISSION_DENIED")

    def test_14_supervisor_returns_403(self):
        sup = api_base.make_api_user("sup_create", UserRole.SUPERVISOR)
        client = api_base.client_for(sup)
        resp = client.post(URL, VALID_PAYLOAD, format="json")
        self.assertEqual(resp.status_code, 403)


class UserCreationValidationTests(TestCase):
    """Input validation."""

    def setUp(self):
        self.admin = api_base.make_api_user("admin_v", UserRole.ADMIN)
        self.client = api_base.client_for(self.admin)

    def test_15_missing_username(self):
        payload = {**VALID_PAYLOAD}
        del payload["username"]
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 400)

    def test_16_missing_password(self):
        payload = {**VALID_PAYLOAD}
        del payload["password"]
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 400)

    def test_17_invalid_role(self):
        payload = {**VALID_PAYLOAD, "role": "BOGUS"}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 400)

    def test_18_duplicate_username(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        resp = self.client.post(URL, VALID_PAYLOAD, format="json")
        self.assertEqual(resp.status_code, 400)
        self.assertIn("username", str(resp.json()))

    def test_19_duplicate_email(self):
        self.client.post(URL, VALID_PAYLOAD, format="json")
        payload2 = {**VALID_PAYLOAD, "username": "other", "email": "test@example.com"}
        resp = self.client.post(URL, payload2, format="json")
        self.assertEqual(resp.status_code, 400)
        self.assertIn("email", str(resp.json()))

    def test_20_invalid_email(self):
        payload = {**VALID_PAYLOAD, "email": "not-an-email"}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 400)


class UserCreationSecurityTests(TestCase):
    """Mass-assignment and privilege escalation protection."""

    def setUp(self):
        self.admin = api_base.make_api_user("admin_s", UserRole.ADMIN)
        self.client = api_base.client_for(self.admin)

    def test_21_cannot_set_is_superuser(self):
        payload = {**VALID_PAYLOAD, "is_superuser": True}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 201)
        user = User.objects.get(username="newuser")
        self.assertFalse(user.is_superuser)

    def test_22_cannot_set_is_staff(self):
        payload = {**VALID_PAYLOAD, "is_staff": True}
        resp = self.client.post(URL, payload, format="json")
        self.assertEqual(resp.status_code, 201)
        user = User.objects.get(username="newuser")
        self.assertFalse(user.is_staff)

    def test_23_empty_body(self):
        resp = self.client.post(URL, {}, format="json")
        self.assertEqual(resp.status_code, 400)

    def test_24_existing_login_tests_still_green(self):
        """Quick regression: login endpoint still works."""
        user = api_base.make_api_user("regression_l", UserRole.SURVEYOR)
        resp = api_base.anon_client().post(
            "/api/v1/auth/login/",
            {"username": "regression_l", "password": "test-pass-123"},
            format="json",
        )
        self.assertEqual(resp.status_code, 200)
