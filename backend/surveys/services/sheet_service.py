"""Survey sheet composition + PDF service boundary.

Sheet contract (Phase 4 brief, ENDPOINT 9):
  * `fields` is the UI-oriented collection — keys with NULL values are OMITTED;
  * `original` keeps the complete master baseline INCLUDING nulls
    (internal/audit view);
  * `current_revision_no` / `source` make ORIGINAL vs LATEST explicit.

PDF: Generation implemented in pdf_service.py (Phase 9).
"""
from surveys.services.revision_service import current_state, master_baseline

# WB2 fields shown on the survey sheet, in presentation order.
SHEET_FIELD_ORDER = [
    "rd_value", "package_no", "village", "owner_name", "father_name",
    "cnic_no", "khasra_number", "mauza_number", "contact_number", "land_owner_doc",
    "electricity_connection_name", "land_area", "latitude", "longitude",
    "structure_status", "structure_name", "length_ft", "width_ft",
    "area_sqft", "construction_nature",
]


def build_sheet(parcel) -> dict:
    original_state = master_baseline(parcel)
    current_state_payload, latest = current_state(parcel)

    fields = {
        key: current_state_payload.get(key)
        for key in SHEET_FIELD_ORDER
    }
    fields = {key: value for key, value in fields.items() if value not in (None, "")}

    images = []
    if latest is not None:
        for image in latest.images.all().order_by("image_type", "point_id", "sequence_no"):
            images.append({
                "image_type": image.image_type,
                "checksum_sha256": image.checksum_sha256,
                "file_path": image.file_path,
                "uploaded_at": image.uploaded_at,
                "latitude": image.latitude,
                "longitude": image.longitude,
                "accuracy": image.accuracy,
                "area_name": image.area_name,
                "point_id": image.point_id,
                "sequence_no": image.sequence_no,
            })

    return {
        "parcel": {
            "parcel_code": parcel.parcel_code,
            "source_nid": parcel.source_nid,
            "village": parcel.village,
            "district": parcel.district,
        },
        "source": "revision" if latest else "master",
        "current_revision_no": latest.revision_no if latest else None,
        "surveyor": latest.changed_by.username if latest else None,
        "changed_at": latest.changed_at if latest else None,
        "accepted_at": latest.accepted_at if latest else None,
        "images": images,
        # UI-oriented: NULL-free field collection
        "fields": fields,
        # internal/audit view: complete baseline including NULLs
        "original": original_state,
    }


def generate_pdf(parcel_code: str):
    """Generate PDF for the given parcel code.

    Returns:
        HttpResponse with PDF content.
    """
    from surveys.services.pdf_service import generate_pdf_response
    return generate_pdf_response(parcel_code)
