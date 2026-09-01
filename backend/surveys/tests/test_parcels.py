"""TEST GROUP A — parcels: creation, uniqueness, source_nid behaviour, indexes."""
from django.db import DatabaseError, IntegrityError, connection, transaction
from django.db.models import UniqueConstraint
from django.test import TestCase

from surveys.models import Parcel, SurveyMaster
from surveys.tests import base
from surveys.tests.base import requires_postgresql


class ParcelCreationTests(TestCase):
    def test_1_parcel_can_be_created(self):
        p = base.make_parcel(code="RUDA-P14-00001", nid=606)
        self.assertTrue(p.pk)
        self.assertEqual(p.parcel_code, "RUDA-P14-00001")
        self.assertEqual(p.source_nid, 606)

    def test_2_parcel_uniqueness_constraint_declared(self):
        names = {c.name for c in Parcel._meta.constraints}
        # parcel_code unique is field-level; the non-empty check is declared here.
        self.assertIn("ck_parcels_code_nonempty", names)
        field = Parcel._meta.get_field("parcel_code")
        self.assertTrue(field.unique)

    def test_3_duplicate_parcel_code_rejected_by_db(self):
        base.make_parcel(code="RUDA-P14-00042")
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                Parcel.objects.create(parcel_code="RUDA-P14-00042")

    def test_4_source_nid_may_repeat_across_parcels(self):
        """NID 606 pollution (doc 02 §C2) may legitimately be split into parcels."""
        for code in ("RUDA-P14-10001", "RUDA-P14-10002", "RUDA-P14-10003"):
            base.make_parcel(code=code, nid=606)
        self.assertEqual(Parcel.objects.filter(source_nid=606).count(), 3)

    def test_5_source_nid_nullable_and_filterable(self):
        p = base.make_parcel(nid=None)
        self.assertIsNone(p.source_nid)
        self.assertEqual(Parcel.objects.filter(source_nid__isnull=True).count(), 1)

    def test_6_lookup_indexes_exist_on_parcel_code_and_source_nid(self):
        with connection.cursor() as cursor:
            cons = connection.introspection.get_constraints(cursor, Parcel._meta.db_table)
        indexed_cols = set()
        for meta in cons.values():
            if meta["index"] or meta["unique"]:
                indexed_cols.update(meta["columns"])
        self.assertIn("parcel_code", indexed_cols)  # unique index
        self.assertIn("source_nid", indexed_cols)   # explicit db_index

    def test_7_parcel_delete_blocked_when_master_rows_reference_it(self):
        parcel = base.make_parcel()
        line = base.make_master_line(parcel=parcel)
        pid = parcel.pk
        with self.assertRaises(Exception), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM parcels WHERE id = %s", [pid])
        self.assertTrue(SurveyMaster.objects.filter(pk=line.pk).exists())
        self.assertTrue(Parcel.objects.filter(pk=pid).exists())

    @requires_postgresql
    def test_8_parcel_delete_blocked_even_when_childless(self):
        """Trigger trg_parcels_no_delete blocks DELETE regardless of children."""
        parcel = base.make_parcel()
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM parcels WHERE id = %s", [parcel.pk])
