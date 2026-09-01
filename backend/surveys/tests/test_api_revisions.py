"""PHASE 4 — Groups F (create revision), G (idempotency), H (concurrency),
I (revision history)."""
import uuid
from concurrent.futures import ThreadPoolExecutor

from django.db import connection, close_old_connections
from django.test import TestCase, TransactionTestCase

from surveys.models import SurveyChange
from surveys.tests import api_base, base


class RevisionSeedCase(TestCase):
    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-F00001", nid=606)
        self.line = base.make_master_line(parcel=self.parcel)  # area_value = 100
        self.surveyor = api_base.make_api_user("sv")
        self.client = api_base.client_for(self.surveyor)


class CreateRevisionTests(RevisionSeedCase):
    """TEST GROUP F."""

    def test_f1_single_field_change_complete_snapshot_and_diff(self):
        response = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 120}, change_reason="field re-measure")
        self.assertEqual(response.status_code, 201)
        body = response.json()
        # complete snapshot rule: unchanged fields preserved
        self.assertEqual(body["full_payload"]["owner_name"], "Rana Bashir")
        self.assertEqual(body["full_payload"]["village"], "Arya Nagar")
        self.assertEqual(body["full_payload"]["area_sqft"], 120)
        # diff contains ONLY the changed field with old/new
        self.assertEqual(body["diff"], {"area_sqft": {"old": 100.0, "new": 120}})
        self.assertEqual(body["revision_no"], 1)
        self.assertFalse(body["replayed"])

    def test_f2_multiple_fields_changed(self):
        response = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 130, "structure_name": "2nd Floor"})
        body = response.json()
        self.assertEqual(sorted(body["diff"].keys()),
                         ["area_sqft", "structure_name"])
        self.assertEqual(body["diff"]["structure_name"],
                         {"old": "Hawaili i.e. rooms, verenda", "new": "2nd Floor"})

    def test_f3_null_to_value_transition(self):
        response = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code, {"cnic_no": "35201-1234567-1"})
        diff = response.json()["diff"]
        self.assertEqual(diff["cnic_no"], {"old": None, "new": "35201-1234567-1"})

    def test_f4_value_to_null_transition(self):
        response = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code, {"father_name": None})
        diff = response.json()["diff"]
        self.assertEqual(diff["father_name"], {"old": "-", "new": None})

    def test_f5_master_unchanged_after_revision(self):
        self.line.refresh_from_db()
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 999})
        self.line.refresh_from_db()
        self.assertEqual(str(self.line.area_value), "100.00")
        self.assertEqual(self.line.owner_name, "Rana Bashir")

    def test_f6_changed_by_and_reason_recorded(self):
        response = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code, {"area_sqft": 111},
            change_reason="typo fix")
        revision = SurveyChange.objects.get(pk=response.json()["id"])
        self.assertEqual(revision.changed_by_id, self.surveyor.pk)
        self.assertEqual(revision.change_reason, "typo fix")
        self.assertIsNotNone(revision.changed_at)
        self.assertIsNotNone(revision.accepted_at)
        self.assertEqual(revision.status, "submitted")

    def test_f7_unknown_protected_fields_rejected(self):
        for payload in ({"changed_by": 5}, {"revision_no": 42},
                        {"status": "synced"}, {"bogus_field": 1}):
            with self.subTest(payload=payload):
                response = api_base.create_revision_via_api(
                    self.client, self.parcel.parcel_code, payload)
                self.assertEqual(response.status_code, 400)
                error = response.json()["error"]
                self.assertEqual(error["code"], "VALIDATION_ERROR")

    def test_f8_no_change_submission_rejected(self):
        baseline_owner = "Rana Bashir"
        response = self.client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            {"client_uuid": str(uuid.uuid4()),
             "data": {"owner_name": baseline_owner}},
            format="json")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"]["code"], "VALIDATION_ERROR")

    def test_f9_stale_parent_conflict_detected(self):
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 120})
        stale_uuid = str(uuid.uuid4())
        response = self.client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            {"client_uuid": stale_uuid, "parent_revision_no": 9,
             "data": {"area_sqft": 130}}, format="json")
        self.assertEqual(response.status_code, 409)
        self.assertEqual(response.json()["error"]["code"], "REVISION_CONFLICT")


class IdempotencyTests(RevisionSeedCase):
    """TEST GROUP G."""

    def test_g1_same_client_uuid_creates_exactly_one_revision(self):
        shared_uuid = str(uuid.uuid4())
        first = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 140}, client_uuid=shared_uuid)
        second = api_base.create_revision_via_api(
            self.client, self.parcel.parcel_code,
            {"area_sqft": 140}, client_uuid=shared_uuid)
        self.assertEqual(first.status_code, 201)
        self.assertEqual(second.status_code, 200)
        self.assertTrue(second.json()["replayed"])
        self.assertEqual(SurveyChange.objects.count(), 1)
        self.assertEqual(second.json()["revision_no"],
                         first.json()["revision_no"])


class ConcurrencyTests(TransactionTestCase):
    """TEST GROUP H — parallel submissions must serialize numbering."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-H00001", nid=8)
        base.make_master_line(parcel=self.parcel)
        self.users = [api_base.make_api_user(f"h{i}") for i in range(4)]

    def _worker(self, index):
        results = []
        try:
            close_old_connections()
            user = self.users[index]
            outcome_user = user.__class__.objects.get(pk=user.pk)
            svc = __import__("surveys.services.revision_service",
                             fromlist=["create_revision"])
            outcome = svc.create_revision(
                parcel_code=self.parcel.parcel_code,
                user=outcome_user,
                client_uuid=uuid.uuid4(),
                data={"area_sqft": 100 + index + 1},
                change_reason=f"parallel {index}",
            )
            results.append(outcome.revision.revision_no)
        except Exception as exc:  # surfaced via assertion below
            results.append(exc)
        finally:
            # this thread owns its own connection; release it so the test
            # runner can safely flush/reuse the database afterwards
            try:
                connection.close()
            except Exception:
                pass
        return results

    def test_h_parallel_submissions_get_distinct_sequential_numbers(self):
        with ThreadPoolExecutor(max_workers=4) as pool:
            outcomes = list(pool.map(self._worker, range(4)))
        flat = [item for sublist in outcomes for item in sublist]
        numbers = sorted(n for n in flat if not isinstance(n, Exception))
        self.assertEqual(len(numbers), 4, f"failures: {flat}")
        self.assertEqual(numbers, [1, 2, 3, 4])
        db_numbers = list(
            SurveyChange.objects.order_by("revision_no")
            .values_list("revision_no", flat=True))
        self.assertEqual(db_numbers, [1, 2, 3, 4])


class RevisionHistoryTests(RevisionSeedCase):
    """TEST GROUP I."""

    def test_i1_order_newest_first_with_pagination(self):
        for area in (110, 120, 130):
            api_base.create_revision_via_api(self.client,
                                             self.parcel.parcel_code,
                                             {"area_sqft": area})
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/")
        body = response.json()
        self.assertIn("results", body)
        numbers = [r["revision_no"] for r in body["results"]]
        self.assertEqual(numbers, [3, 2, 1])

    def test_i2_pagination_page_size_respected(self):
        for area in (111, 121, 131):
            api_base.create_revision_via_api(self.client,
                                             self.parcel.parcel_code,
                                             {"area_sqft": area})
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/?page_size=2")
        body = response.json()
        self.assertEqual(len(body["results"]), 2)
        self.assertIn("count", body)

    def test_i3_historical_values_immutable_in_responses(self):
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 115})
        api_base.create_revision_via_api(self.client, self.parcel.parcel_code,
                                         {"area_sqft": 125})
        history = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/").json()["results"]
        rev1 = next(r for r in history if r["revision_no"] == 1)
        rev2 = next(r for r in history if r["revision_no"] == 2)
        self.assertEqual(rev1["changes"]["area_sqft"], {"old": 100.0, "new": 115})
        self.assertEqual(rev2["changes"]["area_sqft"], {"old": 115, "new": 125})

    def test_i4_no_modification_endpoint_for_history(self):
        from django.urls import resolve, Resolver404

        url = f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/"
        try:
            match = resolve(url)
        except Resolver404:
            self.fail("list endpoint missing")
        # PUT/PATCH/DELETE on the collection are not routed
        put_response = self.client.put(url, {}, format="json")
        delete_response = self.client.delete(url)
        self.assertEqual(put_response.status_code, 405)
        self.assertEqual(delete_response.status_code, 405)
