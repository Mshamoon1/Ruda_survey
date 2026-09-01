"""TEST GROUP G — survey_images: FRONT/SECOND types, revision link, checksum,
uniqueness per (revision, image_type), idempotency key."""
import uuid

from django.core.exceptions import ValidationError
from django.db import IntegrityError, connection, transaction
from django.test import TestCase

from surveys.models import SurveyImage
from surveys.tests import base


class SurveyImageTests(TestCase):
    def setUp(self):
        self.user = base.make_user()
        self.parcel = base.make_parcel()
        self.rev = base.make_revision(self.parcel, self.user)

    def test_g1_front_and_second_accepted(self):
        front = base.make_image(self.rev, SurveyImage.ImageType.FRONT)
        second = base.make_image(self.rev, SurveyImage.ImageType.SECOND,
                                 file_path="surveys/x/0001/SECOND_abc.jpg")
        self.assertEqual(front.image_type, "FRONT")
        self.assertEqual(second.image_type, "SECOND")
        self.assertEqual(self.rev.images.count(), 2)

    def test_g2_invalid_type_rejected_by_validation_and_db_check(self):
        # Model-level: full_clean must reject a non-enum type before any SQL.
        img = SurveyImage(
            revision=self.rev,
            parcel=self.parcel,
            image_type="INVALID_TYPE",
            file_path="surveys/x/0001/INVALID_TYPE_bad.jpg",
            checksum_sha256="a" * 64,
        )
        with self.assertRaises(ValidationError):
            img.full_clean()

        # Database-level: CHECK constraint ck_survey_images_type_valid.
        with self.assertRaises(IntegrityError), transaction.atomic():
            base.make_image(self.rev, image_type="SIDESHOW")

    def test_g3_image_belongs_to_specific_revision_and_parcel(self):
        img = base.make_image(self.rev)
        rev2 = base.make_revision(self.parcel, self.user, revision_no=2)
        self.assertEqual(img.revision_id, self.rev.pk)
        self.assertEqual(img.parcel_id, self.parcel.pk)
        # Image of rev1 is not an image of rev2:
        self.assertFalse(rev2.images.exists())

    def test_g4_duplicate_type_for_same_revision_rejected(self):
        base.make_image(self.rev, SurveyImage.ImageType.FRONT)
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_image(self.rev, SurveyImage.ImageType.FRONT,
                                client_uuid=uuid.uuid4())

    def test_g5_checksum_required_stored_and_indexed(self):
        img = base.make_image(self.rev)
        self.assertEqual(len(img.checksum_sha256), 64)
        img.refresh_from_db()
        self.assertEqual(len(img.checksum_sha256), 64)
        with connection.cursor() as cursor:
            cons = connection.introspection.get_constraints(cursor, SurveyImage._meta.db_table)
        checksum_indexed = any(
            meta.get("columns") == ["checksum_sha256"] and meta["index"]
            for meta in cons.values()
        )
        self.assertTrue(checksum_indexed)

    def test_g6_client_uuid_unique_idempotency_key(self):
        shared = uuid.uuid4()
        base.make_image(self.rev, client_uuid=shared)
        rev2 = base.make_revision(self.parcel, self.user, revision_no=2)
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_image(rev2, client_uuid=shared)

    def test_g7_captured_at_preserved_uploaded_at_set(self):
        from django.utils import timezone

        captured = timezone.now() - timezone.timedelta(hours=5)
        img = base.make_image(self.rev, captured_at=captured)
        img.refresh_from_db()
        self.assertEqual(img.captured_at, captured)
        self.assertIsNotNone(img.uploaded_at)

    def test_g8_file_size_negative_rejected(self):
        with self.assertRaises(IntegrityError), transaction.atomic():
            
                base.make_image(self.rev, file_size=-1)
