"""Deterministic parcel derivation from parsed master rows.

Implements PARCEL_DERIVATION_RULES.md (grounded in doc 02 §C3/§G):

Grouping (strict source order):
  1. A maximal contiguous run of rows sharing the same non-null NID is one
     candidate block; NULL-NID rows are continuations of the current block
     (Phase 1 proved zero orphan NULL-NID runs).
  2. Within a block, a change of the normalised owner key SPLITS the block
     into two parcels (documented rule doc 02 §C3.3: polluted NID blocks are
     split per owner-group). Unidentified owners never drive a split.
  3. A different non-null NID always closes the current block.

parcel_code:
    RUDA-P14-R{first_source_row:05d}      e.g. RUDA-P14-R00005
  * anchored to the group's first physical Excel row -> traceable, unique
    (groups partition the row space), stable across re-imports of the same
    file, independent of DB autoincrement, never a UUID or raw NID.

The splitter is deliberately CONSERVATIVE: it may split same-person spelling
variants apart (reported as SPLIT_FROM_MULTI_OWNER_NID warnings), but it can
never merge two distinct parcels.
"""
from dataclasses import dataclass, field as dc_field

from .normalization import WARNING, Issue, owner_key, village_key

PACKAGE_PREFIX = "RUDA-P14"


@dataclass
class ParcelGroup:
    parcel_code: str
    source_nid: int | None
    first_row: int
    last_row: int
    rows: list                       # list[ParsedRow]
    owner_name_current: str | None
    village: str | None
    tehsil: str | None
    district: str | None
    classification: str = "VALID"    # VALID | WARNING | AMBIGUOUS
    issues: list = dc_field(default_factory=list)

    @property
    def row_count(self) -> int:
        return len(self.rows)


def parcel_code_for(first_source_row: int) -> str:
    return f"{PACKAGE_PREFIX}-R{first_source_row:05d}"


def derive_parcel_groups(parsed_rows) -> list[ParcelGroup]:
    """Build the deterministic parcel groups from ordered ParsedRows.

    Rejected rows (blocking errors) do not participate in grouping but are
    attached to the group whose span they fall inside so the error report can
    cite them; they are excluded from import later.
    """
    groups: list[ParcelGroup] = []

    def close(group: ParcelGroup | None):
        if group is not None and group.rows:
            groups.append(group)

    current: ParcelGroup | None = None
    current_owner_key: str | None = None
    current_village_key: str | None = None

    for row in parsed_rows:
        nid = row.source_nid
        okey = owner_key(row.values.get("owner_name"))
        vkey = village_key(row.values.get("village"))
        raw_owner = row.values.get("owner_name")

        new_block = False
        split_within_block = False

        if current is None:
            new_block = True
        elif nid is not None and current.source_nid is not None and nid != current.source_nid:
            # different NID -> hard boundary
            close(current)
            current, current_owner_key, current_village_key = None, None, None
            new_block = True
        elif nid is None:
            # continuation row (fill-down rule); drift only produces warnings
            if okey and current_owner_key and okey != current_owner_key:
                current.issues.append(Issue(
                    WARNING, "CONTINUATION_OWNER_DRIFT",
                    f"NULL-NID continuation row shows different owner "
                    f"{raw_owner!r}; kept inside parcel {current.parcel_code}",
                    row.source_row_number, "owner_name", raw_owner))
                current.classification = "WARNING"
            if vkey and current_village_key and vkey != current_village_key:
                current.issues.append(Issue(
                    WARNING, "VILLAGE_DRIFT",
                    f"village changed to {row.values.get('village')!r} inside a "
                    f"single parcel; kept (not a documented split signal)",
                    row.source_row_number, "village", row.values.get("village")))
                current.classification = "WARNING"
        else:
            # same non-null NID continues unless the owner identity changes
            if okey and current_owner_key and okey != current_owner_key:
                # owner identity changed inside one NID block -> conservative
                # split per doc 02 §C3.3; BOTH halves are flagged AMBIGUOUS
                previous_owner = current.owner_name_current
                current.classification = "AMBIGUOUS"
                current.issues.append(Issue(
                    WARNING, "SPLIT_FROM_MULTI_OWNER_NID",
                    f"NID {nid} spans multiple owners ({previous_owner!r} -> "
                    f"{raw_owner!r}); block conservatively split per doc 02 §C3.3",
                    row.source_row_number, "source_nid", nid))
                close(current)
                current, current_owner_key, current_village_key = None, None, None
                new_block = True
                split_within_block = True
            else:
                if okey and current_owner_key is None:
                    current_owner_key = okey   # first identified owner claims block header
                if vkey and current_village_key and vkey != current_village_key:
                    current.issues.append(Issue(
                        WARNING, "VILLAGE_DRIFT",
                        f"village changed to {row.values.get('village')!r} inside "
                        f"NID {nid}; kept (not a documented split signal)",
                        row.source_row_number, "village", row.values.get("village")))
                    current.classification = "WARNING"

        if new_block:
            code = parcel_code_for(row.source_row_number)
            classification = "AMBIGUOUS" if split_within_block else "VALID"
            current = ParcelGroup(
                parcel_code=code,
                source_nid=nid,
                first_row=row.source_row_number,
                last_row=row.source_row_number,
                rows=[],
                owner_name_current=raw_owner,
                village=row.values.get("village"),
                tehsil=row.values.get("tehsil"),
                district=row.values.get("district"),
                classification=classification,
            )
            if split_within_block:
                current.issues.append(Issue(
                    WARNING, "SPLIT_FROM_MULTI_OWNER_NID",
                    f"continuation half of the split block; see preceding parcel",
                    row.source_row_number, "source_nid", nid))

        # attach row to current group
        if okey and current.owner_name_current is None:
            current.owner_name_current = raw_owner
        current.rows.append(row)
        current.last_row = row.source_row_number
        if current_owner_key is None and okey:
            current_owner_key = okey
        if current_village_key is None and vkey:
            current_village_key = vkey

        if any(i.code == "SUSPECTED_CORRUPTED_CELL" for i in row.issues):
            current.classification = (
                "WARNING" if current.classification == "VALID" else current.classification)

    close(current)
    return groups


def quality_summary(groups: list[ParcelGroup], parsed_rows) -> dict:
    """Aggregate data-quality metrics required by the Phase 3 report."""
    null_nid_rows = sum(1 for r in parsed_rows if r.source_nid is None)
    nid_owners: dict[int, set[str]] = {}
    for g in groups:
        if g.source_nid is None:
            continue
        for r in g.rows:
            k = owner_key(r.values.get("owner_name"))
            if k:
                nid_owners.setdefault(g.source_nid, set()).add(k)

    multi_owner_nids = {
        nid: sorted(owners) for nid, owners in nid_owners.items() if len(owners) > 1
    }
    split_groups = [g for g in groups if g.classification == "AMBIGUOUS"]
    warning_groups = [g for g in groups if g.classification == "WARNING"]
    corrupted_rows = [
        r.source_row_number for r in parsed_rows
        if any(i.code == "SUSPECTED_CORRUPTED_CELL" for i in r.issues)
    ]
    mixed_type_cells = sum(
        1 for r in parsed_rows for i in r.issues
        if i.code in ("INVALID_NUMERIC_STORED_AS_NULL", "FRACTIONAL_INT_TRUNCATED")
    )
    near_duplicate_tuples: dict[tuple, int] = {}
    for r in parsed_rows:
        key = (
            str(r.values.get("latitude")), str(r.values.get("longitude")),
            owner_key(r.values.get("owner_name")),
            (r.values.get("structure_name") or "").lower(),
            str(r.values.get("length_ft")), str(r.values.get("width_ft")),
        )
        if key[0] != "None":
            near_duplicate_tuples[key] = near_duplicate_tuples.get(key, 0) + 1
    near_duplicates = {k: c for k, c in near_duplicate_tuples.items() if c > 1}

    return {
        "total_parsed_rows": len(parsed_rows),
        "null_nid_rows": null_nid_rows,
        "distinct_nids": len(nid_owners),
        "multi_owner_nids": multi_owner_nids,
        "multi_owner_nid_count": len(multi_owner_nids),
        "candidate_parcels": len(groups),
        "ambiguous_splits": len(split_groups),
        "split_parcel_codes": [g.parcel_code for g in split_groups],
        "warning_groups": len(warning_groups),
        "corrupted_cell_rows": corrupted_rows,
        "mixed_type_or_numeric_warnings": mixed_type_cells,
        "near_duplicate_tuple_kinds": len(near_duplicates),
        "near_duplicate_extra_rows": sum(c - 1 for c in near_duplicates.values()),
    }
