"""Phase 10F — Owner autocomplete endpoint tests."""
from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient
from rest_framework_simplejwt.tokens import RefreshToken

from surveys.tests.base import make_parcel

User = get_user_model()


def _auth_client():
    user = User.objects.create_user(username="surveyor10f", password="test-pass-123")
    client = APIClient()
    refresh = RefreshToken.for_user(user)
    client.credentials(HTTP_AUTHORIZATION=f"Bearer {refresh.access_token}")
    return client


def _anon_client():
    return APIClient()


class OwnerSearchEndpointTest(TestCase):
    """Tests for GET /api/v1/surveys/owners/"""

    def setUp(self):
        self.url = reverse("api:survey-owner-search")
        self.client = _auth_client()

    # -- Authentication tests --

    def test_unauthenticated_returns_401(self):
        client = _anon_client()
        resp = client.get(self.url, {"q": "Ali"})
        self.assertEqual(resp.status_code, 401)

    def test_authenticated_returns_200(self):
        resp = self.client.get(self.url, {"q": "Ali"})
        self.assertEqual(resp.status_code, 200)

    # -- Query validation --

    def test_empty_query_returns_empty(self):
        resp = self.client.get(self.url, {"q": ""})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_single_char_returns_empty(self):
        resp = self.client.get(self.url, {"q": "A"})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_whitespace_only_returns_empty(self):
        resp = self.client.get(self.url, {"q": "   "})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_no_q_param_returns_empty(self):
        resp = self.client.get(self.url)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    # -- Matching tests --

    def test_partial_matching(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        make_parcel(code="R002", owner_name_current="Muhammad Aslam")
        make_parcel(code="R003", owner_name_current="Rana Bashir")

        resp = self.client.get(self.url, {"q": "Muh"})
        data = resp.json()
        names = [r["owner_name"] for r in data["results"]]
        self.assertIn("Muhammad Ali", names)
        self.assertIn("Muhammad Aslam", names)
        self.assertNotIn("Rana Bashir", names)

    def test_case_insensitive_matching(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")

        for query in ["muhammad", "MUHAMMAD", "Muhammad", "Muh", "MUH"]:
            resp = self.client.get(self.url, {"q": query})
            data = resp.json()
            names = [r["owner_name"] for r in data["results"]]
            self.assertIn("Muhammad Ali", names,
                          f"Failed for query: {query}")

    def test_exact_matching(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        resp = self.client.get(self.url, {"q": "Muhammad Ali"})
        data = resp.json()
        names = [r["owner_name"] for r in data["results"]]
        self.assertIn("Muhammad Ali", names)

    def test_middle_name_matching(self):
        make_parcel(code="R001", owner_name_current="Ali Muhammad Khan")
        resp = self.client.get(self.url, {"q": "Muhammad"})
        data = resp.json()
        names = [r["owner_name"] for r in data["results"]]
        self.assertIn("Ali Muhammad Khan", names)

    # -- Duplicate deduplication --

    def test_duplicate_names_deduplicated(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        make_parcel(code="R002", owner_name_current="Muhammad Ali")
        make_parcel(code="R003", owner_name_current="Muhammad Ali")

        resp = self.client.get(self.url, {"q": "Muhammad Ali"})
        data = resp.json()
        self.assertEqual(len(data["results"]), 1)
        self.assertEqual(data["results"][0]["owner_name"], "Muhammad Ali")
        self.assertEqual(data["results"][0]["record_count"], 3)

    def test_record_count_correct(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        make_parcel(code="R002", owner_name_current="Muhammad Ali")
        make_parcel(code="R003", owner_name_current="Ali Khan")

        resp = self.client.get(self.url, {"q": "Ali"})
        data = resp.json()
        for r in data["results"]:
            if r["owner_name"] == "Muhammad Ali":
                self.assertEqual(r["record_count"], 2)
            elif r["owner_name"] == "Ali Khan":
                self.assertEqual(r["record_count"], 1)

    # -- NULL and blank owner exclusion --

    def test_null_owner_excluded(self):
        make_parcel(code="R001", owner_name_current=None)
        resp = self.client.get(self.url, {"q": "null"})
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_blank_owner_excluded(self):
        make_parcel(code="R001", owner_name_current="")
        resp = self.client.get(self.url, {"q": "  "})
        data = resp.json()
        self.assertEqual(data["results"], [])

    # -- Result limit --

    def test_result_limit(self):
        for i in range(20):
            make_parcel(code=f"R{i:03d}", owner_name_current=f"Owner Test{i:03d}")

        resp = self.client.get(self.url, {"q": "Owner Test"})
        data = resp.json()
        self.assertLessEqual(len(data["results"]), 15)

    # -- Response format --

    def test_response_format(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        resp = self.client.get(self.url, {"q": "Muh"})
        data = resp.json()
        self.assertIn("results", data)
        self.assertIsInstance(data["results"], list)
        if data["results"]:
            item = data["results"][0]
            self.assertIn("owner_name", item)
            self.assertIn("record_count", item)
            self.assertIsInstance(item["record_count"], int)

    # -- Special characters and security --

    def test_sql_injection_attempt(self):
        resp = self.client.get(self.url, {"q": "'; DROP TABLE parcels; --"})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_unicode_owner_matching(self):
        make_parcel(code="R001", owner_name_current="\u0639\u0644\u064a \u0645\u062d\u0645\u062f")
        resp = self.client.get(self.url, {"q": "\u0639\u0644\u064a"})
        data = resp.json()
        names = [r["owner_name"] for r in data["results"]]
        self.assertIn("\u0639\u0644\u064a \u0645\u062d\u0645\u062f", names)

    def test_special_characters_in_query(self):
        resp = self.client.get(self.url, {"q": "@#$%^&*()"})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["results"], [])

    def test_very_long_query(self):
        long_q = "A" * 300
        resp = self.client.get(self.url, {"q": long_q})
        self.assertEqual(resp.status_code, 200)

    # -- No unauthorized data exposure --

    def test_no_cnic_exposed(self):
        make_parcel(code="R001", owner_name_current="Muhammad Ali")
        resp = self.client.get(self.url, {"q": "Muh"})
        data = resp.json()
        for r in data["results"]:
            self.assertNotIn("cnic", r)
            self.assertNotIn("phone", r)
            self.assertNotIn("address", r)
