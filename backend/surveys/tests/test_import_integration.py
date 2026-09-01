"""PHASE 3 integration tests — Groups F–M against the REAL master workbook.

These tests exercise the actual source file READ-ONLY (hash-pinned). The
write-path runs inside the throwaway PostgreSQL test database created by
Django's test runner.
"""
import shutil
import tempfile
import unittest
from pathlib import Path

from django.conf import settings
from django.test import TestCase

from surveys.models import ImportBatch, Parcel, SurveyMaster
from surveys.services.excel_import import ExcelImportService
from surveys.services.excel_reader import ALL_FIELDS, parse_master_workbook, sha256_file

PROJECT_DIR = Path(settings.BASE_DIR).parent  # D:\Ruda_survey
MASTER_PATH = PROJECT_DIR / "02_Annex 4.1_Affected Residential & Commercial Structures.xlsx"

PINNED_SHA256 = ("b04dc2b708f2a2ba324199fafdc5ad37d61f2f4bf1c4dab4fa81e79"
                 "9a949c252")  # Phase 1 pin, lowercase hexdigest form
EXPECTED_SOURCE_ROWS = 14872
EXPECTED_NULL_NID_ROWS = 2282
EXPECTED_DISTINCT_PARCEL_FAMILIES_MIN = 4000

SYSTEM_FIELDS = {
    "id", "import_batch", "parcel", "source_row_number",
    "is_formula_area", "is_formula_compensation", "raw_data", "imported_at",
    "khasra_number",  # Phase 10A: field-survey addition, not in source Excel
}

real_file_available = unittest.skipUnless(
    MASTER_PATH.exists(), "real master workbook not present")


class MappingCompletenessTest(TestCase):
    """Every mapped Excel field must exist on the model — nothing dropped."""

    def test_excel_mapping_covers_model_exactly(self):
        model_fields = {
            f.name for f in SurveyMaster._meta.concrete_fields
        } - SYSTEM_FIELDS
        self.assertEqual(set(ALL_FIELDS), model_fields)


@real_file_available
class SourceIntegrityTest(TestCase):
    """TEST GROUP L."""

    def test_l1_source_file_matches_phase1_pinned_hash(self):
        self.assertEqual(sha256_file(MASTER_PATH), PINNED_SHA256)

    def test_l2_hash_identical_before_and_after_import(self):
        before = sha256_file(MASTER_PATH)
        result = ExcelImportService(MASTER_PATH).run(dry_run=True)
        after = sha256_file(MASTER_PATH)
        self.assertEqual(before, after)
        self.assertEqual(before, PINNED_SHA256)
        self.assertEqual(result.total_source_rows, EXPECTED_SOURCE_ROWS)


@real_file_available
class DryRunTests(TestCase):
    """TEST GROUP K — full validation pipeline, ZERO database writes."""

    def test_k1_dry_run_produces_full_report_and_no_rows(self):
        result = ExcelImportService(MASTER_PATH).run(dry_run=True)

        self.assertEqual(result.status, "DRY_RUN_OK")
        self.assertEqual(result.total_source_rows, EXPECTED_SOURCE_ROWS)
        self.assertEqual(result.rejected_rows, 0)
        self.assertGreaterEqual(result.derived_parcels,
                                EXPECTED_DISTINCT_PARCEL_FAMILIES_MIN)
        self.assertIsNone(result.batch_id)

        # quality metrics reflect Phase 1 audit numbers exactly
        q = result.quality
        self.assertEqual(q["total_parsed_rows"], EXPECTED_SOURCE_ROWS)
        self.assertEqual(q["null_nid_rows"], EXPECTED_NULL_NID_ROWS)
        self.assertGreater(q["multi_owner_nid_count"], 0)
        self.assertIn(2899, q["corrupted_cell_rows"])
        self.assertGreater(q["near_duplicate_tuple_kinds"], 0)

        # ZERO database records
        self.assertFalse(Parcel.objects.exists())
        self.assertFalse(SurveyMaster.objects.exists())
        self.assertFalse(ImportBatch.objects.exists())


@real_file_available
class FullImportLifecycleTests(TestCase):
    """Groups F, G, H, I, M — real import into the ephemeral test database."""

    def setUp(self):
        self.result = ExcelImportService(MASTER_PATH).run()

    def test_f1_import_completed_with_expected_counts(self):
        self.assertEqual(self.result.status, "COMPLETED")
        self.assertEqual(self.result.imported_master_records, EXPECTED_SOURCE_ROWS)
        self.assertEqual(SurveyMaster.objects.count(), EXPECTED_SOURCE_ROWS)
        self.assertEqual(self.result.new_parcels, self.result.derived_parcels)
        self.assertEqual(Parcel.objects.count(), self.result.derived_parcels)

    def test_m1_every_row_has_complete_provenance(self):
        missing = SurveyMaster.objects.filter(
            raw_data__isnull=True,
        ).count()
        self.assertEqual(missing, 0)
        orphan_check = SurveyMaster.objects.filter(
            parcel__isnull=True,
        ) | SurveyMaster.objects.filter(import_batch__isnull=True)
        self.assertEqual(orphan_check.count(), 0)
        no_sr = SurveyMaster.objects.filter(source_row_number__isnull=True).count()
        self.assertEqual(no_sr, 0)

    def test_m2_spot_check_first_real_record(self):
        row5 = SurveyMaster.objects.get(source_row_number=5)
        self.assertEqual(row5.sr_no, 1)
        self.assertEqual(row5.owner_name, "Rana Bashir")
        self.assertEqual(row5.village, "Arya Nagar")
        self.assertEqual(str(row5.area_value), "859.07")
        self.assertTrue(row5.is_formula_area)
        self.assertEqual(row5.parcel.parcel_code, "RUDA-P14-R00005")
        self.assertEqual(row5.source_nid, 1)

    def test_m3_null_and_sentinel_discipline_holds_in_db(self):
        # continuation NULL NIDs preserved as NULL
        self.assertEqual(
            SurveyMaster.objects.filter(source_nid__isnull=True).count(),
            EXPECTED_NULL_NID_ROWS,
        )
        # genuine sentinels stay verbatim
        self.assertTrue(
            SurveyMaster.objects.filter(father_name="-").exists())
        self.assertTrue(
            SurveyMaster.objects.filter(owner_name="Not Identified").exists())

    def test_g1_second_identical_import_is_recognised_duplicate(self):
        again = ExcelImportService(MASTER_PATH).run()
        self.assertEqual(again.status, "DUPLICATE")
        self.assertTrue(again.already_imported)
        self.assertIsNone(again.batch_id)
        self.assertEqual(SurveyMaster.objects.count(), EXPECTED_SOURCE_ROWS)
        self.assertEqual(Parcel.objects.count(), self.result.derived_parcels)

    def test_g2_existing_master_unchanged_after_reimport_attempt(self):
        row5 = SurveyMaster.objects.get(source_row_number=5)
        snapshot = {f: getattr(row5, f) for f in
                    ("owner_name", "village", "area_value", "raw_data")}
        ExcelImportService(MASTER_PATH).run()
        row5.refresh_from_db()
        for f, value in snapshot.items():
            self.assertEqual(getattr(row5, f), value)

    def test_h1_triple_import_yields_one_logical_dataset(self):
        for _ in range(2):
            extra = ExcelImportService(MASTER_PATH).run()
            self.assertEqual(extra.status, "DUPLICATE")
        self.assertEqual(SurveyMaster.objects.count(), EXPECTED_SOURCE_ROWS)
        self.assertEqual(Parcel.objects.count(), self.result.derived_parcels)
        self.assertEqual(ImportBatch.objects.count(), 1)

    def test_i1_import_batch_recorded_completely(self):
        batch = ImportBatch.objects.get(pk=self.result.batch_id)
        self.assertEqual(batch.status, ImportBatch.Status.COMPLETED)
        self.assertEqual(batch.file_checksum, PINNED_SHA256)
        self.assertEqual(batch.source_filename, MASTER_PATH.name)
        self.assertEqual(batch.total_rows, EXPECTED_SOURCE_ROWS)
        self.assertEqual(batch.successful_rows, EXPECTED_SOURCE_ROWS)
        self.assertEqual(batch.failed_rows, 0)
        self.assertIsNotNone(batch.imported_at)
        self.assertIn("quality", batch.error_summary)

    def test_j1_resaved_copy_blocked_as_overlapping_source(self):
        """Different bytes, same rows -> must refuse instead of duplicating."""
        import zipfile

        tmp_dir = Path(tempfile.mkdtemp(prefix="ruda_resave_"))
        try:
            # Repack the xlsx container (it is a zip) with an extra marker
            # entry: identical spreadsheet content, different SHA-256,
            # produced in milliseconds (no openpyxl round-trip needed).
            resaved = tmp_dir / "resaved.xlsx"
            with zipfile.ZipFile(MASTER_PATH) as zin, \
                    zipfile.ZipFile(resaved, "w", zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    zout.writestr(item.filename, zin.read(item.filename))
                zout.writestr("ruda_reimport_marker.txt", "re-saved copy")
            self.assertNotEqual(sha256_file(resaved), PINNED_SHA256)

            blocked = ExcelImportService(resaved).run()
            self.assertEqual(blocked.status, "FAILED")
            codes = [e["code"] for e in blocked.errors]
            self.assertIn("OVERLAPPING_SOURCE", codes)
            self.assertEqual(
                SurveyMaster.objects.filter(
                    import_batch__file_checksum=sha256_file(resaved)).count(),
                0,
            )

            # documented operator escape: intentional corrected re-import
            forced = ExcelImportService(resaved, allow_overlap=True).run()
            self.assertEqual(forced.status, "COMPLETED")
            self.assertEqual(forced.imported_master_records, EXPECTED_SOURCE_ROWS)
            self.assertEqual(
                ImportBatch.objects.count(), 2,
                "forced re-import must create a NEW batch, never mutate batch 1",
            )
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)
