"""Import batch tracking (used by the Phase 3 Excel import)."""
from django.conf import settings
from django.db import models


class ImportStatus(models.TextChoices):
    PENDING = "pending", "Pending"
    RUNNING = "running", "Running"
    COMPLETED = "completed", "Completed"
    FAILED = "failed", "Failed"


class ImportBatch(models.Model):
    source_filename = models.CharField(max_length=512)
    file_checksum = models.CharField(
        max_length=64,
        db_index=True,
        help_text="SHA-256 of the imported source workbook (doc 01 pinning).",
    )
    sheet_name = models.CharField(max_length=128, null=True, blank=True)

    total_rows = models.PositiveIntegerField(default=0)
    successful_rows = models.PositiveIntegerField(default=0)
    failed_rows = models.PositiveIntegerField(default=0)
    warning_count = models.PositiveIntegerField(default=0)

    status = models.CharField(
        max_length=16,
        choices=ImportStatus.choices,
        default=ImportStatus.PENDING,
    )
    error_summary = models.JSONField(null=True, blank=True)

    imported_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="import_batches",
    )
    imported_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "import_batches"
        verbose_name = "import batch"
        verbose_name_plural = "import batches"
        constraints = [
            models.CheckConstraint(
                condition=models.Q(status__in=ImportStatus.values),
                name="ck_import_batches_status_valid",
            ),
        ]

    def __str__(self):
        return f"{self.source_filename} ({self.status})"


# Ergonomic alias: ImportBatch.Status.<CHOICE>
ImportBatch.Status = ImportStatus
