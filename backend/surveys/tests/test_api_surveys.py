"""PHASE 4 — Groups C (parcel lookup), D (original data), E (current data)."""
from django.test import TestCase

from surveys.tests import api_base, base
from surveys.tests.import_fixtures import DEFAULT_DATA_ROW


class ParcelApiSeedCase(TestCase):
    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-C00001", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("sv", )
        self.client = api_base.client_for(self.surveyor)


class ParcelLookupTests(ParcelApiSeedCase):
    """TEST GROUP C."""

    def test_c1_valid_parcel_lookup_structure(self):
        response = self.client.get(
            f"/api/v1/surveys/parcel/{self.parcel.parcel_code}/")
        self.assertEqual(response.status_code, 200)
        body = response.json()
        for key in ("parcel", "original", "current_revision", "revision_no",
                    "source"):
            self.assertIn(key, body)
        self.assertEqual(body["parcel"]["parcel_code"], "RUDA-P14-C00001")
        self.assertEqual(body["source"], "master")

    def test_c2_nonexistent_parcel_404_clean_error(self):
        response = self.client.get("/api/v1/surveys/parcel/RUDA-P14-NOPE/")
        self.assertEqual(response.status_code, 404)
        error = response.json()["error"]
        self.assertEqual(error["code"], "PARCEL_NOT_FOUND")
        self.assertIn("details", error)

    def test_c3_exact_lookup_no_fuzzy_match(self):
        fuzzy = f"{self.parcel.parcel_code}X"
        response = self.client.get(f"/api/v1/surveys/parcel/{fuzzy}/")
        self.assertEqual(response.status_code, 404)

    def test_c4_authentication_required(self):
        anon = api_base.anon_client()
        response = anon.get(
            f"/api/v1/surveys/parcel/{self.parcel.parcel_code}/")
        self.assertEqual(response.status_code, 401)

    def test_c5_source_nid_preserved_in_response(self):
        response = self.client.get(
            f"/api/v1/surveys/parcel/{self.parcel.parcel_code}/")
        self.assertEqual(response.json()["parcel"]["source_nid"], 1819)
        self.assertEqual(response.json()["original"]["owner_name"], "Rana Bashir")


class OriginalDataTests(ParcelApiSeedCase):
    """TEST GROUP D."""

    def test_d1_original_returns_lines_with_provenance(self):
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/original/")
        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(len(body["lines"]), 1)
        line = body["lines"][0]
        self.assertEqual(line["source_row_number"], self.line.source_row_number)
        self.assertEqual(line["sr_no"], self.line.sr_no)
        self.assertTrue(line["is_formula_area"])
        self.assertIsNotNone(body["import_batch"]["file_checksum"])

    def test_d2_raw_data_not_exposed_via_api(self):
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/original/")
        self.assertNotIn("raw_data", str(response.content))

    def test_d3_master_rejects_put_patch_delete(self):
        url = f"/api/v1/surveys/{self.parcel.parcel_code}/original/"
        for verb in ("put", "patch", "delete"):
            with self.subTest(verb=verb):
                call = getattr(self.client, verb)
                response = (call(url, {}, format="json")
                            if verb != "delete" else call(url))
                self.assertEqual(response.status_code, 405)

    def test_d4_master_values_unchanged_after_attempts(self):
        from surveys.models import SurveyMaster

        self.client.put(f"/api/v1/surveys/{self.parcel.parcel_code}/original/",
                        {"owner_name": "HACKED"}, format="json")
        self.line.refresh_from_db()
        self.assertEqual(self.line.owner_name, "Rana Bashir")
        self.assertEqual(SurveyMaster.objects.count(), 1)


class CurrentDataTests(ParcelApiSeedCase):
    """TEST GROUP E."""

    def _create(self, area):
        return api_base.create_revision_via_api(self.client,
                                                self.parcel.parcel_code,
                                                {"area_sqft": area})

    def test_e1_without_revisions_master_is_effective_state(self):
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/current/")
        body = response.json()
        self.assertEqual(body["source"], "master")
        self.assertEqual(body["revision_no"], 0)
        # baseline mapped from the immutable first master line
        self.assertEqual(body["data"]["area_sqft"], 100.0)
        self.assertEqual(body["data"]["owner_name"], "Rana Bashir")

    def test_e2_after_revision_one_it_is_effective(self):
        self._create(120)
        body = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/current/").json()
        self.assertEqual(body["source"], "revision")
        self.assertEqual(body["revision_no"], 1)
        self.assertEqual(body["data"]["area_sqft"], 120)
        self.assertEqual(body["data"]["owner_name"], "Rana Bashir")  # preserved

    def test_e3_after_revision_two_it_is_effective_and_history_intact(self):
        self._create(120)
        self._create(150)
        rev1 = SurveyRevisionProbe.get_payload(self.parcel, 1)
        rev2 = SurveyRevisionProbe.get_payload(self.parcel, 2)
        self.assertEqual(rev1["area_sqft"], 120)
        self.assertEqual(rev2["area_sqft"], 150)


class SurveyRevisionProbe:
    @staticmethod
    def get_payload(parcel, revision_no):
        from surveys.models import SurveyChange

        return SurveyChange.objects.get(
            parcel=parcel, revision_no=revision_no).full_payload
