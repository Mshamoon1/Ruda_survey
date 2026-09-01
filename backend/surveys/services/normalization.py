"""Typed coercion + classification rules for the master Excel import.

Every rule here is documented in EXCEL_IMPORT_MAPPING.md and derives from
doc 02 (Parts D/F). Nothing in this module ever *invents* a value:

* blank            -> None (SQL NULL)                      [doc 02 §F.1]
* "-"              -> NULL for numeric fields              [Part D #6/#29]
* unparseable text in a numeric field -> WARNING + NULL    [§F.5]
* "-", "0", "Not Identified" in TEXT fields stay verbatim [§F.2]
"""
import re
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation

# ---------------------------------------------------------------------------
# Severity classes used across parsing / derivation / import reporting.
# ---------------------------------------------------------------------------

INFO = "INFO"
WARNING = "WARNING"
ERROR = "ERROR"


@dataclass
class Issue:
    severity: str          # INFO | WARNING | ERROR
    code: str              # machine-readable, e.g. INVALID_NUMERIC
    message: str
    source_row: int | None = None
    field: str | None = None
    original: object = None
    normalized: object = None

    def as_dict(self):
        return {
            "severity": self.severity,
            "code": self.code,
            "message": self.message,
            "source_row": self.source_row,
            "field": self.field,
            "original": _jsonable(self.original),
            "normalized": _jsonable(self.normalized),
        }


def _jsonable(value):
    if isinstance(value, Decimal):
        return str(value)
    return value


# ---------------------------------------------------------------------------
# Owner-name key normalisation (used ONLY for parcel grouping decisions,
# never stored). Conservative by design: it unifies trivial punctuation /
# case / spacing variants; anything else remains distinct so we can never
# accidentally MERGE two owners. See PARCEL_DERIVATION_RULES.md.
# ---------------------------------------------------------------------------

_HONORIFIC_TOKENS = {"mr", "mrs", "ms", "dr", "haji"}

# Values meaning "identity unknown" — they never drive a split.
_UNIDENTIFIED_RE = re.compile(
    r"(not\s*identified|owner\s*not|locked|not\s*interested|refused|unknown)"
)


def clean_text(value) -> str | None:
    """Whitespace-collapse a raw cell; blank -> None. Never alters content."""
    if value is None:
        return None
    text = " ".join(str(value).split())
    return text if text else None


def owner_key(owner_name_raw) -> str | None:
    """Deterministic grouping key for an owner name, or None when unknown."""
    text = clean_text(owner_name_raw)
    if not text or _UNIDENTIFIED_RE.search(text.lower()):
        return None
    lowered = text.lower().replace(".", " ").replace(",", " ")
    lowered = re.sub(r"[^a-z0-9\s]", " ", lowered)
    tokens = [t for t in lowered.split() if t]
    while tokens and tokens[0] in _HONORIFIC_TOKENS:
        tokens.pop(0)
    if not tokens:
        return None
    return " ".join(tokens)


def village_key(village_raw) -> str | None:
    text = clean_text(village_raw)
    return text.lower() if text else None


# Cells whose text embeds a spreadsheet range artifact, e.g.
# "Lahore+K2C2888:T2888" found at source rows 2899-2900 (doc 01 §A2).
_CORRUPTED_CELL_RE = re.compile(r"\+[A-Z0-9]+:[A-Z0-9]+")


def looks_corrupted(value) -> bool:
    return bool(isinstance(value, str) and _CORRUPTED_CELL_RE.search(value))


# ---------------------------------------------------------------------------
# Numeric coercion
# ---------------------------------------------------------------------------

NUMERIC_FIELDS = {
    "chainage_m": Decimal,
    "affected_persons_count": int,
    "latitude": Decimal,
    "longitude": Decimal,
    "structure_count": int,
    "length_ft": Decimal,
    "width_ft": Decimal,
    "area_value": Decimal,
    "unit_rate_rs": int,
    "compensation_million": Decimal,
    "cl_offset_m": int,
}

# Internal target used when coercing identifier columns (Sr.No / NID).
NUMERIC_FIELDS["__int__"] = int


def coerce_numeric(field_name: str, raw, row_number: int):
    """Return (value, issues). Implements the documented coercion ladder."""
    issues: list[Issue] = []
    target = NUMERIC_FIELDS[field_name]

    if raw is None:
        return None, issues

    if isinstance(raw, bool):
        issues.append(Issue(WARNING, "INVALID_NUMERIC",
                            "boolean cell treated as NULL", row_number, field_name, raw))
        return None, issues

    if isinstance(raw, Decimal):
        value = raw
    elif isinstance(raw, int):
        value = Decimal(raw)
    elif isinstance(raw, float):
        value = Decimal(str(raw))
    elif isinstance(raw, str):
        stripped = raw.strip()
        if not stripped:
            return None, issues
        if stripped == "-":  # documented null-equivalent placeholder
            issues.append(Issue(INFO, "DASH_PLACEHOLDER_NULL",
                                "'-' placeholder normalised to NULL",
                                row_number, field_name, raw))
            return None, issues
        try:
            value = Decimal(stripped)
        except InvalidOperation:
            issues.append(Issue(WARNING, "INVALID_NUMERIC_STORED_AS_NULL",
                                "unparseable numeric text stored as NULL "
                                "(verbatim kept in raw_data)",
                                row_number, field_name, raw))
            return None, issues
    else:
        issues.append(Issue(WARNING, "INVALID_NUMERIC",
                            f"unexpected type {type(raw).__name__} stored as NULL",
                            row_number, field_name, raw))
        return None, issues

    try:
        if target is int:
            if value != value.to_integral_value():
                issues.append(Issue(WARNING, "FRACTIONAL_INT_TRUNCATED",
                                    "fractional value truncated to integer",
                                    row_number, field_name, raw, int(value)))
                return int(value), issues
            return int(value), issues
        return value.quantize(Decimal("0.000001")), issues
    except Exception:  # pragma: no cover - defensive
        issues.append(Issue(WARNING, "INVALID_NUMERIC_STORED_AS_NULL",
                            "conversion failed; stored as NULL",
                            row_number, field_name, raw))
        return None, issues
