"""audit_logs — insert-only audit trail.

No UPDATE / DELETE through any normal application path (ORM guard +
database trigger). Users referenced by audit rows are PROTECTed: accounts
are deactivated rather than deleted, so attribution can never be lost.
"""
from django.conf import settings
from django.db import models

from .immutable import ImmutableModel


class AuditLog(ImmutableModel):
    occurred_at = models.DateTimeField(auto_now_add=True, db_index=True)

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="audit_logs",
        db_column="user_id",
        help_text="NULL for system jobs.",
    )
    parcel = models.ForeignKey(
        "Parcel",
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="audit_logs",
        db_column="parcel_id",
    )
    revision = models.ForeignKey(
        "SurveyChange",
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="audit_logs",
        db_column="revision_id",
    )

    action = models.CharField(max_length=48)
    entity_type = models.CharField(max_length=32, null=True, blank=True)
    entity_id = models.BigIntegerField(null=True, blank=True)

    correlation_id = models.UUIDField(null=True, blank=True, db_index=True)
    details = models.JSONField(null=True, blank=True)
    device_info = models.JSONField(null=True, blank=True)
    ip_address = models.GenericIPAddressField(null=True, blank=True)

    class Meta:
        db_table = "audit_logs"
        verbose_name = "audit log entry (append-only)"
        verbose_name_plural = "audit log entries (append-only)"
        indexes = [
            models.Index(fields=["parcel", "occurred_at"], name="idx_audit_parcel_time"),
            models.Index(fields=["user", "occurred_at"], name="idx_audit_user_time"),
            models.Index(fields=["action"], name="idx_audit_action"),
        ]

    def __str__(self):
        return f"{self.action} @ {self.occurred_at:%Y-%m-%d %H:%M:%S}"
