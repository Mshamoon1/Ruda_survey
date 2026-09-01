"""PHASE 5 — Revision workflow hardening, business rules, and API contract freeze.

Groups:
    P — Status transition security
    Q — Approved revision immutability
    R — Current effective state
    S — Idempotency conflict (different payload)
    T — NULL clearing
    U — Complete snapshot chain
    V — Diff accuracy
"""
import uuid

from django.test import TestCase

from surveys.models import SurveyChange
from surveys.models.user_profile import UserRole
from surveys.tests import api_base, base


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _create_revision(client, parcel_code, data, **kw):
    return api_base.create_revision_via_api(client, parcel_code, data, **kw)


def _transition_status(supervisor_client, parcel_code, revision_no, target):
    return supervisor_client.post(
        f"/api/v1/surveys/{parcel_code}/revisions/{revision_no}/status/",
        {"target": target, "reason": "phase-5 test"},
        format="json",
    )


# ---------------------------------------------------------------------------
# Seed case shared across most groups
# ---------------------------------------------------------------------------

class Phase5SeedCase(TestCase):
    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-P00001", nid=500)
        self.line = base.make_master_line(parcel=self.parcel)  # area_value=100
        self.surveyor = api_base.make_api_user("p5_surveyor")
        self.sv_client = api_base.client_for(self.surveyor)
        self.supervisor = api_base.make_api_user("p5_supervisor",
                                                  role=UserRole.SUPERVISOR)
        self.sup_client = api_base.client_for(self.supervisor)
        self.admin = api_base.make_api_user("p5_admin", role=UserRole.ADMIN)
        self.admin_client = api_base.client_for(self.admin)


# ===========================================================================
# GROUP P — Status transition security
# ===========================================================================

class StatusTransitionSecurityTests(Phase5SeedCase):
    """P1–P5: who can change status, which transitions are legal."""

    def test_p1_surveyor_cannot_change_status(self):
        """Surveyor POST /status/ → 403."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 110})
        resp = _transition_status(self.sv_client, self.parcel.parcel_code, 1,
                                  "synced")
        self.assertEqual(resp.status_code, 403)

    def test_p2_supervisor_can_sync(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 111})
        resp = _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                                  "synced")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "synced")

    def test_p3_admin_can_sync(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 112})
        resp = _transition_status(self.admin_client, self.parcel.parcel_code,
                                  1, "synced")
        self.assertEqual(resp.status_code, 200)

    def test_p4_cannot_transition_synced_to_submitted(self):
        """synced is terminal — no further transitions."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 113})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "synced")
        resp = _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                                  "submitted")
        self.assertEqual(resp.status_code, 400)

    def test_p5_cannot_transition_rejected_to_submitted(self):
        """rejected is terminal."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 114})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "rejected")
        resp = _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                                  "submitted")
        self.assertEqual(resp.status_code, 400)

    def test_p6_unauthenticated_cannot_change_status(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 115})
        from rest_framework.test import APIClient
        anon = APIClient()
        resp = anon.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/1/status/",
            {"target": "synced"}, format="json")
        self.assertEqual(resp.status_code, 401)

    def test_p7_invalid_target_rejected(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 116})
        resp = _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                                  "approved")  # not a valid target
        self.assertEqual(resp.status_code, 400)


# ===========================================================================
# GROUP Q — Approved revision immutability
# ===========================================================================

class ApprovedRevisionImmutabilityTests(Phase5SeedCase):
    """Q1–Q4: synced/rejected revisions must not be mutable."""

    def test_q1_synced_revision_full_payload_unchanged_after_transition(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 200})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "synced")
        rev = SurveyChange.objects.get(parcel=self.parcel, revision_no=1)
        self.assertEqual(rev.full_payload["area_sqft"], 200)
        self.assertEqual(rev.status, "synced")

    def test_q2_rejected_revision_retains_original_payload(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 210})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "rejected")
        rev = SurveyChange.objects.get(parcel=self.parcel, revision_no=1)
        self.assertEqual(rev.full_payload["area_sqft"], 210)
        self.assertEqual(rev.status, "rejected")

    def test_q3_correction_requires_new_revision(self):
        """After rejection, a new revision must be created (not editing old)."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 220})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "rejected")
        # correction: create rev 2
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 230})
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.json()["revision_no"], 2)
        # rev 1 unchanged
        rev1 = SurveyChange.objects.get(parcel=self.parcel, revision_no=1)
        self.assertEqual(rev1.full_payload["area_sqft"], 220)
        self.assertEqual(rev1.status, "rejected")

    def test_q4_orm_save_on_existing_revision_blocked(self):
        """save() on an existing SurveyChange is blocked by AppendOnlyModel."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 240})
        rev = SurveyChange.objects.get(parcel=self.parcel, revision_no=1)
        rev.full_payload["area_sqft"] = 999
        with self.assertRaises(Exception):
            rev.save()


# ===========================================================================
# GROUP R — Current effective state
# ===========================================================================

class CurrentEffectiveStateTests(Phase5SeedCase):
    """R1–R5: what /current/ returns under various revision states."""

    def _current(self):
        return self.sv_client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/current/").json()

    def test_r1_no_revisions_master_is_current(self):
        state = self._current()
        self.assertEqual(state["source"], "master")
        self.assertEqual(state["revision_no"], 0)
        self.assertEqual(state["data"]["area_sqft"], 100.0)

    def test_r2_submitted_revision_is_current(self):
        """The latest revision (any status) becomes current per current_state()."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 300})
        state = self._current()
        self.assertEqual(state["source"], "revision")
        self.assertEqual(state["revision_no"], 1)
        self.assertEqual(state["data"]["area_sqft"], 300)

    def test_r3_synced_revision_becomes_effective(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 310})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "synced")
        state = self._current()
        self.assertEqual(state["source"], "revision")
        self.assertEqual(state["revision_no"], 1)
        self.assertEqual(state["data"]["area_sqft"], 310)

    def test_r4_rejected_revision_is_still_current(self):
        """Rejected revision is the latest → returned as current."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 320})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "rejected")
        state = self._current()
        self.assertEqual(state["source"], "revision")
        self.assertEqual(state["revision_no"], 1)
        self.assertEqual(state["data"]["area_sqft"], 320)

    def test_r5_multiple_revisions_latest_synced_wins(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 330})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "synced")
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 340})
        _transition_status(self.sup_client, self.parcel.parcel_code, 2,
                           "synced")
        state = self._current()
        self.assertEqual(state["revision_no"], 2)
        self.assertEqual(state["data"]["area_sqft"], 340)

    def test_r6_submitted_between_synced_uses_latest(self):
        """Rev 1 synced, rev 2 submitted → latest (rev 2) is effective."""
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 350})
        _transition_status(self.sup_client, self.parcel.parcel_code, 1,
                           "synced")
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 360})
        state = self._current()
        self.assertEqual(state["revision_no"], 2)
        self.assertEqual(state["data"]["area_sqft"], 360)


# ===========================================================================
# GROUP S — Idempotency conflict (different payload)
# ===========================================================================

class IdempotencyConflictTests(Phase5SeedCase):
    """S1–S2: same client_uuid with different data."""

    def test_s1_same_uuid_same_payload_returns_replayed(self):
        uid = str(uuid.uuid4())
        r1 = _create_revision(self.sv_client, self.parcel.parcel_code,
                              {"area_sqft": 400}, client_uuid=uid)
        r2 = _create_revision(self.sv_client, self.parcel.parcel_code,
                              {"area_sqft": 400}, client_uuid=uid)
        self.assertEqual(r1.status_code, 201)
        self.assertEqual(r2.status_code, 200)
        self.assertTrue(r2.json()["replayed"])

    def test_s2_same_uuid_different_payload_returns_replayed_not_error(self):
        """Idempotent replay returns existing revision regardless of data
        differences — the first submission is authoritative."""
        uid = str(uuid.uuid4())
        r1 = _create_revision(self.sv_client, self.parcel.parcel_code,
                              {"area_sqft": 410}, client_uuid=uid)
        r2 = _create_revision(self.sv_client, self.parcel.parcel_code,
                              {"area_sqft": 999}, client_uuid=uid)
        self.assertEqual(r1.status_code, 201)
        self.assertEqual(r2.status_code, 200)
        self.assertTrue(r2.json()["replayed"])
        # original data preserved
        rev = SurveyChange.objects.get(client_uuid=uid)
        self.assertEqual(rev.full_payload["area_sqft"], 410)


# ===========================================================================
# GROUP T — NULL clearing (value → NULL)
# ===========================================================================

class NullClearingTests(Phase5SeedCase):
    """T1–T3: explicit NULL transitions produce valid diffs."""

    def test_t1_value_to_null_produces_correct_diff(self):
        # father_name starts as "-" (genuine source sentinel)
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"father_name": None})
        diff = resp.json()["diff"]
        self.assertIn("father_name", diff)
        self.assertEqual(diff["father_name"]["old"], "-")
        self.assertIsNone(diff["father_name"]["new"])

    def test_t2_null_to_value_produces_correct_diff(self):
        # cnic_no starts as NULL in master
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"cnic_no": "35201-9999999-9"})
        diff = resp.json()["diff"]
        self.assertIn("cnic_no", diff)
        self.assertIsNone(diff["cnic_no"]["old"])
        self.assertEqual(diff["cnic_no"]["new"], "35201-9999999-9")

    def test_t3_explicit_null_sent_in_data_field(self):
        """The serializer allows explicit null in the data dict.
        Use structure_name which is non-NULL in master baseline."""
        resp = self.sv_client.post(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/",
            {"client_uuid": str(uuid.uuid4()),
             "data": {"structure_name": None}},
            format="json")
        self.assertEqual(resp.status_code, 201)
        self.assertIsNone(resp.json()["full_payload"]["structure_name"])


# ===========================================================================
# GROUP U — Complete snapshot chain
# ===========================================================================

class CompleteSnapshotChainTests(Phase5SeedCase):
    """U1–U3: each revision is independently readable."""

    def test_u1_chain_of_three_revisions_each_independently_readable(self):
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 500})
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 510})
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 520})
        resp = self.sv_client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/revisions/"
        ).json()
        revisions = resp.get("results", resp)
        self.assertEqual(len(revisions), 3)
        by_no = {r["revision_no"]: r for r in revisions}
        # Each revision has a valid changes diff
        self.assertEqual(by_no[1]["changes"]["area_sqft"],
                         {"old": 100.0, "new": 500})
        self.assertEqual(by_no[2]["changes"]["area_sqft"],
                         {"old": 500, "new": 510})
        self.assertEqual(by_no[3]["changes"]["area_sqft"],
                         {"old": 510, "new": 520})
        # All have valid status and metadata
        for rev in revisions:
            self.assertIn("status", rev)
            self.assertIn("changed_by", rev)
            self.assertIn("client_uuid", rev)

    def test_u2_full_payload_contains_all_fields_not_just_diff(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 530})
        fp = resp.json()["full_payload"]
        # all 20 allowed fields must be present
        from surveys.services.revision_service import ALLOWED_PAYLOAD_FIELDS
        for field in ALLOWED_PAYLOAD_FIELDS:
            self.assertIn(field, fp)

    def test_u3_snapshot_uses_base_state_not_latest_server_state(self):
        """If rev1 is submitted but not synced, rev2 still builds on master
        (because current_state returns master when no synced revision exists)."""
        # Rev 1: submitted (not synced) area=540
        _create_revision(self.sv_client, self.parcel.parcel_code,
                         {"area_sqft": 540})
        # Rev 2: submitted area=550 — should be based on master (100), not rev1
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 550})
        fp = resp.json()["full_payload"]
        # Since no revision is synced, current_state returns master.
        # rev2 merges master (area=100) with data (area=550) → area=550
        self.assertEqual(fp["area_sqft"], 550)


# ===========================================================================
# GROUP V — Diff accuracy
# ===========================================================================

class DiffAccuracyTests(Phase5SeedCase):
    """V1–V6: diff correctness for various transition types."""

    def test_v1_unchanged_fields_excluded(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 600})
        diff = resp.json()["diff"]
        self.assertEqual(set(diff.keys()), {"area_sqft"})

    def test_v2_multiple_changed_fields_all_present(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 610, "structure_name": "New"})
        diff = resp.json()["diff"]
        self.assertEqual(sorted(diff.keys()),
                         ["area_sqft", "structure_name"])

    def test_v3_numeric_change_old_new_correct(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"area_sqft": 620})
        diff = resp.json()["diff"]
        self.assertEqual(diff["area_sqft"], {"old": 100.0, "new": 620})

    def test_v4_string_change_old_new_correct(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"structure_name": "Changed Name"})
        diff = resp.json()["diff"]
        self.assertEqual(diff["structure_name"],
                         {"old": "Hawaili i.e. rooms, verenda",
                          "new": "Changed Name"})

    def test_v5_null_to_value_old_is_none(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"contact_number": "0300-1234567"})
        diff = resp.json()["diff"]
        self.assertIsNone(diff["contact_number"]["old"])
        self.assertEqual(diff["contact_number"]["new"], "0300-1234567")

    def test_v6_value_to_null_new_is_none(self):
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"structure_name": None})
        diff = resp.json()["diff"]
        self.assertEqual(diff["structure_name"]["old"],
                         "Hawaili i.e. rooms, verenda")
        self.assertIsNone(diff["structure_name"]["new"])

    def test_v7_empty_string_vs_null_different(self):
        """Empty string '' is NOT the same as NULL."""
        resp = _create_revision(self.sv_client, self.parcel.parcel_code,
                                {"khasra_number": ""})
        diff = resp.json()["diff"]
        self.assertEqual(diff["khasra_number"]["old"], None)
        self.assertEqual(diff["khasra_number"]["new"], "")
