"""TEST GROUP J — relationship integrity and parent-deletion protection.

Deleting a parent must never cascade into immutable historical records.
"""
from django.db import connection, transaction
from django.test import TestCase

from surveys.models import (
    AuditLog,
    ImportBatch,
    Parcel,
    SurveyChange,
    SurveyImage,
    SurveyMaster,
)
from surveys.tests import base


class RelationshipNavigationTests(TestCase):
    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel(nid=1819)
        self.batch = base.make_batch(total_rows=2, successful_rows=2)
        self.line1 = base.make_master_line(parcel=self.parcel, batch=self.batch,
                                           sr_no=1, source_row_number=5)
        self.line2 = base.make_master_line(parcel=self.parcel, batch=self.batch,
                                           sr_no=2, source_row_number=6)
        self.rev1 = base.make_revision(self.parcel, self.user, revision_no=1)
        self.rev2 = base.make_revision(self.parcel, self.user, revision_no=2,
                                       parent=self.rev1)
        self.img_front = base.make_image(self.rev2, SurveyImage.ImageType.FRONT)
        self.img_second = base.make_image(self.rev2, SurveyImage.ImageType.SECOND)

    def test_j1_parcel_to_master_lines(self):
        self.assertEqual(self.parcel.master_lines.count(), 2)
        self.assertCountEqual(
            list(self.parcel.master_lines.values_list("sr_no", flat=True)), [1, 2]
        )

    def test_j2_parcel_to_revisions(self):
        self.assertEqual(
            [r.revision_no for r in self.parcel.revisions.order_by("revision_no")],
            [1, 2],
        )

    def test_j3_revision_to_images(self):
        self.assertEqual(self.rev2.images.count(), 2)
        self.assertEqual(
            {i.image_type for i in self.rev2.images.all()}, {"FRONT", "SECOND"}
        )

    def test_j4_parcel_to_audit_logs(self):
        AuditLog.objects.create(user=self.user, parcel=self.parcel, action="PARCEL_SEARCH")
        self.assertEqual(self.parcel.audit_logs.count(), 1)

    def test_j5_import_batch_to_master_rows(self):
        self.assertEqual(self.batch.master_rows.count(), 2)


class ParentDeletionProtectionTests(TestCase):
    """Every path that could destroy history must fail."""

    def _attempt_delete(self, obj):
        pid = obj.pk
        deleted = False
        try:
            obj.delete()
            deleted = True
        except Exception:
            pass
        self.assertFalse(deleted, f"{type(obj).__name__} delete must not succeed")
        self.assertTrue(type(obj).objects.filter(pk=pid).exists())

    def test_j6_delete_parcel_with_master_lines_fails(self):
        parcel = base.make_parcel()
        line = base.make_master_line(parcel=parcel)
        with self.assertRaises(Exception), transaction.atomic():
            parcel.delete()  # PROTECT (or trigger if childless-path reached)
        self.assertTrue(Parcel.objects.filter(pk=parcel.pk).exists())

    def test_j7_delete_revision_referenced_by_image_fails(self):
        user = base.make_user()
        rev = base.make_revision(base.make_parcel(), user)
        base.make_image(rev)
        self._attempt_delete(rev)

    def test_j8_delete_import_batch_with_master_rows_fails(self):
        batch = base.make_batch()
        base.make_master_line(batch=batch)
        bid = batch.pk
        try:
            batch.delete()
        except Exception:
            pass
        self.assertTrue(ImportBatch.objects.filter(pk=bid).exists())
        self.assertTrue(SurveyMaster.objects.exists())

    def test_j9_delete_user_who_authored_revisions_fails(self):
        user = base.make_user(username="historical")
        rev = base.make_revision(base.make_parcel(code="RUDA-P14-90001"), user)
        uid = user.pk
        try:
            user.delete()
        except Exception:
            pass
        self.assertTrue(type(user).objects.filter(pk=uid).exists())
        rev.refresh_from_db()
        self.assertEqual(rev.changed_by_id, uid)

    @base.requires_postgresql
    def test_j10_parcels_table_trigger_blocks_raw_delete(self):
        parcel = base.make_parcel()
        with self.assertRaisesRegex(Exception, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM parcels WHERE id=%s", [parcel.pk])

    def test_j11_all_core_tables_present_after_migrations(self):
        tables = set(connection.introspection.table_names())
        for expected in (
            "parcels",
            "survey_master",
            "survey_changes",
            "survey_images",
            "audit_logs",
            "import_batches",
        ):
            self.assertIn(expected, tables)
