"""Phase 10D — GPS-stamped image fields on SurveyImage.

Adds latitude, longitude, accuracy, area_name, point_id, sequence_no,
original_image_path, and qr_payload.  Replaces the single-type unique
constraint with a conditional constraint that also covers point+sequence.
"""
import django.core.validators
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("surveys", "0005_add_owner_name_index"),
    ]

    operations = [
        # ── new GPS / metadata fields ──────────────────────────────────
        migrations.AddField(
            model_name="surveyimage",
            name="latitude",
            field=models.DecimalField(
                max_digits=10, decimal_places=7, null=True, blank=True,
                validators=[
                    django.core.validators.MinValueValidator(-90),
                    django.core.validators.MaxValueValidator(90),
                ],
                help_text="GPS latitude of image capture point.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="longitude",
            field=models.DecimalField(
                max_digits=10, decimal_places=7, null=True, blank=True,
                validators=[
                    django.core.validators.MinValueValidator(-180),
                    django.core.validators.MaxValueValidator(180),
                ],
                help_text="GPS longitude of image capture point.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="accuracy",
            field=models.DecimalField(
                max_digits=8, decimal_places=2, null=True, blank=True,
                help_text="GPS accuracy in metres.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="area_name",
            field=models.CharField(
                max_length=255, null=True, blank=True,
                help_text="Human-readable area/zone name for this image.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="point_id",
            field=models.CharField(
                max_length=32, null=True, blank=True,
                help_text="Point identifier for POINT_N images.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="sequence_no",
            field=models.PositiveIntegerField(
                default=1,
                help_text="Sequence within point for multiple images at the same location.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="original_image_path",
            field=models.TextField(
                null=True, blank=True,
                help_text="Path to original unstamped image before overlay.",
            ),
        ),
        migrations.AddField(
            model_name="surveyimage",
            name="qr_payload",
            field=models.TextField(
                null=True, blank=True,
                help_text="JSON payload embedded in QR stamp overlay.",
            ),
        ),
        # ── unique constraint for point+sequence ───────────────────────
        migrations.AddConstraint(
            model_name="surveyimage",
            constraint=models.UniqueConstraint(
                fields=["revision", "image_type", "point_id", "sequence_no"],
                name="uq_survey_images_revision_type_point_seq",
            ),
        ),
    ]
