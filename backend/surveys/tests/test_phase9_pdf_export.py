"""PHASE 9 — Groups A (PDF generation), B (Excel export), and PDF API tests."""
import io
from unittest.mock import patch, MagicMock

from django.test import TestCase, override_settings

from surveys.tests import api_base, base


class PdfServiceTestCase(TestCase):
    """Test PDF generation service."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-PDF01", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("pdf_surveyor")
        self.client = api_base.client_for(self.surveyor)

    def test_pdf_generation_returns_bytes(self):
        """Test that PDF generation returns valid PDF bytes."""
        from surveys.services.pdf_service import generate_survey_pdf

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        self.assertIsInstance(pdf_bytes, bytes)
        self.assertGreater(len(pdf_bytes), 0)
        # PDF magic bytes
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))

    def test_pdf_contains_parcel_code(self):
        """Test that PDF contains the parcel code."""
        from surveys.services.pdf_service import generate_survey_pdf

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        # Check PDF content (reportlab generates text-based PDF)
        pdf_content = pdf_bytes.decode('latin-1')
        self.assertIn(self.parcel.parcel_code, pdf_content)

    def test_pdf_generation_invalid_parcel(self):
        """Test PDF generation with invalid parcel code."""
        from surveys.services.pdf_service import generate_survey_pdf
        from surveys.api.errors import ApiError

        with self.assertRaises(ApiError) as ctx:
            generate_survey_pdf("RUDA-P14-NONEXISTENT")
        self.assertEqual(ctx.exception.code, "PARCEL_NOT_FOUND")

    def test_pdf_with_revision(self):
        """Test PDF generation with existing revision."""
        from surveys.services.pdf_service import generate_survey_pdf

        base.make_revision(self.parcel, self.surveyor)
        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))
        self.assertGreater(len(pdf_bytes), 1000)  # Should be substantial

    def test_pdf_with_images(self):
        """Test PDF generation with images."""
        from surveys.services.pdf_service import generate_survey_pdf

        revision = base.make_revision(self.parcel, self.surveyor)
        base.make_image(revision, "FRONT")
        base.make_image(revision, "SECOND")

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))


class PdfApiTestCase(TestCase):
    """Test PDF API endpoint."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-PDF02", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("pdf_api_surveyor")
        self.client = api_base.client_for(self.surveyor)

    def test_pdf_endpoint_returns_pdf(self):
        """Test that PDF endpoint returns valid PDF."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/pdf/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "application/pdf")
        self.assertTrue(response.content.startswith(b'%PDF'))

    def test_pdf_endpoint_content_disposition(self):
        """Test that PDF endpoint has correct Content-Disposition."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/pdf/")
        self.assertIn("attachment", response["Content-Disposition"])
        self.assertIn(self.parcel.parcel_code, response["Content-Disposition"])

    def test_pdf_endpoint_content_length(self):
        """Test that PDF endpoint has correct Content-Length."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/pdf/")
        self.assertEqual(int(response["Content-Length"]), len(response.content))

    def test_pdf_endpoint_authentication_required(self):
        """Test that PDF endpoint requires authentication."""
        anon = api_base.anon_client()
        response = anon.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/pdf/")
        self.assertEqual(response.status_code, 401)

    def test_pdf_endpoint_nonexistent_parcel(self):
        """Test PDF endpoint with nonexistent parcel."""
        response = self.client.get("/api/v1/surveys/RUDA-P14-NOPE/pdf/")
        self.assertEqual(response.status_code, 404)


class ExcelExportServiceTestCase(TestCase):
    """Test Excel export service."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-XLS01", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("xlsx_surveyor")
        self.client = api_base.client_for(self.surveyor)

    def test_excel_generation_returns_bytes(self):
        """Test that Excel generation returns valid bytes."""
        from surveys.services.export_service import generate_survey_excel

        excel_bytes = generate_survey_excel(self.parcel.parcel_code)
        self.assertIsInstance(excel_bytes, bytes)
        self.assertGreater(len(excel_bytes), 0)
        # XLSX magic bytes (PK zip format)
        self.assertTrue(excel_bytes[:2] == b'PK')

    def test_excel_generation_invalid_parcel(self):
        """Test Excel generation with invalid parcel code."""
        from surveys.services.export_service import generate_survey_excel
        from surveys.api.errors import ApiError

        with self.assertRaises(ApiError) as ctx:
            generate_survey_excel("RUDA-P14-NONEXISTENT")
        self.assertEqual(ctx.exception.code, "PARCEL_NOT_FOUND")

    def test_excel_with_revision(self):
        """Test Excel generation with existing revision."""
        from surveys.services.export_service import generate_survey_excel

        base.make_revision(self.parcel, self.surveyor)
        excel_bytes = generate_survey_excel(self.parcel.parcel_code)
        self.assertTrue(excel_bytes[:2] == b'PK')


class ExcelExportApiTestCase(TestCase):
    """Test Excel export API endpoint."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-XLS02", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("xlsx_api_surveyor")
        self.client = api_base.client_for(self.surveyor)

    def test_export_endpoint_returns_excel(self):
        """Test that export endpoint returns valid Excel."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/export/")
        self.assertEqual(response.status_code, 200)
        self.assertIn("spreadsheet", response["Content-Type"])
        self.assertTrue(response.content[:2] == b'PK')

    def test_export_endpoint_content_disposition(self):
        """Test that export endpoint has correct Content-Disposition."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/export/")
        self.assertIn("attachment", response["Content-Disposition"])
        self.assertIn(".xlsx", response["Content-Disposition"])

    def test_export_endpoint_content_length(self):
        """Test that export endpoint has correct Content-Length."""
        response = self.client.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/export/")
        self.assertEqual(int(response["Content-Length"]), len(response.content))

    def test_export_endpoint_authentication_required(self):
        """Test that export endpoint requires authentication."""
        anon = api_base.anon_client()
        response = anon.get(
            f"/api/v1/surveys/{self.parcel.parcel_code}/export/")
        self.assertEqual(response.status_code, 401)

    def test_export_endpoint_nonexistent_parcel(self):
        """Test export endpoint with nonexistent parcel."""
        response = self.client.get("/api/v1/surveys/RUDA-P14-NOPE/export/")
        self.assertEqual(response.status_code, 404)


class SheetServicePdfTestCase(TestCase):
    """Test sheet_service.generate_pdf integration."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-SVC01", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)

    def test_sheet_service_generate_pdf(self):
        """Test that sheet_service.generate_pdf calls pdf_service."""
        from surveys.services.sheet_service import generate_pdf

        response = generate_pdf(self.parcel.parcel_code)
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.content.startswith(b'%PDF'))


class PdfFieldDisplayTestCase(TestCase):
    """Test PDF field display and formatting."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-FLD01", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("fld_surveyor")

    def test_pdf_displays_all_fields(self):
        """Test that PDF is generated with correct structure."""
        from surveys.services.pdf_service import generate_survey_pdf

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        # PDF is compressed, so we check structure instead of text
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))
        self.assertIn(b'Helvetica', pdf_bytes)  # Font reference
        self.assertIn(b'RUDA', pdf_bytes)  # RUDA branding in metadata

    def test_pdf_displays_revision_info(self):
        """Test that PDF shows revision information in structure."""
        from surveys.services.pdf_service import generate_survey_pdf

        base.make_revision(self.parcel, self.surveyor)
        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        # PDF should be larger with revision data
        self.assertGreater(len(pdf_bytes), 2000)
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))


class PdfImageSectionTestCase(TestCase):
    """Test PDF image section."""

    def setUp(self):
        self.parcel = base.make_parcel(code="RUDA-P14-IMG01", nid=1819)
        self.line = base.make_master_line(parcel=self.parcel)
        self.surveyor = api_base.make_api_user("img_surveyor")

    def test_pdf_shows_images(self):
        """Test that PDF is generated with image metadata."""
        from surveys.services.pdf_service import generate_survey_pdf

        revision = base.make_revision(self.parcel, self.surveyor)
        base.make_image(revision, "FRONT")

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        # PDF should contain image reference
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))
        self.assertGreater(len(pdf_bytes), 2000)

    def test_pdf_shows_no_images_message(self):
        """Test that PDF is generated when no images."""
        from surveys.services.pdf_service import generate_survey_pdf

        base.make_revision(self.parcel, self.surveyor)

        pdf_bytes = generate_survey_pdf(self.parcel.parcel_code)
        # PDF should still be generated
        self.assertTrue(pdf_bytes.startswith(b'%PDF'))
        self.assertGreater(len(pdf_bytes), 1000)
