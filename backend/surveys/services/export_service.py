"""Excel export service for survey data.

Generates Excel files with:
  - Survey sheet data in formatted tables
  - Original master data
  - Image references
  - Revision metadata
"""
import io
from pathlib import Path

from django.http import HttpResponse
from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

from surveys.api.errors import ApiError
from surveys.models import Parcel
from surveys.services.sheet_service import SHEET_FIELD_ORDER, build_sheet

# Field display labels
FIELD_LABELS = {
    "rd_value": "RD Value",
    "package_no": "Package No",
    "village": "Village",
    "owner_name": "Owner Name",
    "father_name": "Father Name",
    "cnic_no": "CNIC No",
    "khasra_number": "Khasra Number",
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

# Styles
HEADER_FILL = PatternFill(start_color="1a5276", end_color="1a5276", fill_type="solid")
HEADER_FONT = Font(color="FFFFFF", bold=True, size=11)
LABEL_FILL = PatternFill(start_color="f8f9fa", end_color="f8f9fa", fill_type="solid")
LABEL_FONT = Font(bold=True, size=10)
VALUE_FONT = Font(size=10)
THIN_BORDER = Border(
    left=Side(style="thin"),
    right=Side(style="thin"),
    top=Side(style="thin"),
    bottom=Side(style="thin"),
)


def _apply_header_style(ws, row, cols):
    """Apply header styling to a row."""
    for col in range(1, cols + 1):
        cell = ws.cell(row=row, column=col)
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = Alignment(horizontal="center")
        cell.border = THIN_BORDER


def _apply_field_style(ws, row, col, is_label=False):
    """Apply field styling to a cell."""
    cell = ws.cell(row=row, column=col)
    if is_label:
        cell.fill = LABEL_FILL
        cell.font = LABEL_FONT
    else:
        cell.font = VALUE_FONT
    cell.border = THIN_BORDER
    cell.alignment = Alignment(wrap_text=True, vertical="top")


def generate_survey_excel(parcel_code: str) -> bytes:
    """Generate an Excel file for the given parcel.

    Args:
        parcel_code: The parcel code to generate Excel for.

    Returns:
        Excel file content as bytes.

    Raises:
        ApiError: If parcel not found or Excel generation fails.
    """
    parcel = Parcel.objects.filter(parcel_code=parcel_code.strip()).first()
    if parcel is None:
        raise ApiError("Parcel not found.", code="PARCEL_NOT_FOUND", status=404)

    # Build sheet data
    sheet_data = build_sheet(parcel)
    fields = sheet_data.get("fields", {})
    original = sheet_data.get("original", {})
    images = sheet_data.get("images", [])

    wb = Workbook()

    # --- Sheet 1: Survey Data ---
    ws1 = wb.active
    ws1.title = "Survey Data"

    # Title row
    ws1.merge_cells("A1:B1")
    ws1.cell(row=1, column=1, value="RUDA Survey Sheet")
    ws1.cell(row=1, column=1).font = Font(bold=True, size=14, color="1a5276")
    ws1.cell(row=1, column=1).alignment = Alignment(horizontal="center")

    # Parcel info
    parcel_info = sheet_data.get("parcel", {})
    ws1.cell(row=3, column=1, value="Parcel Code:")
    ws1.cell(row=3, column=1).font = LABEL_FONT
    ws1.cell(row=3, column=2, value=parcel_info.get("parcel_code", parcel_code))
    ws1.cell(row=4, column=1, value="Village:")
    ws1.cell(row=4, column=1).font = LABEL_FONT
    ws1.cell(row=4, column=2, value=parcel_info.get("village", ""))
    ws1.cell(row=5, column=1, value="District:")
    ws1.cell(row=5, column=1).font = LABEL_FONT
    ws1.cell(row=5, column=2, value=parcel_info.get("district", ""))

    # Revision info
    ws1.cell(row=7, column=1, value="Source:")
    ws1.cell(row=7, column=1).font = LABEL_FONT
    ws1.cell(row=7, column=2, value=sheet_data.get("source", "master"))
    ws1.cell(row=8, column=1, value="Revision No:")
    ws1.cell(row=8, column=1).font = LABEL_FONT
    ws1.cell(row=8, column=2, value=sheet_data.get("current_revision_no") or "N/A")
    ws1.cell(row=9, column=1, value="Surveyor:")
    ws1.cell(row=9, column=1).font = LABEL_FONT
    ws1.cell(row=9, column=2, value=sheet_data.get("surveyor") or "N/A")

    # Survey fields table
    ws1.cell(row=11, column=1, value="Field")
    ws1.cell(row=11, column=2, value="Value")
    _apply_header_style(ws1, 11, 2)

    row_num = 12
    for field_key in SHEET_FIELD_ORDER:
        if field_key not in fields:
            continue
        label = FIELD_LABELS.get(field_key, field_key)
        value = fields.get(field_key)
        ws1.cell(row=row_num, column=1, value=label)
        ws1.cell(row=row_num, column=2, value=str(value) if value is not None else "—")
        _apply_field_style(ws1, row_num, 1, is_label=True)
        _apply_field_style(ws1, row_num, 2)
        row_num += 1

    # Adjust column widths
    ws1.column_dimensions["A"].width = 25
    ws1.column_dimensions["B"].width = 40

    # --- Sheet 2: Original Data ---
    ws2 = wb.create_sheet("Original Data")

    ws2.merge_cells("A1:B1")
    ws2.cell(row=1, column=1, value="Original Master Data")
    ws2.cell(row=1, column=1).font = Font(bold=True, size=14, color="1a5276")
    ws2.cell(row=1, column=1).alignment = Alignment(horizontal="center")

    ws2.cell(row=3, column=1, value="Field")
    ws2.cell(row=3, column=2, value="Original Value")
    _apply_header_style(ws2, 3, 2)

    row_num = 4
    for field_key in SHEET_FIELD_ORDER:
        label = FIELD_LABELS.get(field_key, field_key)
        value = original.get(field_key)
        ws2.cell(row=row_num, column=1, value=label)
        ws2.cell(row=row_num, column=2, value=str(value) if value is not None else "—")
        _apply_field_style(ws2, row_num, 1, is_label=True)
        _apply_field_style(ws2, row_num, 2)
        row_num += 1

    ws2.column_dimensions["A"].width = 25
    ws2.column_dimensions["B"].width = 40

    # --- Sheet 3: Images ---
    ws3 = wb.create_sheet("Images")

    ws3.merge_cells("A1:D1")
    ws3.cell(row=1, column=1, value="Evidence Images")
    ws3.cell(row=1, column=1).font = Font(bold=True, size=14, color="1a5276")
    ws3.cell(row=1, column=1).alignment = Alignment(horizontal="center")

    headers = ["Image Type", "File Path", "SHA-256 Checksum", "Uploaded At"]
    for col, header in enumerate(headers, 1):
        ws3.cell(row=3, column=col, value=header)
    _apply_header_style(ws3, 3, len(headers))

    row_num = 4
    for img in images:
        ws3.cell(row=row_num, column=1, value=img.get("image_type", ""))
        ws3.cell(row=row_num, column=2, value=img.get("file_path", ""))
        ws3.cell(row=row_num, column=3, value=img.get("checksum_sha256", ""))
        ws3.cell(row=row_num, column=4, value=str(img.get("uploaded_at", "")))
        for col in range(1, len(headers) + 1):
            _apply_field_style(ws3, row_num, col)
        row_num += 1

    if not images:
        ws3.cell(row=4, column=1, value="No images uploaded")
        ws3.cell(row=4, column=1).font = Font(italic=True, color="7f8c8d")

    ws3.column_dimensions["A"].width = 15
    ws3.column_dimensions["B"].width = 50
    ws3.column_dimensions["C"].width = 40
    ws3.column_dimensions["D"].width = 20

    # Save to buffer
    buffer = io.BytesIO()
    wb.save(buffer)
    buffer.seek(0)
    excel_bytes = buffer.getvalue()
    buffer.close()

    return excel_bytes


def generate_excel_response(parcel_code: str):
    """Generate Excel and return Django HttpResponse.

    Args:
        parcel_code: The parcel code to generate Excel for.

    Returns:
        HttpResponse with Excel content.

    Raises:
        ApiError: If parcel not found or Excel generation fails.
    """
    excel_bytes = generate_survey_excel(parcel_code)

    response = HttpResponse(
        excel_bytes,
        content_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    response["Content-Disposition"] = f'attachment; filename="survey_{parcel_code}.xlsx"'
    response["Content-Length"] = len(excel_bytes)

    return response
