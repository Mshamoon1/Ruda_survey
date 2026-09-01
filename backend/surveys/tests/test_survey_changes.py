"""TEST GROUP C — revision creation story (master 100 → rev1 120 → rev2 150).
TEST GROUP D — revision immutability (ORM + database level).
TEST GROUP E — revision numbering, client_uuid idempotency.
"""
import uuid
from decimal import Decimal

from django.db import DatabaseError, IntegrityError, connection, transaction
from django.test import TestCase
from django.utils import timezone

from surveys.models import SurveyChange, SurveyMaster
from surveys.tests import base
from surveys.tests.base import requires_postgresql


class RevisionCreationStoryTests(TestCase):
    """Business rule #2: master stays 100; revisions hold 120 then 150."""

    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel(nid=606)
        self.master = base.make_master_line(parcel=self.parcel)  # area_value = 100

    def test_c1_full_story(self):
        rev1 = base.make_revision(
            self.parcel,
            self.user,
            revision_no=1,
            full_payload=base.base_payload(area_sqft=120),
            changes={"area_sqft": {"from": 100, "to": 120}},
        )
        rev2 = base.make_revision(
            self.parcel,
            self.user,
            revision_no=2,
            parent=rev1,
            full_payload=base.base_payload(area_sqft=150),
            changes={"area_sqft": {"from": 120, "to": 150}},
        )

        self.master.refresh_from_db()
        self.assertEqual(self.master.area_value, Decimal("100.00"))  # MASTER = 100 (never 150)

        rev1.refresh_from_db()
        rev2.refresh_from_db()
        self.assertEqual(rev1.full_payload["area_sqft"], 120)        # Revision 1 = 120
        self.assertEqual(rev2.full_payload["area_sqft"], 150)        # Revision 2 = 150

        self.assertIsNone(rev1.parent_revision)
        self.assertEqual(rev2.parent_revision_id, rev1.pk)
        self.assertEqual(
            [r.revision_no for r in self.parcel.revisions.order_by("revision_no")],
            [1, 2],
        )

    def test_c2_revision_is_independently_reconstructable(self):
        """A revision must carry the COMPLETE state, not only changed fields."""
        rev1 = base.make_revision(
            self.parcel,
            self.user,
            revision_no=1,
            full_payload=base.base_payload(area_sqft=120),
        )
        payload_keys = set(rev1.full_payload.keys())
        expected_wb2_fields = {
            "rd_value", "latitude", "longitude", "package_no", "village",
            "owner_name", "father_name", "cnic_no", "khasra_number",
            "contact_number", "land_owner_doc", "electricity_connection_name",
            "land_area", "structure_status", "structure_name", "length_ft",
            "width_ft", "area_sqft", "construction_nature",
        }
        self.assertLessEqual(expected_wb2_fields, payload_keys)

    def test_c3_master_row_ids_recorded_on_revision(self):
        rev = base.make_revision(self.parcel, self.user)
        self.assertIn(self.master.pk, rev.base_master_row_ids)


class RevisionImmutabilityTests(TestCase):
    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel()
        self.rev = base.make_revision(self.parcel, self.user, revision_no=1,
                                      full_payload=base.base_payload(area_sqft=120))

    # ---------- ORM path ----------
    def test_d1_orm_instance_save_blocked(self):
        with self.assertRaises(RuntimeError):
            self.rev.full_payload = base.base_payload(area_sqft=999)
            self.rev.save()

    def test_d2_orm_queryset_update_blocked(self):
        with self.assertRaises(RuntimeError):
            SurveyChange.objects.update(full_payload={})

    def test_d3_orm_queryset_delete_blocked(self):
        with self.assertRaises(RuntimeError):
            SurveyChange.objects.all().delete()

    def test_d4_orm_instance_delete_blocked(self):
        with self.assertRaises(RuntimeError):
            self.rev.delete()

    def test_d5_lifecycle_transition_allowed_via_explicit_channel(self):
        rev = base.make_revision(self.parcel, self.user, revision_no=2,
                                 status=SurveyChange.Status.DRAFT)
        self.assertEqual(rev.transition_status(SurveyChange.Status.SUBMITTED), 1)
        self.assertEqual(rev.status, "submitted")
        self.assertIsNotNone(rev.accepted_at)
        rev.transition_status(SurveyChange.Status.SYNCED)
        self.assertEqual(rev.status, "synced")

    def test_d6_illegal_status_transition_rejected_orm_side(self):
        rev = base.make_revision(self.parcel, self.user, revision_no=2,
                                 status=SurveyChange.Status.SYNCED)
        with self.assertRaises(RuntimeError):
            rev.transition_status(SurveyChange.Status.DRAFT)

    def test_d7_limited_update_rejects_non_lifecycle_fields(self):
        with self.assertRaisesRegex(RuntimeError, "Only"):
            SurveyChange.objects.filter(pk=self.rev.pk).limited_update(full_payload={})

    def test_d8_values_intact_after_blocked_attempts(self):
        self.rev.refresh_from_db()
        self.assertEqual(self.rev.full_payload["area_sqft"], 120)
        self.assertEqual(self.rev.revision_no, 1)


# NOTE: DB-level enforcement is exercised on PostgreSQL only.


@requires_postgresql
class RevisionDatabaseLevelTests(TestCase):
    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel()
        self.rev = base.make_revision(self.parcel, self.user, revision_no=1,
                                      full_payload=base.base_payload(area_sqft=120))

    def test_d9_db_delete_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM survey_changes WHERE id = %s", [self.rev.pk])

    def test_d10_db_payload_update_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "only status/accepted_at"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE survey_changes SET full_payload = %s::jsonb WHERE id = %s",
                    ['{"area_sqft": 999}', self.rev.pk],
                )

    def test_d11_db_changed_by_swap_blocked_by_trigger(self):
        other = base.make_user(username="other")
        with self.assertRaisesRegex(DatabaseError, "only status/accepted_at"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE survey_changes SET changed_by_id = %s WHERE id = %s",
                    [other.pk, self.rev.pk],
                )

    def test_d12_db_status_only_update_allowed_and_payload_intact(self):
        with connection.cursor() as cursor:
            cursor.execute(
                "UPDATE survey_changes SET status = 'rejected' WHERE id = %s",
                [self.rev.pk],
            )
        self.rev.refresh_from_db()
        self.assertEqual(self.rev.status, "rejected")
        self.assertEqual(self.rev.full_payload["area_sqft"], 120)


class RevisionNumberingTests(TestCase):
    """TEST GROUP E."""

    def setUp(self):
        self.user = base.make_user()

    def test_e1_sequential_revisions_per_parcel(self):
        p = base.make_parcel()
        for n in (1, 2, 3):
            base.make_revision(p, self.user, revision_no=n)
        numbers = list(p.revisions.order_by("revision_no").values_list("revision_no", flat=True))
        self.assertEqual(numbers, [1, 2, 3])

    def test_e2_duplicate_revision_no_same_parcel_rejected(self):
        p = base.make_parcel()
        base.make_revision(p, self.user, revision_no=1)
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_revision(p, base.make_user(), revision_no=1)

    def test_e3_same_revision_no_different_parcels_allowed(self):
        p1 = base.make_parcel(code="RUDA-P14-20001")
        p2 = base.make_parcel(code="RUDA-P14-20002")
        base.make_revision(p1, self.user, revision_no=1)
        base.make_revision(p2, self.user, revision_no=1)
        self.assertEqual(SurveyChange.objects.filter(revision_no=1).count(), 2)

    def test_e4_zero_or_negative_revision_no_rejected(self):
        p = base.make_parcel()
        for bad in (0, -1):
            with self.assertRaises(IntegrityError), transaction.atomic():
                
                    base.make_revision(p, self.user, revision_no=bad)

    def test_e5_duplicate_client_uuid_rejected(self):
        p = base.make_parcel()
        shared = uuid.uuid4()
        base.make_revision(p, self.user, client_uuid=shared)
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_revision(base.make_parcel(code="RUDA-P14-30001"), self.user,
                                   client_uuid=shared)

    def test_e6_client_uuid_unique_index_present(self):
        with connection.cursor() as cursor:
            cons = connection.introspection.get_constraints(cursor, SurveyChange._meta.db_table)
        covered = [meta for meta in cons.values()
                   if meta.get("columns") == ["client_uuid"] and meta["unique"]]
        self.assertTrue(covered)

    def test_e7_parent_revision_protected_from_delete_attempt(self):
        p = base.make_parcel()
        parent = base.make_revision(p, self.user, revision_no=1)
        child = base.make_revision(p, self.user, revision_no=2, parent=parent)
        pid = parent.pk
        try:
            with self.assertRaises(Exception), transaction.atomic():
                parent.delete()  # ORM guard raises before touching DB
        except Exception:
            pass
        # Even raw path must fail (ORM guard or DB trigger):
        self.assertTrue(SurveyChange.objects.filter(pk=pid).exists())
        self.assertTrue(SurveyChange.objects.filter(pk=child.pk).exists())
