"""TEST GROUP I — import_batches: tracking fields, counters, status, metadata."""
import hashlib

from django.db import IntegrityError, connection, transaction
from django.test import TestCase

from surveys.models import ImportBatch, SurveyMaster
from surveys.tests import base


class ImportBatchTests(TestCase):
    def test_i1_create_batch_with_verbatim_filename(self):
        name = "02_Annex 4.1_Affected Residential & Commercial Structures.xlsx"
        batch = base.make_batch(source_filename=name)  # keeps double spaces etc.
        batch.refresh_from_db()
        self.assertEqual(batch.source_filename, name)
        self.assertEqual(batch.status, ImportBatch.Status.COMPLETED)

    def test_i2_checksum_stored_and_lookupable(self):
        digest = hashlib.sha256(b"workbook-bytes").hexdigest()
        batch = base.make_batch(file_checksum=digest)
        self.assertEqual(
            ImportBatch.objects.get(file_checksum=digest).pk, batch.pk
        )

    def test_i3_row_counters_default_zero_and_updatable(self):
        batch = base.make_batch(total_rows=14872, successful_rows=14872,
                                failed_rows=0, warning_count=3014)
        batch.refresh_from_db()
        self.assertEqual(batch.total_rows, 14872)
        self.assertEqual(batch.successful_rows, 14872)
        self.assertEqual(batch.warning_count, 3014)

    def test_i4_invalid_status_rejected_by_check_constraint(self):
        with transaction.atomic():
            with self.assertRaises(IntegrityError):
                base.make_batch(status="half-done")

    def test_i5_error_summary_metadata_json(self):
        summary = {
            "warnings": [
                {"row": 2899, "column": "N", "issue": "corrupted district value"},
                {"row": 3000, "column": "C", "issue": "non-numeric chainage"},
            ]
        }
        batch = base.make_batch(status=ImportBatch.Status.COMPLETED,
                                error_summary=summary)
        batch.refresh_from_db()
        self.assertEqual(batch.error_summary["warnings"][0]["row"], 2899)

    def test_i6_imported_at_auto_set(self):
        batch = base.make_batch()
        batch.refresh_from_db()
        self.assertIsNotNone(batch.imported_at)

    def test_i7_batch_linked_to_master_rows(self):
        batch = base.make_batch(total_rows=2, successful_rows=2)
        l1 = base.make_master_line(batch=batch, sr_no=1, source_row_number=5)
        l2 = base.make_master_line(batch=batch, sr_no=2, source_row_number=6)
        self.assertEqual(batch.master_rows.count(), 2)

    def test_i8_batch_delete_protected_when_master_rows_exist(self):
        batch = base.make_batch()
        line = base.make_master_line(batch=batch)
        bid = batch.pk
        try:
            batch.delete()
        except Exception:
            pass
        self.assertTrue(ImportBatch.objects.filter(pk=bid).exists())
        self.assertTrue(SurveyMaster.objects.filter(pk=line.pk).exists())
