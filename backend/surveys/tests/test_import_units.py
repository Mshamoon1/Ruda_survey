"""PHASE 3 unit tests — Groups A (file validation) and B (header mapping).

All fixtures are synthetic workbooks written to a temp dir; the real Excel
source files are never touched here.
"""
import hashlib
import shutil
import tempfile
from pathlib import Path

from django.test import TestCase

from surveys.services.excel_import import ExcelImportService
from surveys.services.excel_reader import (
    ALL_FIELDS,
    COLUMN_SPECS,
    SourceValidationError,
    parse_master_workbook,
    resolve_headers,
    sha256_file,
)
from surveys.tests.import_fixtures import DEFAULT_DATA_ROW, write_workbook


def _row(**overrides):
    return {**DEFAULT_DATA_ROW, **overrides}


class TempWorkbookCase(TestCase):
    """Base class providing an isolated temp directory per test class."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.tmp_dir = Path(tempfile.mkdtemp(prefix="ruda_xlsx_"))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp_dir, ignore_errors=True)
        super().tearDownClass()

    def _make_file(self, data_rows, name=None, **kwargs):
        short = self.id().rsplit(".", 1)[-1]
        path = self.tmp_dir / (name or f"{short}.xlsx")
        write_workbook(path, data_rows, **kwargs)
        return path


class FileValidationTests(TempWorkbookCase):
    """TEST GROUP A."""

    def test_a1_correct_workbook_accepted(self):
        rows = [_row(B=1), _row(A=2, B=1)]
        result = ExcelImportService(self._make_file(rows)).run(dry_run=True)
        self.assertEqual(result.status, "DRY_RUN_OK")
        self.assertEqual(result.total_source_rows, 2)

    def test_a2_wrong_workbook_rejected(self):
        path = self._make_file([_row()], title="Some other project's export")
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        self.assertIn("UNEXPECTED_TITLE", {i.code for i in ctx.exception.issues})

    def test_a3_missing_or_wrongly_named_sheet_rejected(self):
        path = self._make_file([_row()], sheet="Totally Wrong")
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        self.assertIn("UNEXPECTED_SHEET_STRUCTURE",
                      {i.code for i in ctx.exception.issues})

    def test_a4_wrong_header_rejected(self):
        path = self._make_file([_row()],
                               headers_row2={"B": "Notice Number"})  # needs 'nid'
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        issues = [i for i in ctx.exception.issues if i.code == "HEADER_MISMATCH"]
        self.assertTrue(any(i.field == "source_nid" for i in issues))

    def test_a5_malformed_excel_rejected(self):
        path = self.tmp_dir / "garbage.xlsx"
        path.write_bytes(b"this is definitely not a zip archive")
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        self.assertIn("FILE_UNREADABLE", {i.code for i in ctx.exception.issues})

    def test_a6_checksum_generated_correctly(self):
        path = self._make_file([_row()])
        expected = hashlib.sha256(path.read_bytes()).hexdigest()
        self.assertEqual(sha256_file(path), expected)


class HeaderMappingTests(TempWorkbookCase):
    """TEST GROUP B."""

    def _combined(self):
        wb_path = self._make_file([_row()])
        import openpyxl

        ws = openpyxl.load_workbook(wb_path).worksheets[0]
        return resolve_headers(ws)

    def test_b1_multi_row_headers_combined(self):
        combined = self._combined()
        self.assertIn("north", combined["F"])
        self.assertIn("lat", combined["F"])
        self.assertIn("coordinates", combined["F"])  # tier-1 group label present

    def test_b2_all_expected_columns_map_to_fields(self):
        # 30 physical columns == 30 mapped fields (28 data + 2 identifiers)
        self.assertEqual(len(ALL_FIELDS), 30)
        self.assertEqual(len(set(ALL_FIELDS)), 30)

    def test_b3_unexpected_named_column_reported(self):
        path = self._make_file([_row()], headers_row2={"AD": "Surprise Column"})
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        self.assertIn("UNEXPECTED_COLUMN", {i.code for i in ctx.exception.issues})

    def test_b4_missing_required_column_reported(self):
        path = self._make_file([_row()],
                               headers_row2={"I": None},   # kill 'Identification' tier-1
                               headers_row3={"I": None})   # and Owner's Name tier-2
        with self.assertRaises(SourceValidationError) as ctx:
            parse_master_workbook(path)
        mismatched = [i.field for i in ctx.exception.issues
                      if i.code == "HEADER_MISMATCH"]
        self.assertIn("owner_name", mismatched)

    def test_b5_normalised_header_variants_match(self):
        path = self._make_file([_row(B=1)], headers_row2={"B": "  nid  "})
        parsed = parse_master_workbook(path)
        self.assertEqual(parsed[0].source_nid, 1)

    def test_b6_numeric_spec_matches_coercion_registry(self):
        """Every numeric-flagged column must exist in the coercion registry
        and vice versa (prevents silent type leaks into numeric columns)."""
        from surveys.services.normalization import NUMERIC_FIELDS

        flagged = {s.db_field for s in COLUMN_SPECS if s.numeric}
        registry = set(NUMERIC_FIELDS) - {"__int__"}
        self.assertEqual(flagged, registry)
