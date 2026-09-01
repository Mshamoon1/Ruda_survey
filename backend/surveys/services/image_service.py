"""Survey evidence image handling (metadata in DB, bytes on disk).

Security rules implemented (Phase 4 brief):
  * JPEG/PNG only — decided by MAGIC BYTES + Pillow decode, never filename;
  * hard size cap (settings.SURVEY_IMAGE_MAX_BYTES);
  * storage path generated server-side: {parcel}/{rev:04d}/{TYPE}_{hash10}.{ext}
    under MEDIA_ROOT; resolved path is asserted to stay inside MEDIA_ROOT
    (path-traversal defence-in-depth);
  * client filename stored only as sanitised `original_filename`;
  * checksum = SHA-256 of the decoded bytes;
  * uniqueness (revision, image_type) -> IMAGE_ALREADY_EXISTS conflict.
"""
import hashlib
from pathlib import Path

from django.conf import settings
from PIL import Image, UnidentifiedImageError

from surveys.api.errors import ApiError, ImageAlreadyExists, InvalidImage
from surveys.models import Parcel, SurveyChange, SurveyImage

MAGIC = {
    b"\xff\xd8\xff": "jpg",
    b"\x89PNG\r\n\x1a\n": "png",
}
ALLOWED_CONTENT_TYPES = {"image/jpeg": "jpg", "image/png": "png"}


def _sniff(head: bytes) -> str | None:
    for magic, ext in MAGIC.items():
        if head.startswith(magic):
            return ext
    return None


def store_image(*, parcel: Parcel, revision: SurveyChange, image_type: str,
                uploaded_file, user=None, latitude=None, longitude=None,
                accuracy=None, area_name=None, point_id=None,
                sequence_no=1) -> SurveyImage:
    # ---- size cap ---------------------------------------------------------
    uploaded_file.seek(0, 2)
    size = uploaded_file.tell()
    if size == 0:
        raise InvalidImage("Empty file.")
    if size > settings.SURVEY_IMAGE_MAX_BYTES:
        raise ApiError(
            f"Image exceeds the {settings.SURVEY_IMAGE_MAX_BYTES // (1024*1024)} MB limit.",
            code="INVALID_IMAGE", status=400)

    uploaded_file.seek(0)
    blob = uploaded_file.read(settings.SURVEY_IMAGE_MAX_BYTES + 1)
    checksum = hashlib.sha256(blob).hexdigest()

    # ---- content sniffing (never trust filename / header alone) ----------
    ext = _sniff(blob[:8])
    if ext is None:
        raise InvalidImage("Only JPEG and PNG images are accepted.")

    try:
        with Image.open(uploaded_file) as img:
            img.verify()
        uploaded_file.seek(0)
        with Image.open(uploaded_file) as img2:
            width_px, height_px = img2.size
            fmt = (img2.format or "").upper()
    except (UnidentifiedImageError, OSError) as exc:
        raise InvalidImage("File is not a decodable image.") from exc
    if fmt not in ("JPEG", "PNG"):
        raise InvalidImage(f"Unsupported image format {fmt}.")

    # ---- duplicate type ---------------------------------------------------
    # FRONT/SECOND: one per revision; POINT_*: unique on (revision, type, point_id, sequence_no)
    if image_type in ("FRONT", "SECOND"):
        if SurveyImage.objects.filter(revision=revision, image_type=image_type).exists():
            raise ImageAlreadyExists(
                details={"image_type": image_type,
                         "revision_no": revision.revision_no})
    else:
        if SurveyImage.objects.filter(
            revision=revision, image_type=image_type,
            point_id=point_id, sequence_no=sequence_no,
        ).exists():
            raise ImageAlreadyExists(
                details={"image_type": image_type, "point_id": point_id,
                         "sequence_no": sequence_no,
                         "revision_no": revision.revision_no})

    # ---- server-generated safe path ---------------------------------------
    relative = (
        f"surveys/{parcel.parcel_code}/{revision.revision_no:04d}/"
        f"{image_type}_{checksum[:10]}.{ext}"
    )
    absolute = (Path(settings.MEDIA_ROOT) / relative).resolve()
    media_root = Path(settings.MEDIA_ROOT).resolve()
    if not str(absolute).startswith(str(media_root)):
        raise ApiError("Resolved storage path escaped media root.",
                       code="INVALID_IMAGE")  # defence-in-depth; unreachable

    absolute.parent.mkdir(parents=True, exist_ok=True)

    # Atomic write: write to temp file, then rename to avoid TOCTOU races
    tmp_path = absolute.with_suffix(absolute.suffix + ".tmp")
    try:
        tmp_path.write_bytes(blob)
        # On Windows os.rename fails if target exists; remove first (Linux replaces atomically)
        if absolute.exists():
            absolute.unlink()
        tmp_path.rename(absolute)
    except OSError:
        if tmp_path.exists():
            tmp_path.unlink()
        raise

    original_name = Path(uploaded_file.name or "").name  # basename only

    try:
        image = SurveyImage.objects.create(
            revision=revision,
            parcel=parcel,
            image_type=image_type,
            file_path=relative,
            original_filename=original_name[:255] or None,
            content_type=f"image/{'jpeg' if ext == 'jpg' else 'png'}",
            file_size=size,
            checksum_sha256=checksum,
            width_px=width_px,
            height_px=height_px,
            captured_at=None,
            uploaded_by=user,
            latitude=latitude,
            longitude=longitude,
            accuracy=accuracy,
            area_name=area_name,
            point_id=point_id,
            sequence_no=sequence_no,
        )
    except Exception:
        # Clean up orphan file if DB create fails
        if absolute.exists():
            absolute.unlink()
        raise

    from surveys.services import audit as audit_service

    audit_service.record(
        action="IMAGE_UPLOADED",
        user=user,
        parcel=parcel,
        revision=revision,
        entity_type="image",
        entity_id=image.pk,
        details={"image_type": image_type, "checksum_sha256": checksum,
                 "file_path": relative},
    )
    return image


def get_revision_or_404(parcel_code: str, revision_no) -> tuple[Parcel, SurveyChange]:
    from surveys.api.errors import RevisionNotFound

    parcel = Parcel.objects.filter(parcel_code=parcel_code.strip()).first()
    if parcel is None:
        raise ApiError("Parcel was not found.", code="PARCEL_NOT_FOUND", status=404)
    try:
        rev_no = int(revision_no)
    except (TypeError, ValueError):
        raise RevisionNotFound()
    revision = parcel.revisions.filter(revision_no=rev_no).first()
    if revision is None:
        raise RevisionNotFound(details={"revision_no": rev_no})
    return parcel, revision
