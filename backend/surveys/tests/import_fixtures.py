"""Build tiny synthetic master workbooks (in-memory BytesIO) for importer tests.

Layout mirrors the real Annex 4.1 file:
    row1 title · rows2-3 headers · row4 numbering · rows5+ data
No real Excel file is ever touched by the unit test-suite.
"""
import io

from openpyxl import Workbook

HEADERS_ROW2 = {
    "A": "Sr. No", "B": "NID", "C": "Chainage (Km)", "D": "Affected Persons #",
    "E": "Phase#", "F": "Coordinates", "G": None, "H": "Project Commponent",
    "I": "Identification", "J": None, "K": None, "L": None, "M": None, "N": None,
    "O": "Ownership Documents (Sale Deed, Registry, Allotment Letter, Intiqal, Aks Shajra, Other)",
    "P": "Status of Structure (Residential, Commercial, Agri. Deras, Other )",
    "Q": "Structure Name", "R": "Number of Structure", "S": "Status (Owner,Tenant)",
    "T": "Size of Structure", "U": None, "V": None, "W": "Nature of Construction",
    "X": "Unit Rate (Rs.)", "Y": "Structure Compensation (Rs. million)",
    "Z": "Extent of Impact", "AA": "Location (Right, Left, Center)", "AB": "RoW (Y/N)",
    "AC": "Off-set from Propsed Revised CL of River (m)", "AD": None,
}
HEADERS_ROW3 = {
    "F": "North (Latitude)", "G": "East (Longitude) ", "I": "Owner's Name",
    "J": "Father's Name", "K": "Caste", "L": "Village", "M": "Tehsil",
    "N": "District", "T": "Length (ft)", "U": "Width (ft)", "V": "Area (Sq.ft/ R.ft) ",
}

DEFAULT_DATA_ROW = {
    "C": 15000, "E": "Ph#1 ", "F": 31.70384951, "G": 74.4159358, "H": "Dam",
    "I": "Test Owner", "J": "Father X", "K": "Rajput", "L": "Arya Nagar",
    "M": "Ferozwala", "N": "Sheikhupura",
    "O": "Intiqal", "P": "Residential", "Q": "House", "R": 1, "S": "Owner",
    "T": 10, "U": 10, "V": 100, "W": "Pacca", "X": 2200, "Y": 0.22,
    "Z": "Major Impact", "AA": "Right", "AB": "Yes", "AC": 439,
}


def build_master_workbook(data_rows, *, title="Annex 4.1: Affected Residential & Commercial Structures ",
                          sheet="Anex 4.1 Str(Rpr)Dec25Hsn (F)",
                          headers_row2=None, headers_row3=None):
    """data_rows: list of dicts keyed by column letter; A/B auto-filled when absent."""
    wb = Workbook()
    ws = wb.active
    ws.title = sheet
    ws.cell(row=1, column=1, value=title)

    r2 = {**HEADERS_ROW2, **(headers_row2 or {})}
    r3 = {**HEADERS_ROW3, **(headers_row3 or {})}
    for letter, value in r2.items():
        if value is not None:
            ws[f"{letter}2"] = value
    for letter, value in r3.items():
        if value is not None:
            ws[f"{letter}3"] = value
    for col in range(1, 29):  # numbering row 4 (A..AB)
        ws.cell(row=4, column=col, value=col)

    for i, data in enumerate(data_rows, start=5):
        merged = {**DEFAULT_DATA_ROW, **data}
        sr = data.get("A", i - 4)
        ws[f"A{i}"] = sr
        if "B" in data:
            ws[f"B{i}"] = data["B"]
        for letter, value in merged.items():
            if letter in ("A", "B"):
                continue
            if value is not None:
                ws[f"{letter}{i}"] = value
    buffer = io.BytesIO()
    wb.save(buffer)
    buffer.seek(0)
    return buffer


def write_workbook(path, data_rows, **kwargs):
    buffer = build_master_workbook(data_rows, **kwargs)
    with open(path, "wb") as handle:
        handle.write(buffer.getvalue())
    return path
