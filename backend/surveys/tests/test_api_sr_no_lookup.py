"""Tests for GET /api/v1/surveys/sr-no/<sr_no>/ — Serial Number lookup."""
from decimal import Decimal

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

from surveys.models import ImportBatch, Parcel, SurveyMaster

User = get_user_model()


def _auth_client():
    user = User.objects.create_user(username="srno_user", password="test-pass-123")
    client = APIClient()
    refresh = RefreshToken.for_user(user)
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {refresh.access_token}")
    return client


def _create_parcel(parcel_code="RUDA-P14-TEST001", **kwargs):
    parcel, _ = Parcel.objects.get_or_create(
        parcel_code=parcel_code,
        defaults={
            "source_nid": 99999,
            "village": kwargs.get("village", "TestVillage"),
            "tehsil": kwargs.get("tehsil", "TestTehsil"),
            "district": kwargs.get("district", "Lahore"),
            "owner_name_current": kwargs.get("owner_name", "TestOwner"),
            "khasra_number": kwargs.get("khasra", "1"),
            "mauza_number": kwargs.get("mauza", "M1"),
        },
    )
    return parcel


def _create_master_line(parcel, sr_no=1, **kwargs):
    batch = kwargs.pop("import_batch", None)
    if batch is None:
        batch, _ = ImportBatch.objects.get_or_create(
            file_checksum=f"test_{parcel.parcel_code}_{sr_no}",
            defaults={
                "source_filename": "test.xlsx",
                "file_checksum": f"test_{parcel.parcel_code}_{sr_no}",
                "sheet_name": "Sheet1",
                "total_rows": 1,
                "successful_rows": 1,
                "failed_rows": 0,
                "warning_count": 0,
            },
        )
    return SurveyMaster.objects.create(
        import_batch=batch,
        source_row_number=kwargs.get("source_row_number", sr_no),
        sr_no=sr_no,
        parcel=parcel,
        source_nid=parcel.source_nid,
        chainage_m=Decimal("0"),
        affected_persons_count=0,
        phase_code="",
        latitude=Decimal("0"),
        longitude=Decimal("0"),
        project_component="",
        owner_name=kwargs.get("owner_name", "TestOwner"),
        father_name="",
        caste="",
        village=parcel.village,
        tehsil=parcel.tehsil,
        district=parcel.district,
        ownership_documents="",
        structure_status="N/A",
        structure_name="",
        structure_count=0,
        tenure_status="",
        length_ft=Decimal("0"),
        width_ft=Decimal("0"),
        area_value=Decimal("0"),
        construction_nature="",
        unit_rate_rs=0,
        compensation_million=Decimal("0"),
        impact_extent="N/A",
        river_location="",
        in_row_yn="",
        cl_offset_m=0,
        extra_note="",
        khasra_number=parcel.khasra_number,
        raw_data={"A": str(sr_no), "B": str(parcel.source_nid)},
    )


class TestSrNoLookupEndpoint(TestCase):
    """Tests for GET /api/v1/surveys/sr-no/<sr_no>/"""

    def setUp(self):
        self.client = _auth_client()

    def test_unauthenticated_returns_401(self):
        client = APIClient()
        resp = client.get("/api/v1/surveys/sr-no/1/")
        self.assertEqual(resp.status_code, 401)

    def test_valid_sr_no_returns_results(self):
        parcel = _create_parcel("RUDA-P14-SR001")
        _create_master_line(parcel, sr_no=42)
        resp = self.client.get("/api/v1/surveys/sr-no/42/")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["sr_no"], 42)
        self.assertEqual(len(data["parcels"]), 1)
        p = data["parcels"][0]
        self.assertEqual(p["parcel_code"], "RUDA-P14-SR001")
        self.assertEqual(p["master_line_count"], 1)
        self.assertEqual(p["village"], "TestVillage")
        self.assertEqual(p["tehsil"], "TestTehsil")
        self.assertEqual(p["district"], "Lahore")
        self.assertEqual(p["owner_name"], "TestOwner")

    def test_nonexistent_sr_no_returns_empty(self):
        resp = self.client.get("/api/v1/surveys/sr-no/999999/")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["sr_no"], 999999)
        self.assertEqual(data["parcels"], [])

    def test_multiple_master_lines_same_parcel(self):
        parcel = _create_parcel("RUDA-P14-SR002")
        _create_master_line(parcel, sr_no=50, source_row_number=10)
        _create_master_line(parcel, sr_no=50, source_row_number=11)
        resp = self.client.get("/api/v1/surveys/sr-no/50/")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data["parcels"]), 1)
        self.assertEqual(data["parcels"][0]["master_line_count"], 2)

    def test_multiple_parcels_same_sr_no(self):
        p1 = _create_parcel("RUDA-P14-SR003", village="Village1")
        p2 = _create_parcel("RUDA-P14-SR004", village="Village2")
        _create_master_line(p1, sr_no=100)
        _create_master_line(p2, sr_no=100)
        resp = self.client.get("/api/v1/surveys/sr-no/100/")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data["parcels"]), 2)
        codes = {p["parcel_code"] for p in data["parcels"]}
        self.assertIn("RUDA-P14-SR003", codes)
        self.assertIn("RUDA-P14-SR004", codes)

    def test_negative_sr_no_returns_404(self):
        resp = self.client.get("/api/v1/surveys/sr-no/-1/")
        self.assertEqual(resp.status_code, 404)

    def test_zero_sr_no_returns_validation_error(self):
        resp = self.client.get("/api/v1/surveys/sr-no/0/")
        self.assertEqual(resp.status_code, 400)

    def test_response_format(self):
        parcel = _create_parcel("RUDA-P14-SR005")
        _create_master_line(parcel, sr_no=77)
        resp = self.client.get("/api/v1/surveys/sr-no/77/")
        data = resp.json()
        self.assertIn("sr_no", data)
        self.assertIn("parcels", data)
        self.assertIsInstance(data["parcels"], list)
        if data["parcels"]:
            p = data["parcels"][0]
            self.assertIn("parcel_code", p)
            self.assertIn("village", p)
            self.assertIn("tehsil", p)
            self.assertIn("district", p)
            self.assertIn("owner_name", p)
            self.assertIn("master_line_count", p)
