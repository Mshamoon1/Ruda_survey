"""survey_master — the IMMUTABLE copy of the original/master survey data.

One row per original Excel line-item (doc 02 Part D). After import:
  * no UPDATE (application or database path)
  * no DELETE
Source sentinel values that genuinely exist in the workbook ("-", "0",
"Not Identified") are stored verbatim; only truly empty cells become NULL.
`raw_data` keeps every original cell verbatim for provenance/audit.
"""
from django.conf import settings
from django.db import models

from .immutable import ImmutableModel


class SurveyMaster(ImmutableModel):
    # --- provenance -------------------------------------------------------
    import_batch = models.ForeignKey(
        "ImportBatch",
        on_delete=models.PROTECT,
        related_name="master_rows",
        db_column="import_batch_id",
    )
    source_row_number = models.PositiveIntegerField(
        help_text="Physical Excel row number this record was imported from.",
    )
    sr_no = models.PositiveIntegerField(
        help_text="Excel column A 'Sr. No' counter (verbatim).",
    )
    parcel = models.ForeignKey(
        "Parcel",
        on_delete=models.PROTECT,
        related_name="master_lines",
        db_column="parcel_id",
    )
    source_nid = models.BigIntegerField(
        null=True,
        blank=True,
        db_index=True,
        help_text="Excel hidden column B 'NID' (raw, never normalized destructively).",
    )

    # --- mapped data fields (doc 02 Part D) -------------------------------
    chainage_m = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)          # C
    affected_persons_count = models.IntegerField(null=True, blank=True)                               # D
    phase_code = models.CharField(max_length=16, null=True, blank=True)                               # E
    latitude = models.DecimalField(max_digits=10, decimal_places=7, null=True, blank=True)            # F
    longitude = models.DecimalField(max_digits=10, decimal_places=7, null=True, blank=True)           # G
    project_component = models.CharField(max_length=64)                                               # H NOT NULL
    owner_name = models.CharField(max_length=255)                                                     # I NOT NULL
    father_name = models.CharField(max_length=255, null=True, blank=True)                             # J
    caste = models.CharField(max_length=96, null=True, blank=True)                                    # K
    village = models.CharField(max_length=128)                                                        # L NOT NULL
    tehsil = models.CharField(max_length=96, null=True, blank=True)                                   # M
    district = models.CharField(max_length=96, null=True, blank=True)                                 # N
    ownership_documents = models.CharField(max_length=255, null=True, blank=True)                     # O
    structure_status = models.CharField(max_length=64)                                                # P NOT NULL
    structure_name = models.CharField(max_length=255, null=True, blank=True)                          # Q
    structure_count = models.IntegerField(null=True, blank=True)                                      # R
    tenure_status = models.CharField(max_length=16, null=True, blank=True)                            # S
    length_ft = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)           # T
    width_ft = models.DecimalField(max_digits=10, decimal_places=2, null=True, blank=True)            # U
    area_value = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)          # V
    construction_nature = models.CharField(max_length=32, null=True, blank=True)                      # W
    unit_rate_rs = models.IntegerField(null=True, blank=True)                                         # X
    compensation_million = models.DecimalField(max_digits=14, decimal_places=6, null=True, blank=True)# Y
    impact_extent = models.CharField(max_length=32)                                                   # Z NOT NULL
    river_location = models.CharField(max_length=16, null=True, blank=True)                           # AA
    in_row_yn = models.CharField(max_length=4, null=True, blank=True)                                 # AB
    cl_offset_m = models.IntegerField(null=True, blank=True)                                          # AC
    extra_note = models.CharField(max_length=64, null=True, blank=True)                               # AD stray

    # --- field survey additions (not in source Excel) ------------------------
    khasra_number = models.CharField(max_length=64, null=True, blank=True,
                                     help_text="Khasra/plot number assigned during field survey.")

    # --- import provenance flags / raw snapshot ---------------------------
    is_formula_area = models.BooleanField(default=False)
    is_formula_compensation = models.BooleanField(default=False)
    raw_data = models.JSONField(help_text="All original cells verbatim (column letter -> value).")
    imported_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = "survey_master"
        verbose_name = "survey master record (immutable)"
        verbose_name_plural = "survey master records (immutable)"
        constraints = [
            models.UniqueConstraint(
                fields=["import_batch", "source_row_number"],
                name="uq_survey_master_batch_source_row",
            ),
            models.CheckConstraint(condition=models.Q(sr_no__gt=0), name="ck_master_sr_no_positive"),
            models.CheckConstraint(
                condition=models.Q(source_row_number__gt=0),
                name="ck_master_source_row_positive",
            ),
        ]
        indexes = [
            models.Index(fields=["village"], name="idx_master_village"),
            models.Index(fields=["structure_status"], name="idx_master_struct_status"),
        ]

    def __str__(self):
        return f"#{self.sr_no} {self.owner_name} @ {self.village}"
