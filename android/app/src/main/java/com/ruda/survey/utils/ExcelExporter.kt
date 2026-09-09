package com.ruda.survey.utils

import com.ruda.survey.domain.model.SurveyItem

object ExcelExporter {

    fun generate(survey: SurveyItem): ByteArray {
        val xml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"")
            appendLine(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">")
            appendLine("<Styles>")
            appendLine("<Style ss:ID=\"header\">")
            appendLine("<Font ss:Bold=\"1\" ss:Size=\"12\"/>")
            appendLine("</Style>")
            appendLine("<Style ss:ID=\"label\">")
            appendLine("<Font ss:Bold=\"1\"/>")
            appendLine("</Style>")
            appendLine("</Styles>")
            appendLine("<Worksheet ss:Name=\"Survey Data\">")
            appendLine("<Table>")

            appendLine("<Row><Cell ss:StyleID=\"header\"><Data ss:Type=\"String\">RUDA Survey Report</Data></Cell></Row>")
            appendLine("<Row/>")

            addRow("SR No", survey.srNo.toString())
            addRow("Parcel ID", survey.parcelId)
            addRow("Village", survey.village)
            addRow("Owner Name", survey.ownerName)
            addRow("Father Name", survey.fName)
            addRow("CNIC", survey.cnic)
            addRow("Phone", survey.phone)
            addRow("Land Owner Doc", survey.landOwnerDoc)
            addRow("Khasra No", survey.khasraNo)
            addRow("Land Area", survey.landArea)
            addRow("Electricity", survey.electricityConnectionName)
            appendLine("<Row/>")

            addRow("Latitude", survey.lat.toString())
            addRow("Longitude", survey.lng.toString())
            appendLine("<Row/>")

            addRow("Structure", survey.structuralName)
            addRow("Status", survey.status)
            addRow("Construction", survey.natureOfConstruction)
            addRow("Length", survey.length)
            addRow("Width", survey.width)
            addRow("Area", survey.area)
            addRow("RD", survey.rd)
            addRow("Package", survey.pkg)
            appendLine("<Row/>")

            addRow("Image 1", survey.imgOne.ifBlank { "Not uploaded" })
            addRow("Image 2", survey.imgTwo.ifBlank { "Not uploaded" })

            appendLine("</Table>")
            appendLine("</Worksheet>")
            appendLine("</Workbook>")
        }

        return xml.toByteArray(Charsets.UTF_8)
    }

    private fun StringBuilder.addRow(label: String, value: String) {
        appendLine("<Row>")
        appendLine("<Cell ss:StyleID=\"label\"><Data ss:Type=\"String\">$label</Data></Cell>")
        appendLine("<Cell><Data ss:Type=\"String\">$value</Data></Cell>")
        appendLine("</Row>")
    }
}
