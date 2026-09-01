"""TEST GROUP B — survey_master immutability (ORM + database level).
TEST GROUP F — NULL handling and source-sentinel preservation.
"""
from decimal import Decimal

from django.db import DatabaseError, IntegrityError, connection, transaction
from django.test import TestCase

from surveys.models import SurveyMaster
from surveys.tests import base
from surveys.tests.base import requires_postgresql


class MasterImmutabilityTests(TestCase):
    def setUp(self):
        self.line = base.make_master_line()
        self.line.refresh_from_db()

    # ---------- ORM path ----------
    def test_b1_orm_instance_save_blocked(self):
        with self.assertRaises(RuntimeError):
            self.line.owner_name = "Changed Name"
            self.line.save()

    def test_b2_orm_queryset_update_blocked(self):
        with self.assertRaises(RuntimeError):
            SurveyMaster.objects.update(owner_name="Changed Name")

    def test_b3_orm_queryset_delete_blocked(self):
        with self.assertRaises(RuntimeError):
            SurveyMaster.objects.all().delete()

    def test_b4_orm_instance_delete_blocked(self):
        with self.assertRaises(RuntimeError):
            self.line.delete()

    def test_b5_orm_bulk_update_blocked(self):
        with self.assertRaises(RuntimeError):
            SurveyMaster.objects.bulk_update([self.line], ["owner_name"])

    def test_b6_values_unchanged_after_orm_attempts(self):
        self.line.refresh_from_db()
        self.assertEqual(self.line.owner_name, "Rana Bashir")
        self.assertEqual(self.line.area_value, Decimal("100.00"))

    # ---------- database path ----------
    @requires_postgresql
    def test_b7_db_update_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE survey_master SET owner_name = 'HACKED' WHERE id = %s",
                    [self.line.pk],
                )

    @requires_postgresql
    def test_b8_db_delete_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute("DELETE FROM survey_master WHERE id = %s", [self.line.pk])

    @requires_postgresql
    def test_b9_db_raw_jsonb_update_blocked_by_trigger(self):
        with self.assertRaisesRegex(DatabaseError, "RUDA-SURVEY"), transaction.atomic():
            with connection.cursor() as cursor:
                cursor.execute(
                    "UPDATE survey_master SET raw_data = raw_data || '{\"x\":1}' WHERE id = %s",
                    [self.line.pk],
                )
        self.line.refresh_from_db()
        self.assertEqual(self.line.raw_data.get("A"), "1")

    def test_b10_original_values_intact_after_all_attempts(self):
        self.line.refresh_from_db()
        self.assertEqual(self.line.owner_name, "Rana Bashir")
        self.assertEqual(self.line.structure_name, "Hawaili i.e. rooms, verenda")
        self.assertTrue(SurveyMaster.objects.filter(pk=self.line.pk).exists())

    def test_b11_bulk_create_insert_only_path_allowed_for_import(self):
        batch = base.make_batch()
        rows = [
            SurveyMaster(
                import_batch=batch,
                source_row_number=100 + i,
                sr_no=90 + i,
                parcel=self.line.parcel,
                project_component="Dam",
                owner_name="Bulk Owner",
                village="Arya Nagar",
                structure_status="Residential",
                impact_extent="Major Impact",
                raw_data={},
            )
            for i in range(3)
        ]
        created = SurveyMaster.objects.bulk_create(rows)
        self.assertEqual(len(created), 3)


class MasterNullAndSentinelTests(TestCase):
    def test_f1_python_none_becomes_database_null(self):
        line = base.make_master_line(
            father_name=None,
            caste=None,
            structure_name=None,
            latitude=None,
            longitude=None,
            area_value=None,
        )
        line.refresh_from_db()
        for field in ("father_name", "caste", "structure_name", "latitude", "longitude", "area_value"):
            self.assertIsNone(getattr(line, field), field)

    def test_f2_dash_sentinel_preserved_as_string(self):
        line = base.make_master_line(father_name="-", in_row_yn="-")
        line.refresh_from_db()
        self.assertEqual(line.father_name, "-")
        self.assertEqual(line.in_row_yn, "-")

    def test_f3_zero_sentinel_preserved_as_number_and_string(self):
        line = base.make_master_line(length_ft=Decimal("0"), width_ft=Decimal("0"),
                                     construction_nature="0")
        line.refresh_from_db()
        self.assertEqual(line.length_ft, Decimal("0"))
        self.assertEqual(line.construction_nature, "0")

    def test_f4_not_identified_sentinel_preserved_verbatim(self):
        line = base.make_master_line(owner_name="Not Identified (Locked)")
        line.refresh_from_db()
        self.assertEqual(line.owner_name, "Not Identified (Locked)")

    def test_f5_required_fields_reject_null_at_model_level(self):
        required = ["project_component", "owner_name", "village", "structure_status", "impact_extent"]
        for field in required:
            f = SurveyMaster._meta.get_field(field)
            self.assertFalse(f.null, f"{field} must be NOT NULL")

    def test_f6_unique_batch_source_row_enforced(self):
        line = base.make_master_line(source_row_number=777)
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_master_line(parcel=line.parcel, batch=line.import_batch,
                                      source_row_number=777)

    def test_f7_sr_no_must_be_positive(self):
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_master_line(sr_no=0)

    def test_f8_named_indexes_present_on_master(self):
        with connection.cursor() as cursor:
            cons = connection.introspection.get_constraints(cursor, SurveyMaster._meta.db_table)
        names = set(cons.keys())
        self.assertIn("idx_master_village", names)
        self.assertIn("idx_master_struct_status", names)
        self.assertIn("uq_survey_master_batch_source_row", names)

    def test_f9_trigger_exists_on_survey_master(self):
        if connection.vendor != "postgresql":
            self.skipTest("PostgreSQL only")
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT 1 FROM pg_trigger WHERE tgrelid = %s::regclass AND tgname = %s",
                ["survey_master", "trg_survey_master_immutable"],
            )
            self.assertIsNotNone(cursor.fetchone())
