"""
Django Admin registration — READ-ONLY inspection only for every RUDA core
table. Immutable models can never be added/changed/deleted through the
admin UI; this is an application-layer rule on top of the ORM guards and
database triggers.
"""
from django.contrib import admin

from .models import AuditLog, ImportBatch, Parcel, SurveyChange, SurveyImage, SurveyMaster


class ReadOnlyAdmin(admin.ModelAdmin):
    """Disallow all mutations; allow viewing/searching only."""

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(Parcel)
class ParcelAdmin(ReadOnlyAdmin):
    list_display = ("parcel_code", "source_nid", "village", "district", "owner_name_current")
    search_fields = ("parcel_code", "source_nid", "village", "owner_name_current")
    list_filter = ("district", "tehsil")


@admin.register(SurveyMaster)
class SurveyMasterAdmin(ReadOnlyAdmin):
    list_display = (
        "sr_no",
        "parcel",
        "source_nid",
        "owner_name",
        "village",
        "structure_status",
        "compensation_million",
        "import_batch",
    )
    search_fields = ("sr_no", "owner_name", "village", "structure_name")
    list_filter = ("project_component", "phase_code")


@admin.register(SurveyChange)
class SurveyChangeAdmin(ReadOnlyAdmin):
    list_display = ("id", "parcel", "revision_no", "status", "changed_by", "created_at")
    search_fields = ("client_uuid",)
    list_filter = ("status",)


@admin.register(SurveyImage)
class SurveyImageAdmin(ReadOnlyAdmin):
    list_display = ("id", "revision", "image_type", "checksum_sha256", "uploaded_at")
    list_filter = ("image_type", "sync_status")


@admin.register(AuditLog)
class AuditLogAdmin(ReadOnlyAdmin):
    list_display = ("action", "user", "parcel", "occurred_at")
    list_filter = ("action",)
    search_fields = ("correlation_id",)


@admin.register(ImportBatch)
class ImportBatchAdmin(ReadOnlyAdmin):
    list_display = (
        "id",
        "source_filename",
        "file_checksum",
        "total_rows",
        "successful_rows",
        "failed_rows",
        "status",
        "imported_at",
    )
    list_filter = ("status",)
