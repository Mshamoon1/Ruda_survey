"""Import the master Annex 4.1 workbook into survey_master + parcels.

    python manage.py import_survey <path-to-xlsx> [--dry-run] [--report-out FILE]

The source workbook is opened READ-ONLY and its SHA-256 is recorded.
"""
import json
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError

from surveys.services.excel_import import ExcelImportService
from surveys.services.excel_reader import sha256_file


class Command(BaseCommand):
    help = "Import the immutable master survey workbook (Annex 4.1)."

    def add_arguments(self, parser):
        parser.add_argument("workbook", type=str)
        parser.add_argument("--dry-run", action="store_true",
                            help="Validate + derive parcels without writing anything.")
        parser.add_argument("--report-out", type=str, default=None,
                            help="Optional path for a JSON summary.")

    def handle(self, *args, **options):
        path = Path(options["workbook"])
        if not path.exists():
            raise CommandError(f"workbook not found: {path}")
        dry_run = options["dry_run"]

        self.stdout.write(f"source : {path}")
        self.stdout.write(f"sha256 : {sha256_file(path)}")
        self.stdout.write(f"mode   : {'DRY RUN' if dry_run else 'REAL IMPORT'}")

        result = ExcelImportService(path).run(dry_run=dry_run)

        summary = result.summary()
        for key, value in summary.items():
            self.stdout.write(self.style.SUCCESS(f"  {key:26}: {value}"))

        if result.quality:
            q = result.quality
            self.stdout.write("  --- data quality ---")
            self.stdout.write(f"  null_nid_rows            : {q['null_nid_rows']}")
            self.stdout.write(f"  multi_owner_nids         : {q['multi_owner_nid_count']}")
            self.stdout.write(f"  ambiguous splits         : {q['ambiguous_splits']}")
            self.stdout.write(f"  corrupted_cell_rows      : {len(q['corrupted_cell_rows'])}")
            self.stdout.write(f"  near_duplicate_kinds     : {q['near_duplicate_tuple_kinds']}")

        if options["report_out"]:
            Path(options["report_out"]).write_text(
                json.dumps({"summary": summary, "quality": result.quality,
                            "errors": result.errors[:500],
                            "warnings": result.warnings[:1000]}, indent=2),
                encoding="utf-8",
            )
            self.stdout.write(f"report written to {options['report_out']}")

        if result.status in ("FAILED",):
            raise CommandError(f"import failed with {len(result.errors)} errors")
