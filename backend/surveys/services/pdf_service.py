"""PDF generation service for survey sheets.

Produces a professional A4 PDF with:
  - RUDA header with parcel identification
  - Survey fields in formatted table
  - Image references and checksums
  - Revision metadata and audit trail
  - QR code for parcel verification
"""
import io
import json
from pathlib import Path

from django.conf import settings
from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm, mm
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    Image,
    PageBreak,
    PageTemplate,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from surveys.api.errors import ApiError
from surveys.models import Parcel, SurveyImage
from surveys.services.revision_service import current_state, master_baseline
from surveys.services.sheet_service import SHEET_FIELD_ORDER, build_sheet

# Field display labels (human-readable)
FIELD_LABELS = {
    "rd_value": "RD Value",
    "package_no": "Package No",
    "village": "Village",
    "owner_name": "Owner Name",
    "father_name": "Father Name",
    "cnic_no": "CNIC No",
    "khasra_number": "Khasra Number",
    "mauza_number": "Mauza Number",
    "contact_number": "Contact Number",
    "land_owner_doc": "Land Owner Document",
    "electricity_connection_name": "Electricity Connection",
    "land_area": "Land Area",
    "latitude": "Latitude",
    "longitude": "Longitude",
    "structure_status": "Structure Status",
    "structure_name": "Structure Name",
    "length_ft": "Length (ft)",
    "width_ft": "Width (ft)",
    "area_sqft": "Area (sqft)",
    "construction_nature": "Construction Nature",
}

# Section grouping for organized display
FIELD_SECTIONS = {
    "Location": ["rd_value", "package_no", "village", "mauza_number", "latitude", "longitude"],
    "Ownership": ["owner_name", "father_name", "cnic_no", "land_owner_doc"],
    "Contact": ["contact_number", "electricity_connection_name"],
    "Land Details": ["khasra_number", "land_area", "length_ft", "width_ft", "area_sqft"],
    "Structures": ["structure_status", "structure_name", "construction_nature"],
}


def _build_styles():
    """Create custom paragraph styles for the PDF."""
    styles = getSampleStyleSheet()

    styles.add(ParagraphStyle(
        name="RudaHeader",
        parent=styles["Heading1"],
        fontSize=18,
        textColor=colors.HexColor("#1a5276"),
        spaceAfter=6,
        alignment=TA_CENTER,
    ))

    styles.add(ParagraphStyle(
        name="RudaSubHeader",
        parent=styles["Heading2"],
        fontSize=12,
        textColor=colors.HexColor("#2c3e50"),
        spaceAfter=4,
        alignment=TA_CENTER,
    ))

    styles.add(ParagraphStyle(
        name="SectionTitle",
        parent=styles["Heading3"],
        fontSize=11,
        textColor=colors.HexColor("#1a5276"),
        spaceBefore=12,
        spaceAfter=6,
        borderWidth=1,
        borderColor=colors.HexColor("#1a5276"),
        borderPadding=4,
    ))

    styles.add(ParagraphStyle(
        name="FieldLabel",
        parent=styles["Normal"],
        fontSize=9,
        textColor=colors.HexColor("#7f8c8d"),
        leading=12,
    ))

    styles.add(ParagraphStyle(
        name="FieldValue",
        parent=styles["Normal"],
        fontSize=10,
        textColor=colors.HexColor("#2c3e50"),
        leading=13,
    ))

    styles.add(ParagraphStyle(
        name="Footer",
        parent=styles["Normal"],
        fontSize=8,
        textColor=colors.HexColor("#95a5a6"),
        alignment=TA_CENTER,
    ))

    styles.add(ParagraphStyle(
        name="RevisionInfo",
        parent=styles["Normal"],
        fontSize=9,
        textColor=colors.HexColor("#34495e"),
        leading=12,
    ))

    return styles


def _add_page_header(canvas, doc):
    """Draw header on each page."""
    canvas.saveState()
    canvas.setFont("Helvetica-Bold", 14)
    canvas.setFillColor(colors.HexColor("#1a5276"))
    canvas.drawCentredString(A4[0] / 2, A4[1] - 1.5 * cm, "RUDA SURVEY SHEET")

    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#7f8c8d"))
    canvas.drawCentredString(A4[0] / 2, A4[1] - 2 * cm,
                              "Rawalpunde Urban Development Authority — Land Acquisition Survey")

    # Horizontal line
    canvas.setStrokeColor(colors.HexColor("#1a5276"))
    canvas.setLineWidth(0.5)
    canvas.line(2 * cm, A4[1] - 2.3 * cm, A4[0] - 2 * cm, A4[1] - 2.3 * cm)
    canvas.restoreState()


def _add_page_footer(canvas, doc):
    """Draw footer on each page."""
    canvas.saveState()
    canvas.setFont("Helvetica", 7)
    canvas.setFillColor(colors.HexColor("#95a5a6"))
    canvas.drawCentredString(A4[0] / 2, 1.2 * cm,
                              f"Page {doc.page} | Generated: {timezone.now().strftime('%Y-%m-%d %H:%M:%S')} | RUDA Survey System")
    canvas.line(2 * cm, 1.5 * cm, A4[0] - 2 * cm, 1.5 * cm)
    canvas.restoreState()


def _format_value(value):
    """Format a field value for display."""
    if value is None:
        return "—"
    if isinstance(value, float):
        if value == int(value):
            return str(int(value))
        return f"{value:.4f}"
    return str(value)


def _build_field_table(fields: dict, styles) -> list:
    """Build formatted field sections for the PDF."""
    elements = []

    for section_name, section_fields in FIELD_SECTIONS.items():
        # Section header
        elements.append(Paragraph(section_name, styles["SectionTitle"]))

        # Build field rows for this section
        table_data = []
        for field_key in section_fields:
            if field_key not in fields:
                continue
            label = FIELD_LABELS.get(field_key, field_key)
            value = _format_value(fields.get(field_key))
            table_data.append([
                Paragraph(f"<b>{label}</b>", styles["FieldLabel"]),
                Paragraph(value, styles["FieldValue"]),
            ])

        if not table_data:
            continue

        # Create table with two columns
        table = Table(table_data, colWidths=[5 * cm, 11 * cm])
        table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("TOPPADDING", (0, 0), (-1, -1), 3),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
            ("LINEBELOW", (0, 0), (-1, -2), 0.25, colors.HexColor("#ecf0f1")),
            ("LINEBELOW", (0, -1), (-1, -1), 0.5, colors.HexColor("#bdc3c7")),
            ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#f8f9fa")),
            ("LEFTPADDING", (0, 0), (0, -1), 8),
            ("RIGHTPADDING", (0, 0), (0, -1), 8),
        ]))
        elements.append(table)
        elements.append(Spacer(1, 6))

    return elements


def _build_original_table(original: dict, styles) -> list:
    """Build the original master data table."""
    elements = []
    elements.append(Paragraph("Original Master Data", styles["SectionTitle"]))
    elements.append(Paragraph(
        "Complete baseline data from the original import (includes null values).",
        styles["RevisionInfo"]
    ))

    table_data = []
    for key in SHEET_FIELD_ORDER:
        label = FIELD_LABELS.get(key, key)
        value = _format_value(original.get(key))
        table_data.append([
            Paragraph(f"<b>{label}</b>", styles["FieldLabel"]),
            Paragraph(value, styles["FieldValue"]),
        ])

    if table_data:
        table = Table(table_data, colWidths=[5 * cm, 11 * cm])
        table.setStyle(TableStyle([
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("TOPPADDING", (0, 0), (-1, -1), 2),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
            ("LINEBELOW", (0, 0), (-1, -1), 0.25, colors.HexColor("#ecf0f1")),
            ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#f5f6fa")),
            ("LEFTPADDING", (0, 0), (0, -1), 6),
        ]))
        elements.append(table)

    return elements


def _build_images_section(images: list, styles) -> list:
    """Build the images section with embedded actual images and metadata.

    For each image, if the file exists on disk the actual image is rendered
    (resized to fit the page width while maintaining aspect ratio).
    Otherwise a "file not found" placeholder is shown.
    """
    import os
    from PIL import Image as PilImage
    from reportlab.lib.utils import ImageReader

    elements = []
    PAGE_CONTENT_WIDTH = A4[0] - 4 * cm  # 2cm margins each side
    MAX_IMG_WIDTH = PAGE_CONTENT_WIDTH
    MAX_IMG_HEIGHT = 14 * cm

    if not images:
        elements.append(Paragraph("Evidence Images", styles["SectionTitle"]))
        elements.append(Paragraph("No images uploaded for this survey.", styles["RevisionInfo"]))
        return elements

    # Separate images by type
    survey_images = [img for img in images if img.get("image_type") in ("FRONT", "SECOND")]
    point_images = [img for img in images if img.get("image_type", "").startswith("POINT")]

    _img_groups = [
        ("Evidence Images", survey_images),
        ("Point/Coordinate Images", point_images),
    ]

    for group_title, group_images in _img_groups:
        if not group_images:
            continue

        elements.append(Paragraph(group_title, styles["SectionTitle"]))

        for img in group_images:
            img_type = img.get("image_type", "UNKNOWN")
            checksum = img.get("checksum_sha256", "N/A")
            file_path = img.get("file_path", "")
            uploaded = img.get("uploaded_at", "N/A")
            point_id = img.get("point_id")
            latitude = img.get("latitude")
            longitude = img.get("longitude")

            # Build metadata label
            meta_parts = [f"<b>{img_type}</b>"]
            if point_id:
                meta_parts.append(f"Point: {point_id}")
            if latitude and longitude:
                meta_parts.append(f"Lat: {latitude}, Lon: {longitude}")
            meta_parts.append(f"Uploaded: {uploaded}")
            meta_line = " | ".join(meta_parts)
            elements.append(Paragraph(meta_line, styles["FieldValue"]))

            # Try to resolve the absolute path
            abs_path = None
            if file_path:
                candidate = Path(settings.MEDIA_ROOT) / file_path
                if candidate.is_file():
                    abs_path = candidate

            if abs_path is not None:
                try:
                    with PilImage.open(abs_path) as pil_img:
                        orig_w, orig_h = pil_img.size

                    # Scale to fit within MAX bounds while keeping aspect ratio
                    scale = min(MAX_IMG_WIDTH / orig_w, MAX_IMG_HEIGHT / orig_h, 1.0)
                    display_w = orig_w * scale
                    display_h = orig_h * scale

                    elements.append(Spacer(1, 4))
                    elements.append(Image(str(abs_path), width=display_w, height=display_h))
                except Exception:
                    elements.append(Paragraph(
                        f"  <i>Could not render image file: {abs_path}</i>",
                        styles["FieldLabel"],
                    ))
            else:
                elements.append(Paragraph(
                    f"  <i>Image file not found: {file_path or '(no path)'}  "
                    f"[SHA-256: {checksum[:32]}...]</i>",
                    styles["FieldLabel"],
                ))

            elements.append(Spacer(1, 8))

    return elements


def _build_revision_info(sheet_data: dict, styles) -> list:
    """Build revision metadata section."""
    elements = []
    elements.append(Paragraph("Revision Information", styles["SectionTitle"]))

    info = [
        ("Source", sheet_data.get("source", "master")),
        ("Revision No", str(sheet_data.get("current_revision_no") or "N/A")),
        ("Surveyor", sheet_data.get("surveyor") or "N/A"),
        ("Changed At", str(sheet_data.get("changed_at") or "N/A")),
        ("Accepted At", str(sheet_data.get("accepted_at") or "N/A")),
    ]

    table_data = []
    for label, value in info:
        table_data.append([
            Paragraph(f"<b>{label}</b>", styles["FieldLabel"]),
            Paragraph(value, styles["FieldValue"]),
        ])

    table = Table(table_data, colWidths=[4 * cm, 12 * cm])
    table.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 2),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2),
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#f8f9fa")),
        ("LEFTPADDING", (0, 0), (0, -1), 6),
    ]))
    elements.append(table)

    return elements


def generate_survey_pdf(parcel_code: str) -> bytes:
    """Generate a complete survey PDF for the given parcel.

    Args:
        parcel_code: The parcel code to generate PDF for.

    Returns:
        PDF file content as bytes.

    Raises:
        ApiError: If parcel not found or PDF generation fails.
    """
    parcel = Parcel.objects.filter(parcel_code=parcel_code.strip()).first()
    if parcel is None:
        raise ApiError("Parcel not found.", code="PARCEL_NOT_FOUND", status=404)

    # Build sheet data
    sheet_data = build_sheet(parcel)
    fields = sheet_data.get("fields", {})
    original = sheet_data.get("original", {})
    images = sheet_data.get("images", [])

    # Create PDF
    buffer = io.BytesIO()
    styles = _build_styles()

    doc = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        topMargin=2.5 * cm,
        bottomMargin=2 * cm,
        leftMargin=2 * cm,
        rightMargin=2 * cm,
        title=f"RUDA Survey - {parcel_code}",
        author="RUDA Survey System",
    )

    elements = []

    # Parcel header
    parcel_info = sheet_data.get("parcel", {})
    elements.append(Paragraph(
        f"Parcel: {parcel_info.get('parcel_code', parcel_code)}",
        styles["RudaSubHeader"]
    ))

    # Village / District info
    village = parcel_info.get("village", "")
    district = parcel_info.get("district", "")
    tehsil = parcel_info.get("tehsil", "")
    location_parts = [p for p in [village, tehsil, district] if p]
    if location_parts:
        elements.append(Paragraph(
            " | ".join(location_parts),
            styles["RudaSubHeader"]
        ))

    elements.append(Spacer(1, 12))

    # Revision information
    elements.extend(_build_revision_info(sheet_data, styles))
    elements.append(Spacer(1, 8))

    # Survey fields
    elements.append(Paragraph("Survey Data", styles["SectionTitle"]))
    elements.extend(_build_field_table(fields, styles))
    elements.append(Spacer(1, 8))

    # Images section
    elements.extend(_build_images_section(images, styles))
    elements.append(Spacer(1, 12))

    # Original data (collapsed section)
    elements.extend(_build_original_table(original, styles))

    # Build PDF
    try:
        doc.build(
            elements,
            onFirstPage=_add_page_header,
            onLaterPages=_add_page_header,
        )
    except Exception as exc:
        raise ApiError(
            f"PDF generation failed: {str(exc)}",
            code="PDF_GENERATION_FAILED",
            status=500,
        )

    pdf_bytes = buffer.getvalue()
    buffer.close()

    return pdf_bytes


def generate_pdf_response(parcel_code: str):
    """Generate PDF and return Django HttpResponse.

    Args:
        parcel_code: The parcel code to generate PDF for.

    Returns:
        HttpResponse with PDF content.

    Raises:
        ApiError: If parcel not found or PDF generation fails.
    """
    from django.http import HttpResponse

    pdf_bytes = generate_survey_pdf(parcel_code)

    response = HttpResponse(pdf_bytes, content_type="application/pdf")
    response["Content-Disposition"] = f'attachment; filename="survey_{parcel_code}.pdf"'
    response["Content-Length"] = len(pdf_bytes)

    return response
