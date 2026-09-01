"""PHASE 4 — Groups J (images), K (sheet), L (audit), M (immutability
regression), N (API security), O (OpenAPI)."""
import uuid

from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import DatabaseError, connection, transaction
from django.test import TestCase
from drf_spectacular.generators import SchemaGenerator

from surveys.models import AuditLog, SurveyChange, SurveyImage
from surveys.tests import api_base, base


class ImageSeedCase(TestCase):
    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-J00001", nid=9)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("sv")
        self.client = api_base.client_for(self.surveyor)
        self.revision_no = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 120}).json()["revision_no"]


class ImageUploadTests(ImageSeedCase):
    """TEST GROUP J."""

    def test_j1_front_upload_succeeds_with_checksum(self):
        response = api_base.upload_multipart(
            self.client, self.parcel.parcel_code, self.revision_no, "FRONT",
            content=api_base.tiny_jpeg(), filename="photo.jpg")
        self.assertEqual(response.status_code, 201)
        body = response.json()
        self.assertEqual(body["image_type"], "FRONT")
        self.assertEqual(len(body["checksum_sha256"]), 64)
        self.assertTrue(body["storage_key"].startswith(
            f"surveys/{self.parcel.parcel_code}/{self.revision_no:04d}/FRONT_"))
        image = SurveyImage.objects.get(pk=body["id"])
        self.assertIsNotNone(image.file_path)

    def test_j2_second_type_accepted(self):
        first = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                          self.revision_no, "FRONT")
        second = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                           self.revision_no, "SECOND",
                                           content=api_base.tiny_png())
        self.assertEqual(first.status_code, 201)
        self.assertEqual(second.status_code, 201)
        self.assertEqual(SurveyImage.objects.count(), 2)

    def test_j3_invalid_type_rejected(self):
        response = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             self.revision_no, "SIDE",
                                             content=api_base.tiny_png())
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "INVALID_IMAGE")

    def test_j4_invalid_content_rejected_by_sniffing(self):
        response = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             self.revision_no, "FRONT",
                                             content=b"not-an-image-at-all",
                                             filename="fake.jpg")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "INVALID_IMAGE")

    def test_j5_oversized_file_rejected(self):
        blob = b"\xff\xd8\xff" + b"\x00" * (10 * 1024 * 1024 + 1)
        response = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             self.revision_no, "FRONT",
                                             content=blob)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "INVALID_IMAGE")

    def test_j6_duplicate_front_conflicts_without_deleting_old(self):
        first = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                          self.revision_no, "FRONT")
        old_checksum = first.json()["checksum_sha256"]
        conflict = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             self.revision_no, "FRONT",
                                             content=api_base.tiny_png())
        self.assertEqual(conflict.status_code, 409)
        self.assertEqual(conflict.json()["error"]["code"], "IMAGE_ALREADY_EXISTS")
        image = SurveyImage.objects.get(revision__revision_no=self.revision_no,
                                        image_type="FRONT")
        self.assertEqual(image.checksum_sha256, old_checksum)

    def test_j7_nonexistent_revision_404(self):
        response = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             99, "FRONT")
        self.assertEqual(response.status_code, 404)

    def test_j8_wrong_parcel_for_revision_is_404(self):
        other = base.make_parcel(code="RUDA-P14-J00002")
        response = api_base.upload_multipart(self.client, other.parcel_code,
                                             self.revision_no, "FRONT")
        self.assertEqual(response.status_code, 404)

    def test_j9_storage_path_has_no_client_controlled_components(self):
        evil_name = "../../evil.png"
        response = api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                             self.revision_no, "FRONT",
                                             content=api_base.tiny_png(),
                                             filename=evil_name)
        self.assertEqual(response.status_code, 201)
        key = response.json()["storage_key"]
        self.assertNotIn("..", key)
        self.assertNotIn("evil", key)


class SheetTests(TestCase):
    """TEST GROUP K — standalone seed: parcel WITHOUT any revision."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-K00001", nid=10)
        base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("sv")
        self.client = api_base.client_for(self.surveyor)

    def _sheet(self):
        return self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/sheet/").json()

    def test_k1_original_only_parcel_sheet(self):
        sheet = self._sheet()
        self.assertEqual(sheet["source"], "master")
        self.assertIsNone(sheet["current_revision_no"])
        self.assertIn("area_sqft", sheet["fields"])

    def test_k2_revised_parcel_sheet_reflects_revision_and_surveyor(self):
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 140})
        sheet = self._sheet()
        self.assertEqual(sheet["source"], "revision")
        self.assertEqual(sheet["current_revision_no"], 1)
        self.assertEqual(sheet["fields"]["area_sqft"], 140)
        self.assertEqual(sheet["surveyor"], self.surveyor.username)

    def test_k3_null_fields_hidden_from_fields_but_kept_in_original(self):
        sheet = self._sheet()
        self.assertNotIn("cnic_no", sheet["fields"])
        self.assertIn("cnic_no", sheet["original"])

    def test_k4_images_included_when_present(self):
        revision_no = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 145}).json()["revision_no"]
        api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                  revision_no, "FRONT")
        api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                  revision_no, "SECOND",
                                  content=api_base.tiny_png())
        sheet = self._sheet()
        types = {img["image_type"] for img in sheet["images"]}
        self.assertEqual(types, {"FRONT", "SECOND"})


class AuditTrailTests(ImageSeedCase):
    """TEST GROUP L."""

    def test_l1_revision_creation_audited(self):
        AuditLog.objects.get_or_create(action="REVISION_CREATED")  # ensure table live
        before = AuditLog.objects.filter(action="REVISION_CREATED").count()
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 150})
        after = AuditLog.objects.filter(action="REVISION_CREATED").count()
        self.assertEqual(after, before + 1)

    def test_l2_image_upload_audited(self):
        before = AuditLog.objects.filter(action="IMAGE_UPLOADED").count()
        api_base.upload_multipart(self.client, self.parcel.parcel_code,
                                  self.revision_no, "FRONT")
        self.assertEqual(AuditLog.objects.filter(action="IMAGE_UPLOADED").count(),
                         before + 1)

    def test_l3_status_transition_audited(self):
        from surveys.models.user_profile import UserRole

        supervisor = api_base.make_api_user("sup", role=UserRole.SUPERVISOR)
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 160})
        sup_client = api_base.client_for(supervisor)
        sup_client.post(f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/1/status/",
                        {"target": "synced"}, format="json")
        self.assertTrue(AuditLog.objects.filter(
            action="REVISION_STATUS_CHANGED").exists())

    def test_l4_no_tokens_or_passwords_logged(self):
        login_client = api_base.anon_client()
        login_client.post("/api/v1/auth/login/",
                          {"username": self.surveyor.username,
                           "password": "test-pass-123"}, format="json")
        for entry in AuditLog.objects.all():
            blob = str(entry.details) + str(entry.device_info)
            self.assertNotIn("test-pass-123", blob)
            self.assertNotIn("eyJ", blob)   # JWT prefix


class ImmutabilityRegressionTests(ImageSeedCase):
    """TEST GROUP M — Phase-2 DB protection re-verified AFTER API activity."""

    def test_m_master_still_blocked_at_db_level(self):
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 170})
        line_pk = self.line.pk
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE survey_master SET owner_name='X' WHERE id=%s",
                    [line_pk])

    def test_m_revisions_still_append_only_at_db_level(self):
        revision = SurveyChange.objects.get(parcel=self.parcel, revision_no=1)
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM survey_changes WHERE id=%s",
                               [revision.pk])

    def test_m_audit_logs_still_insert_only_at_db_level(self):
        entry = AuditLog.objects.create(action="PROBE")
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM audit_logs WHERE id=%s", [entry.pk])


class ApiSecurityTests(TestCase):
    """TEST GROUP N."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-N00001", nid=11)
        base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("sv")
        self.client = api_base.client_for(self.surveyor)

    def test_n1_invalid_jwt_format_rejected(self):
        client = api_base.anon_client()
        client.credentials(HTTP_AUTHORIZATION="Bearer garbage.token.value")
        response = client.get(f"/api/v1/surveys/{self.parcel.parcel_code}/current/")
        self.assertEqual(response.status_code, 401)

    def test_n2_permission_escalation_denied(self):
        # surveyor attempts admin surface
        response = self.client.get("/api/v1/admin/stats/")
        self.assertEqual(response.status_code, 403)

    def test_n3_protected_field_injection_rejected(self):
        payload = {
            "client_uuid": str(uuid.uuid4()),
            "data": {"revision_no": 99},
        }
        response = self.client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            payload, format="json")
        self.assertEqual(response.status_code, 400)

    def test_n4_invalid_ids_return_clean_errors(self):
        response = self.client.get(
            "/api/v1/surveys/RUDA-P14-N00001/revisions/not-a-number/images/")
        self.assertEqual(response.status_code, 404)

    def test_n5_malformed_json_handled(self):
        response = self.client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            data="{broken json", content_type="application/json")
        self.assertIn(response.status_code, (400,))
        self.assertEqual(response.json()["error"]["code"],
                         ("VALIDATION_ERROR" if response.status_code == 400
                          else response.json()["error"]["code"]))

    def test_n6_invalid_content_type_on_json_endpoint(self):
        response = self.client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            "plain text", content_type="text/plain")
        self.assertEqual(response.status_code, 415)
        self.assertEqual(response.json()["error"]["code"],
                         "UNSUPPORTED_MEDIA_TYPE")


class OpenAPITests(TestCase):
    """TEST GROUP O."""

    def _schema(self):
        client = api_base.anon_client()
        response = client.get("/api/v1/schema/",
                              HTTP_ACCEPT="application/vnd.oai.openapi+json")
        self.assertEqual(response.status_code, 200, response.content[:500])
        return response.json()

    def test_o1_schema_generates_without_critical_gaps(self):
        schema = self._schema()
        paths = schema["paths"]
        required_paths = {
            "/api/v1/auth/login/", "/api/v1/auth/refresh/", "/api/v1/auth/logout/",
            "/api/v1/surveys/parcel/{parcel_code}/",
            "/api/v1/surveys/{parcel_code}/original/",
            "/api/v1/surveys/{parcel_code}/current/",
            "/api/v1/surveys/{parcel_code}/revisions/",
            "/api/v1/surveys/{parcel_code}/revisions/{revision_no}/images/",
            "/api/v1/surveys/{parcel_code}/revisions/{revision_no}/status/",
            "/api/v1/surveys/{parcel_code}/sheet/",
            "/api/v1/surveys/{parcel_code}/pdf/",
            "/api/v1/admin/audit-logs/",
        }
        missing = sorted(required_paths - set(paths))
        self.assertEqual(missing, [], f"undocumented endpoints; have: {sorted(paths)}")

    def test_o2_immutable_endpoints_have_no_write_methods(self):
        schema = self._schema()
        original_ops = set(schema["paths"]
                           ["/api/v1/surveys/{parcel_code}/original/"].keys())
        self.assertFalse(original_ops & {"put", "patch", "delete"})
