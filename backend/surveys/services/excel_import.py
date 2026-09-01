"""Excel import orchestrator (parse -> derive -> atomically persist).

Guarantees:
  * idempotent: same SHA-256 twice -> ALREADY_IMPORTED / DUPLICATE result,
    zero new rows; DB unique (batch,row) is the backstop.
  * atomic: all parcels + master rows are written inside ONE transaction;
    any failure rolls everything back and marks the batch FAILED.
  * immutable-friendly: only INSERT paths are used (bulk_create).
  * dry_run: full parse + derivation + validation with ZERO database writes
    (not even an import_batches row).
"""
import time
from dataclasses import dataclass, field as dc_field
from pathlib import Path

from django.db import transaction

from surveys.models import ImportBatch, Parcel, SurveyMaster

from .excel_reader import (
    SourceValidationError,
    parse_master_workbook,
    sha256_file,
)
from .parcel_derivation import derive_parcel_groups, quality_summary


@dataclass
class ImportResult:
    status: str                       # COMPLETED | FAILED | DUPLICATE | DRY_RUN_OK | DRY_RUN_ERRORS
    source_path: str
    sha256: str
    sheet: str | None = None
    dry_run: bool = False
    already_imported: bool = False
    batch_id: int | None = None
    total_source_rows: int = 0
    valid_rows: int = 0
    rejected_rows: int = 0
    derived_parcels: int = 0
    new_parcels: int = 0
    existing_parcels_before: int = 0
    imported_master_records: int = 0
    errors: list = dc_field(default_factory=list)
    warnings: list = dc_field(default_factory=list)
    quality: dict = dc_field(default_factory=dict)
    duration_ms: int = 0

    def summary(self) -> dict:
        return {
            "status": self.status,
            "source_file": Path(self.source_path).name,
            "sha256": self.sha256,
            "sheet": self.sheet,
            "dry_run": self.dry_run,
            "already_imported": self.already_imported,
            "import_batch_id": self.batch_id,
            "total_source_rows": self.total_source_rows,
            "valid_rows": self.valid_rows,
            "rejected_rows": self.rejected_rows,
            "derived_parcels": self.derived_parcels,
            "existing_parcels_before": self.existing_parcels_before,
            "new_parcels": self.new_parcels,
            "imported_master_records": self.imported_master_records,
            "error_count": len(self.errors),
            "warning_count": len(self.warnings),
            "duration_ms": self.duration_ms,
        }


def _row_issues_as_dicts(parsed_rows):
    out = []
    for row in parsed_rows:
        for issue in row.issues:
            if issue.severity in ("WARNING", "ERROR"):
                entry = issue.as_dict()
                out.append(entry)
    return out


class ExcelImportService:
    """Reusable service; also driven by the ``import_survey`` command."""

    # Overlap ratio above which a *different* file (different SHA-256) is
    # considered a re-export of already-imported rows and refused. Prevents
    # silent double-import when the workbook is re-saved by Excel.
    OVERLAP_REFUSAL_RATIO = 0.5

    def __init__(self, master_path: str | Path, *, allow_overlap: bool = False):
        self.master_path = Path(master_path)
        self.allow_overlap = allow_overlap

    # ------------------------------------------------------------------ #
    def run(self, *, dry_run: bool = False, imported_by=None) -> ImportResult:
        started = time.monotonic()
        result = ImportResult(
            status="FAILED", source_path=str(self.master_path),
            sha256=sha256_file(self.master_path), dry_run=dry_run,
        )

        # ---- parse + structural validation (no DB access at all) -------
        try:
            parsed_rows = parse_master_workbook(self.master_path)
        except SourceValidationError as exc:
            result.errors = [i.as_dict() for i in exc.issues]
            if not dry_run:
                batch = ImportBatch.objects.create(
                    source_filename=self.master_path.name,
                    file_checksum=result.sha256,
                    status=ImportBatch.Status.FAILED,
                    error_summary={"errors": result.errors},
                    imported_by=imported_by,
                )
                result.batch_id = batch.pk
            result.duration_ms = int((time.monotonic() - started) * 1000)
            return result

        result.total_source_rows = len(parsed_rows)
        result.valid_rows = sum(1 for r in parsed_rows if not r.rejected)
        result.rejected_rows = result.total_source_rows - result.valid_rows
        result.warnings = [i.as_dict() for r in parsed_rows for i in r.issues
                           if i.severity == "WARNING"]
        result.errors = [i.as_dict() for r in parsed_rows for i in r.issues
                         if i.severity == "ERROR"]

        # ---- parcel derivation -----------------------------------------
        groups = derive_parcel_groups(parsed_rows)
        importable_groups = [
            g for g in groups
            if any(not r.rejected for r in g.rows)
        ]
        result.derived_parcels = len(groups)
        result.quality = quality_summary(groups, parsed_rows)

        # ---- dry run stops here ----------------------------------------
        if dry_run:
            result.status = ("DRY_RUN_OK" if result.rejected_rows == 0
                             else "DRY_RUN_ERRORS")
            result.duration_ms = int((time.monotonic() - started) * 1000)
            return result

        # ---- idempotency guard (checksum) ------------------------------
        if SurveyMaster.objects.filter(
                import_batch__file_checksum=result.sha256).exists():
            result.status = "DUPLICATE"
            result.already_imported = True
            result.imported_master_records = SurveyMaster.objects.filter(
                import_batch__file_checksum=result.sha256).count()
            result.new_parcels = 0
            result.duration_ms = int((time.monotonic() - started) * 1000)
            return result

        # ---- overlap guard: same ROWS arriving under a different hash ---
        if not self.allow_overlap:
            new_sr_nos = {r.sr_no for r in parsed_rows if r.sr_no is not None}
            for prior in (ImportBatch.objects.filter(
                    status=ImportBatch.Status.COMPLETED)
                    .exclude(file_checksum=result.sha256)):
                prior_rows = prior.master_rows.count()
                if prior_rows == 0:
                    continue
                overlap = SurveyMaster.objects.filter(
                    import_batch=prior, sr_no__in=new_sr_nos).count()
                ratio = overlap / min(len(new_sr_nos), prior_rows)
                if ratio >= self.OVERLAP_REFUSAL_RATIO:
                    result.status = "FAILED"
                    result.errors.append({
                        "severity": "ERROR", "code": "OVERLAPPING_SOURCE",
                        "message": (
                            f"{int(ratio * 100)}% of these rows already exist in "
                            f"completed batch #{prior.pk} (same content under a "
                            f"different file). Re-run with allow_overlap=True "
                            f"only if this is an intentional corrected re-import."
                        ),
                        "prior_batch_id": prior.pk,
                        "overlap_ratio": round(ratio, 4),
                    })
                    result.duration_ms = int((time.monotonic() - started) * 1000)
                    return result

        result.existing_parcels_before = Parcel.objects.count()

        # ---- atomic persistence ----------------------------------------
        batch = ImportBatch.objects.create(
            source_filename=self.master_path.name,
            file_checksum=result.sha256,
            sheet_name=_sheet_name(self.master_path),
            total_rows=result.total_source_rows,
            status=ImportBatch.Status.RUNNING,
            imported_by=imported_by,
        )
        result.batch_id = batch.pk

        try:
            with transaction.atomic():
                parcels_to_create = []
                for g in importable_groups:
                    header_row = next((r for r in g.rows if not r.rejected), g.rows[0])
                    parcels_to_create.append(Parcel(
                        parcel_code=g.parcel_code,
                        source_nid=g.source_nid,
                        village=header_row.values.get("village"),
                        tehsil=header_row.values.get("tehsil"),
                        district=header_row.values.get("district"),
                        owner_name_current=g.owner_name_current,
                    ))
                # Canonical parcels are reused when a documented
                # allow_overlap re-import arrives: codes are anchored to
                # source rows, so identical groups MUST map to the same
                # parcel records. Only genuinely new codes get created.
                wanted_codes = [p.parcel_code for p in parcels_to_create]
                existing_map = dict(Parcel.objects.filter(
                    parcel_code__in=wanted_codes,
                ).values_list("parcel_code", "id"))

                to_insert = [p for p in parcels_to_create
                             if p.parcel_code not in existing_map]
                if to_insert:
                    Parcel.objects.bulk_create(to_insert, batch_size=1000)
                    created_map = dict(Parcel.objects.filter(
                        parcel_code__in=[p.parcel_code for p in to_insert],
                    ).values_list("parcel_code", "id"))
                else:
                    created_map = {}
                result.new_parcels = len(to_insert)

                code_to_parcel = {**existing_map, **created_map}

                masters = []
                for g in importable_groups:
                    parcel_id = code_to_parcel[g.parcel_code]
                    for row in g.rows:
                        if row.rejected:
                            continue
                        masters.append(SurveyMaster(
                            import_batch_id=batch.pk,
                            source_row_number=row.source_row_number,
                            sr_no=row.sr_no,
                            parcel_id=parcel_id,
                            source_nid=row.source_nid,
                            raw_data=row.raw_data,
                            is_formula_area=row.is_formula_area,
                            is_formula_compensation=row.is_formula_compensation,
                            **row.values,
                        ))
                SurveyMaster.objects.bulk_create(masters, batch_size=500)
                result.imported_master_records = len(masters)

                failed_rows = result.rejected_rows
                ImportBatch.objects.filter(pk=batch.pk).update(
                    successful_rows=result.imported_master_records,
                    failed_rows=failed_rows,
                    warning_count=len(result.warnings),
                    status=ImportBatch.Status.COMPLETED,
                    error_summary={
                        "errors": result.errors[:200],
                        "warnings": result.warnings[:500],
                        "quality": result.quality,
                        "truncated": {
                            "errors": max(0, len(result.errors) - 200),
                            "warnings": max(0, len(result.warnings) - 500),
                        },
                    },
                )
        except Exception as exc:  # rollback happened; record the failure
            ImportBatch.objects.filter(pk=batch.pk).update(
                status=ImportBatch.Status.FAILED,
                error_summary={"fatal": str(exc)[:2000]},
            )
            raise

        result.status = "COMPLETED"
        result.duration_ms = int((time.monotonic() - started) * 1000)
        return result


def _sheet_name(path: Path) -> str | None:
    import openpyxl

    wb = openpyxl.load_workbook(path, read_only=True)
    try:
        return wb.sheetnames[0]
    finally:
        wb.close()
