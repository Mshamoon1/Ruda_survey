"""Read-only master workbook reader.

Layout (doc 02 §A1):
    row 1      title            "Annex 4.1: Affected Residential & Commercial Structures"
    rows 2-3   two-tier merged headers
    row 4      column numbers 1..28
    rows 5+    data (one row per structure line-item)

The reader opens the workbook TWICE, read-only:
  * data_only=True  -> cached formula results (used for DB values)
  * data_only=False -> raw cell contents (formulas kept as "=..." strings;
                       stored verbatim in raw_data provenance)

If a formula cell has no cached result the row is REJECTED with
FORMULA_NO_CACHE — we never invent a value.
"""
import hashlib
from dataclasses import dataclass, field as dc_field
from decimal import Decimal
from pathlib import Path

import openpyxl
from openpyxl.utils import get_column_letter

from .normalization import (
    ERROR,
    INFO,
    Issue,
    NUMERIC_FIELDS,
    WARNING,
    clean_text,
    coerce_numeric,
    looks_corrupted,
)

HEADER_ROWS = (2, 3)
DATA_START_ROW = 5
EXPECTED_SHEET_PREFIX = "Anex 4.1"
EXPECTED_TITLE_FRAGMENT = "Annex 4.1"

REQUIRED_TEXT_FIELDS = ("project_component", "owner_name", "village", "structure_status", "impact_extent")


@dataclass(frozen=True)
class ColumnSpec:
    letter: str          # physical Excel column
    db_field: str        # survey_master field name ('' for sr_no/source_nid handled too)
    header_must_contain: tuple[str, ...]   # all fragments required in the combined header
    numeric: bool = False


# Physical column -> field map (authoritative; mirrors doc 02 Part D).
COLUMN_SPECS: list[ColumnSpec] = [
    ColumnSpec("A",  "sr_no",                  ("sr", "no")),
    ColumnSpec("B",  "source_nid",             ("nid",)),
    ColumnSpec("C",  "chainage_m",             ("chainage",), numeric=True),
    ColumnSpec("D",  "affected_persons_count", ("affected persons",), numeric=True),
    ColumnSpec("E",  "phase_code",             ("phase",)),
    ColumnSpec("F",  "latitude",               ("north", "lat"), numeric=True),
    ColumnSpec("G",  "longitude",              ("east", "long"), numeric=True),
    ColumnSpec("H",  "project_component",      ("project",)),
    ColumnSpec("I",  "owner_name",             ("owner",)),
    ColumnSpec("J",  "father_name",            ("father",)),
    ColumnSpec("K",  "caste",                  ("caste",)),
    ColumnSpec("L",  "village",                ("village",)),
    ColumnSpec("M",  "tehsil",                 ("tehsil",)),
    ColumnSpec("N",  "district",               ("district",)),
    ColumnSpec("O",  "ownership_documents",    ("ownership documents",)),
    ColumnSpec("P",  "structure_status",       ("status of structure",)),
    ColumnSpec("Q",  "structure_name",         ("structure name",)),
    ColumnSpec("R",  "structure_count",        ("number of structure",), numeric=True),
    ColumnSpec("S",  "tenure_status",          ("status (owner", )),
    ColumnSpec("T",  "length_ft",              ("length",), numeric=True),
    ColumnSpec("U",  "width_ft",               ("width",), numeric=True),
    ColumnSpec("V",  "area_value",             ("area",), numeric=True),
    ColumnSpec("W",  "construction_nature",    ("nature of construction",)),
    ColumnSpec("X",  "unit_rate_rs",           ("unit rate",), numeric=True),
    ColumnSpec("Y",  "compensation_million",   ("compensation",), numeric=True),
    ColumnSpec("Z",  "impact_extent",          ("extent of impact",)),
    ColumnSpec("AA", "river_location",         ("location",)),
    ColumnSpec("AB", "in_row_yn",              ("row (y/n", )),
    ColumnSpec("AC", "cl_offset_m",            ("off-set",), numeric=True),
    # AD is the documented UNNAMED stray tenancy-notes column (47 values).
    ColumnSpec("AD", "extra_note",             ()),
]

ALL_FIELDS = [spec.db_field for spec in COLUMN_SPECS]


class SourceValidationError(Exception):
    """Raised when the workbook does not match the documented master layout."""

    def __init__(self, issues: list[Issue]):
        self.issues = issues
        detail = "; ".join(i.message for i in issues)
        super().__init__(detail)


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _norm_header(value) -> str:
    return " ".join(str(value).lower().split()) if value is not None else ""


def _head_rows(ws, upto: int = 4):
    """Consume rows 1..`upto` of a read-only worksheet in ONE streaming pass."""
    head = []
    for row in ws.iter_rows(min_row=1, max_row=upto, values_only=True):
        head.append(list(row))
    while len(head) < upto:
        head.append([])
    return head


def resolve_headers_from_head(head_rows, ncols: int) -> dict[str, str]:
    combined: dict[str, str] = {}
    for col in range(1, ncols + 1):
        letter = get_column_letter(col)
        parts = []
        for r in HEADER_ROWS:
            if r - 1 < len(head_rows) and col - 1 < len(head_rows[r - 1]):
                normalised = _norm_header(head_rows[r - 1][col - 1])
                if normalised:
                    parts.append(normalised)
        combined[letter] = " | ".join(parts)
    return combined


def resolve_headers(ws) -> dict[str, str]:
    """Combined two-tier header map (works for normal AND read-only sheets)."""
    return resolve_headers_from_head(_head_rows(ws), ws.max_column or len(COLUMN_SPECS))


def validate_headers(combined: dict[str, str]) -> list[Issue]:
    issues: list[Issue] = []
    for spec in COLUMN_SPECS:
        actual = combined.get(spec.letter)
        if spec.header_must_contain:
            missing = [frag for frag in spec.header_must_contain if frag not in (actual or "")]
            if actual is None or missing:
                issues.append(Issue(
                    ERROR, "HEADER_MISMATCH",
                    f"column {spec.letter}: expected header containing "
                    f"{spec.header_must_contain}, found {actual!r}",
                    field=spec.db_field,
                ))
        elif actual:
            # AD must be unnamed; anything else there means the layout changed.
            issues.append(Issue(
                ERROR, "UNEXPECTED_COLUMN",
                f"column {spec.letter} expected to be the unnamed stray notes "
                f"column but carries header {actual!r}",
                field=spec.db_field,
            ))
    return issues


@dataclass
class ParsedRow:
    source_row_number: int
    sr_no: int | None
    source_nid: int | None
    values: dict = dc_field(default_factory=dict)     # db_field -> python value
    raw_data: dict = dc_field(default_factory=dict)   # column letter -> verbatim cell
    is_formula_area: bool = False
    is_formula_compensation: bool = False
    issues: list[Issue] = dc_field(default_factory=list)

    @property
    def rejected(self) -> bool:
        return any(i.severity == ERROR for i in self.issues)


def _jsonable(cell_value):
    """Verbatim-but-JSON-safe representation for raw_data."""
    import datetime

    if isinstance(cell_value, (datetime.datetime, datetime.date)):
        return cell_value.isoformat()
    if isinstance(cell_value, Decimal):
        return str(cell_value)
    return cell_value


def parse_master_workbook(path: str | Path) -> list[ParsedRow]:
    """Full structural validation + row parsing. Raises SourceValidationError
    on workbook-level problems (missing sheet / bad headers / wrong title)."""
    path = Path(path)
    if not path.exists():
        raise SourceValidationError([Issue(ERROR, "FILE_NOT_FOUND", f"{path} does not exist")])

    try:
        wb_cached = openpyxl.load_workbook(path, data_only=True, read_only=True)
    except Exception as exc:
        raise SourceValidationError([Issue(
            ERROR, "FILE_UNREADABLE",
            f"workbook could not be opened as xlsx: {type(exc).__name__}: {exc}",
        )]) from exc
    try:
        sheet_names = wb_cached.sheetnames
        target = next((name for name in sheet_names
                       if EXPECTED_SHEET_PREFIX.lower() in name.lower()), None)
        if len(sheet_names) != 1 or target is None:
            raise SourceValidationError([Issue(
                ERROR, "UNEXPECTED_SHEET_STRUCTURE",
                f"expected exactly one '{EXPECTED_SHEET_PREFIX}…' sheet, found {sheet_names!r}")])
        ws_cached = wb_cached[target]
        head = _head_rows(ws_cached)
        if EXPECTED_TITLE_FRAGMENT.lower() not in _norm_header(head[0][0]):
            raise SourceValidationError([Issue(
                ERROR, "UNEXPECTED_TITLE",
                f"row 1 title does not contain {EXPECTED_TITLE_FRAGMENT!r}")])

        combined = resolve_headers_from_head(head, len(COLUMN_SPECS))
        header_issues = validate_headers(combined)
        if header_issues:
            raise SourceValidationError(header_issues)

        wb_formula = openpyxl.load_workbook(path, data_only=False, read_only=True)
        try:
            ws_formula = wb_formula[target]
            return _parse_rows(ws_cached, ws_formula)
        finally:
            wb_formula.close()
    finally:
        wb_cached.close()


def _parse_rows(ws_cached, ws_formula) -> list[ParsedRow]:
    parsed: list[ParsedRow] = []
    cached_iter = ws_cached.iter_rows(min_row=DATA_START_ROW,
                                      max_col=len(COLUMN_SPECS), values_only=True)
    formula_iter = ws_formula.iter_rows(min_row=DATA_START_ROW,
                                        max_col=len(COLUMN_SPECS), values_only=True)
    # single streaming pass over BOTH sheets in lockstep (read-only mode
    # cannot do random access without re-parsing the whole sheet)
    for row_index, (row_cached, row_formula) in enumerate(
            zip(cached_iter, formula_iter), start=DATA_START_ROW):
        if not any(v is not None and str(v).strip() != "" for v in row_cached):
            continue  # fully blank spacer row — skip silently (none exist in master)

        pr = ParsedRow(source_row_number=row_index, sr_no=None, source_nid=None)

        rejected = False
        for idx, spec in enumerate(COLUMN_SPECS):
            letter = spec.letter
            cached = row_cached[idx]
            formula_view = row_formula[idx]
            is_formula = isinstance(formula_view, str) and formula_view.startswith("=")

            pr.raw_data[letter] = _jsonable(formula_view)

            if looks_corrupted(cached):
                pr.issues.append(Issue(WARNING, "SUSPECTED_CORRUPTED_CELL",
                                       "cell contains a pasted range artifact "
                                       "(imported verbatim)",
                                       row_index, spec.db_field, cached))

            # --- identifier columns -------------------------------------
            if spec.db_field == "sr_no":
                pr.sr_no = int(cached) if isinstance(cached, (int, float)) else None
                if pr.sr_no is None:
                    pr.issues.append(Issue(ERROR, "SR_NO_UNREADABLE",
                                           "Sr.No cell is not numeric", row_index,
                                           "sr_no", cached))
                    rejected = True
                continue

            if spec.db_field == "source_nid":
                if cached is None or (isinstance(cached, str) and not cached.strip()):
                    pr.source_nid = None
                else:
                    nid, n_issues = coerce_numeric("__int__", cached, row_index)
                    pr.source_nid = int(nid) if nid is not None else None
                    for issue in n_issues:
                        issue.field = "source_nid"
                    pr.issues.extend(n_issues)
                continue

            # --- text columns -------------------------------------------
            if not spec.numeric:
                text = clean_text(cached)
                if spec.db_field in REQUIRED_TEXT_FIELDS and text is None:
                    pr.issues.append(Issue(ERROR, "REQUIRED_BLANK",
                                           "required field is blank", row_index,
                                           spec.db_field))
                    rejected = True
                pr.values[spec.db_field] = text
                continue

            # --- numeric columns ----------------------------------------
            value, num_issues = coerce_numeric(spec.db_field, cached, row_index)
            for issue in num_issues:
                issue.field = spec.db_field
            pr.issues.extend(num_issues)

            if is_formula and cached is None:
                pr.issues.append(Issue(ERROR, "FORMULA_NO_CACHE",
                                       "formula cell has no cached result; refusing "
                                       "to invent a value", row_index, spec.db_field,
                                       formula_view))
                rejected = True

            if spec.db_field == "area_value" and is_formula:
                pr.is_formula_area = True
            if spec.db_field == "compensation_million" and is_formula:
                pr.is_formula_compensation = True

            pr.values[spec.db_field] = value

        if rejected:
            # keep the row object so the error report can cite it, but flag it
            pr.issues.append(Issue(ERROR, "ROW_REJECTED",
                                   "row contains blocking errors and was not imported",
                                   row_index))
        parsed.append(pr)
    return parsed
