"""PHASE 3 unit tests — Groups C (parcel derivation), D (normalization),
E (formula handling). Pure in-memory fixtures; no real Excel files touched."""
from decimal import Decimal

from django.test import TestCase

from surveys.services.excel_reader import ParsedRow, parse_master_workbook
from surveys.services.normalization import (
    WARNING,
    Issue,
    coerce_numeric,
    looks_corrupted,
    owner_key,
)
from surveys.services.parcel_derivation import (
    derive_parcel_groups,
    parcel_code_for,
)
from surveys.tests.import_fixtures import DEFAULT_DATA_ROW, write_workbook

import shutil
import tempfile
from pathlib import Path


def _rows(nid, owner, village="Arya Nagar", start=5, count=1, sr_start=1):
    out = []
    for offset in range(count):
        out.append(ParsedRow(
            source_row_number=start + offset,
            sr_no=sr_start + offset,
            source_nid=nid,
            values={
                "owner_name": owner,
                "village": village,
                "project_component": "Dam",
                "structure_status": "Residential",
                "impact_extent": "Major Impact",
            },
            raw_data={},
        ))
    return out


class ParcelDerivationTests(TestCase):
    """TEST GROUP C — deterministic grouping rules."""

    def test_c1_single_row_single_parcel_deterministic_code(self):
        groups = derive_parcel_groups(_rows(1, "Rana Bashir"))
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].parcel_code, "RUDA-P14-R00005")
        self.assertEqual(groups[0].parcel_code, parcel_code_for(5))

    def test_c2_multiple_rows_same_parcel(self):
        groups = derive_parcel_groups(_rows(7, "Rana Bashir", count=4))
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].row_count, 4)

    def test_c3_null_nid_rows_are_continuations(self):
        rows = _rows(9, "Ali Raza", count=2)
        rows += _rows(None, "Ali Raza", start=7, count=3, sr_start=3)
        groups = derive_parcel_groups(rows)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].row_count, 5)
        # block header NID is retained for the whole parcel (fill-down rule)
        self.assertEqual(groups[0].source_nid, 9)

    def test_c4_duplicate_nid_in_separate_blocks_not_merged(self):
        rows = (_rows(7, "Owner A", start=5, count=2)
                + _rows(8, "Owner B", start=7)
                + _rows(7, "Owner A", start=8, count=2, sr_start=3))
        groups = derive_parcel_groups(rows)
        codes = [g.parcel_code for g in groups]
        self.assertEqual(len(codes), 3)
        self.assertEqual(len(set(codes)), 3)  # no accidental merge across the gap
        # each block anchored to its own first source row
        self.assertEqual(codes, ["RUDA-P14-R00005", "RUDA-P14-R00007",
                                 "RUDA-P14-R00008"])

    def test_c5_catchall_nid_split_per_owner_606_style(self):
        rows = (_rows(606, "Abdul Rahim", start=5, count=2)
                + _rows(606, "Abdullah Ashfaq", start=7)
                + _rows(606, "Mr. Samira Zafar", start=8))
        groups = derive_parcel_groups(rows)
        self.assertEqual(len(groups), 3)
        self.assertTrue(all(g.classification == "AMBIGUOUS" for g in groups))
        notes = [i for g in groups for i in g.issues
                 if i.code == "SPLIT_FROM_MULTI_OWNER_NID"]
        # every split boundary flags BOTH sides -> 2 notes per boundary
        self.assertEqual(len(notes), 2 * (len(groups) - 1))
        # each sub-parcel anchored to its own first source row
        self.assertEqual([g.parcel_code for g in groups],
                         ["RUDA-P14-R00005", "RUDA-P14-R00007", "RUDA-P14-R00008"])

    def test_c6_owner_spelling_variants_do_not_split(self):
        rows = _rows(100, "mr. arshad ali", start=5) + _rows(100, "Arshad Ali", start=6)
        groups = derive_parcel_groups(rows)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].classification, "VALID")

    def test_c7_unidentified_owners_never_split(self):
        rows = (_rows(11, "Not Identified")
                + _rows(11, "Not Identified (Locked)")
                + _rows(11, "Owner not Identified"))
        groups = derive_parcel_groups(rows)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].classification, "VALID")

    def test_c8_repeated_derivation_identical_codes(self):
        rows = (_rows(606, "A B", start=5, count=2) + _rows(None, "A B", start=7)
                + _rows(12, "C D", start=8) + _rows(12, "E F", start=9))
        first = [(g.parcel_code, g.first_row, g.row_count)
                 for g in derive_parcel_groups(rows)]
        second = [(g.parcel_code, g.first_row, g.row_count)
                  for g in derive_parcel_groups(rows)]
        self.assertEqual(first, second)

    def test_c9_village_drift_warns_without_splitting(self):
        rows = _rows(21, "Same Owner") + _rows(21, "Same Owner", village="Other Village")
        groups = derive_parcel_groups(rows)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0].classification, "WARNING")


class NormalizationTests(TestCase):
    """TEST GROUP D."""

    def test_d1_blank_becomes_none(self):
        value, issues = coerce_numeric("chainage_m", "", 5)
        self.assertIsNone(value)
        self.assertEqual(issues, [])

    def test_d2_numeric_string_becomes_numeric(self):
        value, issues = coerce_numeric("chainage_m", "15250", 5)
        self.assertEqual(value, Decimal("15250"))
        self.assertEqual(issues, [])

    def test_d3_valid_numeric_passes_through(self):
        value, issues = coerce_numeric("latitude", 31.70384951, 5)
        self.assertEqual(value, Decimal("31.703850").quantize(Decimal("0.000001")))
        self.assertEqual(issues, [])

    def test_d4_dash_sentinel_null_for_numeric_fields(self):
        for field_name in ("latitude", "longitude", "cl_offset_m"):
            value, issues = coerce_numeric(field_name, "-", 5)
            self.assertIsNone(value, field_name)
            self.assertTrue(any(i.code == "DASH_PLACEHOLDER_NULL" for i in issues))

    def test_d5_text_sentinels_preserved_by_reader(self):
        tmp = Path(tempfile.mkdtemp(prefix="ruda_norm_"))
        try:
            data = {**DEFAULT_DATA_ROW, "B": 3, "J": "-", "W": "0",
                    "I": "Not Identified"}
            path = tmp / "sentinels.xlsx"
            write_workbook(path, [data])
            parsed = parse_master_workbook(path)[0]
            self.assertEqual(parsed.values["father_name"], "-")
            self.assertEqual(parsed.values["construction_nature"], "0")
            self.assertEqual(parsed.values["owner_name"], "Not Identified")
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def test_d6_invalid_numeric_warns_and_stores_null_not_rejects(self):
        value, issues = coerce_numeric("unit_rate_rs", "abc123", 42)
        self.assertIsNone(value)
        self.assertTrue(any(i.severity == WARNING
                            and i.code == "INVALID_NUMERIC_STORED_AS_NULL"
                            for i in issues))

    def test_d7_corrupted_cell_detector(self):
        self.assertTrue(looks_corrupted("Lahore+K2C2888:T2888"))
        self.assertFalse(looks_corrupted("Lahore"))

    def test_d8_owner_key_normalisation_contract(self):
        self.assertEqual(owner_key("Mr. Arshad Ali"), owner_key("arshad ali"))
        self.assertEqual(owner_key("M.Rafiq"), None or owner_key("m rafiq"))
        self.assertIsNone(owner_key("Not Identified (Locked)"))


class FormulaHandlingTests(TestCase):
    """TEST GROUP E — formulas are read from cache; never invented."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="ruda_formula_"))

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_e1_literal_area_and_compensation_imported_verbatim(self):
        data = {**DEFAULT_DATA_ROW, "B": 4, "V": 100, "Y": 0.22}
        path = self.tmp / "literals.xlsx"
        write_workbook(path, [data])
        row = parse_master_workbook(path)[0]
        self.assertEqual(row.values["area_value"], Decimal("100"))
        self.assertEqual(row.values["compensation_million"], Decimal("0.220000"))
        self.assertFalse(row.is_formula_area)

    def test_e2_formula_without_cached_result_rejects_row(self):
        """openpyxl-written '=U5*T5' has NO cached value -> must refuse."""
        data = {**DEFAULT_DATA_ROW, "B": 5, "V": "=U5*T5"}
        path = self.tmp / "formula.xlsx"
        write_workbook(path, [data])
        row = parse_master_workbook(path)[0]
        codes = {i.code for i in row.issues}
        self.assertIn("FORMULA_NO_CACHE", codes)
        self.assertIn("ROW_REJECTED", codes)
        self.assertTrue(row.rejected)

    def test_e3_formula_provenance_flags_map_to_correct_fields(self):
        data = {**DEFAULT_DATA_ROW, "B": 6, "Y": "=(V6*X6)/1000000"}
        path = self.tmp / "comp_formula.xlsx"
        write_workbook(path, [data])
        row = parse_master_workbook(path)[0]
        self.assertIn("FORMULA_NO_CACHE",
                      {i.code for i in row.issues if i.field == "compensation_million"})
