"""Canonical application-level survey parcel.

The master Excel has no literal Parcel ID column (doc 02 §C). The canonical
parcel identifier is derived by the Phase 3 import/parcel-derivation service
using the documented strategy (doc 02 §C3); this table is therefore populated
by import code, never hand-invented. `source_nid` preserves the original
hidden Excel column B value for provenance and is deliberately NOT unique
(NID 606 spans multiple owner groups and may be split into several parcels).
"""
from django.db import models


class Parcel(models.Model):
    # Canonical Parcel ID string, e.g. "RUDA-P14-00001" (doc 02 §C4).
    parcel_code = models.CharField(
        max_length=32,
        unique=True,
        help_text="Canonical Parcel ID used by the application for survey lookup.",
    )
    source_nid = models.BigIntegerField(
        null=True,
        blank=True,
        db_index=True,
        help_text="Original hidden 'NID' Excel column B value. Not unique by design.",
    )
    # Denormalized header fields copied from the first master line of the
    # parcel during import (doc 03 §2.0); refreshable by import only.
    village = models.CharField(max_length=128, null=True, blank=True)
    tehsil = models.CharField(max_length=96, null=True, blank=True)
    district = models.CharField(max_length=96, null=True, blank=True)
    owner_name_current = models.CharField(max_length=255, null=True, blank=True)

    # Business search keys (denormalized from latest revision for efficient search).
    khasra_number = models.CharField(max_length=64, null=True, blank=True)
    mauza_number = models.CharField(max_length=64, null=True, blank=True)

    # Convenience pointer to the latest revision; kept in sync by the
    # revision service (Phase 5), not by manual editing.
    current_revision = models.ForeignKey(
        "SurveyChange",
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="+",
        db_column="current_revision_id",
    )

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = "parcels"
        verbose_name = "parcel"
        verbose_name_plural = "parcels"
        constraints = [
            models.CheckConstraint(
                condition=~models.Q(parcel_code=""),
                name="ck_parcels_code_nonempty",
            ),
        ]
        indexes = [
            models.Index(fields=["owner_name_current"], name="idx_parcels_owner_name"),
        ]

    def __str__(self):
        return self.parcel_code
