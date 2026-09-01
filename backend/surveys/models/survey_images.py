"""survey_images — metadata only; binaries live on disk/object storage.

Exactly one FRONT/SECOND image per revision. POINT_* images allow multiples
identified by point_id + sequence_no. GPS stamping fields added in Phase 10D.
"""
import uuid

from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models


class ImageType(models.TextChoices):
    FRONT = "FRONT", "Front"
    SECOND = "SECOND", "Second/Survey"
    POINT_1 = "POINT_1", "Point Image 1"
    POINT_2 = "POINT_2", "Point Image 2"
    POINT_3 = "POINT_3", "Point Image 3"
    POINT_4 = "POINT_4", "Point Image 4"


class SyncStatus(models.TextChoices):
    PENDING = "pending", "Pending"
    SYNCED = "synced", "Synced"


class SurveyImage(models.Model):
    revision = models.ForeignKey(
        "SurveyChange",
        on_delete=models.PROTECT,
        related_name="images",
        db_column="revision_id",
    )
    parcel = models.ForeignKey(
        "Parcel",
        on_delete=models.PROTECT,
        related_name="images",
        db_column="parcel_id",
        help_text="Denormalized from revision for direct parcel queries.",
    )

    image_type = models.CharField(max_length=12, choices=ImageType.choices)
    file_path = models.TextField(help_text="Storage-relative path (never a DB blob).")
    original_filename = models.CharField(max_length=255, null=True, blank=True)
    content_type = models.CharField(max_length=64, null=True, blank=True)
    file_size = models.BigIntegerField(null=True, blank=True)
    checksum_sha256 = models.CharField(max_length=64, db_index=True)
    width_px = models.PositiveIntegerField(null=True, blank=True)
    height_px = models.PositiveIntegerField(null=True, blank=True)

    captured_at = models.DateTimeField(null=True, blank=True)
    uploaded_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.PROTECT,
        related_name="uploaded_images",
        db_column="uploaded_by_id",
    )
    uploaded_at = models.DateTimeField(auto_now_add=True)

    client_uuid = models.UUIDField(default=uuid.uuid4, unique=True)
    sync_status = models.CharField(
        max_length=12,
        choices=SyncStatus.choices,
        default=SyncStatus.PENDING,
    )

    # GPS stamping fields (Phase 10D)
    latitude = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True,
        validators=[MinValueValidator(-90), MaxValueValidator(90)],
        help_text="GPS latitude of image capture point.",
    )
    longitude = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True,
        validators=[MinValueValidator(-180), MaxValueValidator(180)],
        help_text="GPS longitude of image capture point.",
    )
    accuracy = models.DecimalField(
        max_digits=8, decimal_places=2, null=True, blank=True,
        help_text="GPS accuracy in metres.",
    )
    area_name = models.CharField(
        max_length=255, null=True, blank=True,
        help_text="Human-readable area/zone name for this image.",
    )
    point_id = models.CharField(
        max_length=32, null=True, blank=True,
        help_text="Point identifier for POINT_N images.",
    )
    sequence_no = models.PositiveIntegerField(
        default=1,
        help_text="Sequence within point for multiple images at the same location.",
    )
    original_image_path = models.TextField(
        null=True, blank=True,
        help_text="Path to original unstamped image before overlay.",
    )
    qr_payload = models.TextField(
        null=True, blank=True,
        help_text="JSON payload embedded in QR stamp overlay.",
    )

    class Meta:
        db_table = "survey_images"
        verbose_name = "survey image"
        verbose_name_plural = "survey images"
        constraints = [
            models.UniqueConstraint(
                fields=["revision", "image_type"],
                name="uq_survey_images_revision_type",
            ),
            models.UniqueConstraint(
                fields=["revision", "image_type", "point_id", "sequence_no"],
                name="uq_survey_images_revision_type_point_seq",
            ),
            models.CheckConstraint(
                condition=models.Q(image_type__in=ImageType.values),
                name="ck_survey_images_type_valid",
            ),
            models.CheckConstraint(
                condition=models.Q(file_size__isnull=True) | models.Q(file_size__gte=0),
                name="ck_survey_images_size_nonnegative",
            ),
            models.CheckConstraint(
                condition=models.Q(sync_status__in=SyncStatus.values),
                name="ck_survey_images_sync_status_valid",
            ),
        ]

    def __str__(self):
        return f"{self.image_type} for rev#{self.revision_id}"


# Ergonomic aliases: SurveyImage.ImageType / .SyncStatus
SurveyImage.ImageType = ImageType
SurveyImage.SyncStatus = SyncStatus
