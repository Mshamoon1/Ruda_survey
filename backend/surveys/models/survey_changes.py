"""survey_changes — APPEND-ONLY revision snapshots.

Each accepted revision stores the COMPLETE survey state at that revision
(`full_payload`) plus a field-level diff (`changes`). A revision can be
reconstructed independently; history is never rewritten.

Immutability:
  * ORM: AppendOnlyModel — inserts only; status/accepted_at may move through
    transition_status(); everything else raises ImmutableRecordError.
  * DB: trigger trg_survey_changes_appendonly (migration 0002) allows UPDATEs
    that touch ONLY status/accepted_at and blocks all DELETEs.
"""
import uuid

from django.conf import settings
from django.db import models
from django.utils import timezone

from django.contrib.postgres.fields import ArrayField

from .immutable import AppendOnlyModel


class RevisionStatus(models.TextChoices):
    DRAFT = "draft", "Draft"
    SUBMITTED = "submitted", "Submitted"
    SYNCED = "synced", "Synced"
    REJECTED = "rejected", "Rejected"


class SurveyChange(AppendOnlyModel):
    client_uuid = models.UUIDField(
        default=uuid.uuid4,
        unique=True,
        help_text="Offline idempotency key generated on the device.",
    )
    parcel = models.ForeignKey(
        "Parcel",
        on_delete=models.PROTECT,
        related_name="revisions",
        db_column="parcel_id",
    )
    revision_no = models.PositiveIntegerField()
    parent_revision = models.ForeignKey(
        "self",
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="child_revisions",
        db_index=True,
        db_column="parent_revision_id",
    )
    base_master_row_ids = ArrayField(
        models.BigIntegerField(),
        default=list,
        help_text="survey_master ids covered by this revision.",
    )

    changes = models.JSONField(
        default=dict,
        help_text='Field-level diff: {"field": {"from": …, "to": …}}.',
    )
    full_payload = models.JSONField(
        default=dict,
        help_text="COMPLETE survey state at this revision (WB2 schema, doc 02 Part E).",
    )

    change_reason = models.CharField(max_length=255, null=True, blank=True)

    changed_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.PROTECT,
        related_name="survey_changes",
        db_column="changed_by_id",
    )
    changed_at = models.DateTimeField(default=timezone.now)
    created_at = models.DateTimeField(auto_now_add=True)
    accepted_at = models.DateTimeField(null=True, blank=True)

    status = models.CharField(
        max_length=16,
        choices=RevisionStatus.choices,
        default=RevisionStatus.DRAFT,
        db_index=True,
    )
    device_info = models.JSONField(null=True, blank=True)

    class Meta:
        db_table = "survey_changes"
        verbose_name = "survey revision (append-only)"
        verbose_name_plural = "survey revisions (append-only)"
        constraints = [
            models.UniqueConstraint(
                fields=["parcel", "revision_no"],
                name="uq_survey_changes_parcel_revision_no",
            ),
            models.CheckConstraint(
                condition=models.Q(revision_no__gt=0),
                name="ck_survey_changes_revision_no_positive",
            ),
            models.CheckConstraint(
                condition=models.Q(status__in=RevisionStatus.values),
                name="ck_survey_changes_status_valid",
            ),
        ]
        indexes = [
            models.Index(fields=["changed_at"], name="idx_changes_changed_at"),
        ]

    def __str__(self):
        return f"{self.parcel_id} rev#{self.revision_no} ({self.status})"


# Ergonomic alias: SurveyChange.Status.<CHOICE>
SurveyChange.Status = RevisionStatus
