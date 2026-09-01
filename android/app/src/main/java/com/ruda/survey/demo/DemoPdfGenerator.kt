package com.ruda.survey.demo

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DemoPdfGenerator(private val context: Context) {

    private val db = DemoDatabase.getInstance(context)
    private val dao = db.demoDao()
    private val gson = Gson()

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN_LEFT = 50f
        private const val MARGIN_TOP = 60f
        private const val MARGIN_RIGHT = 50f
        private const val MARGIN_BOTTOM = 60f
        private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
        private const val LINE_SPACING = 22f
        private const val FIELD_LABEL_RATIO = 0.38f
    }

    private val fieldLabels: Map<String, String> = mapOf(
        "parcel_code" to "Parcel Code",
        "source_nid" to "Source NID",
        "village" to "Village",
        "tehsil" to "Tehsil",
        "district" to "District",
        "owner_name" to "Owner Name",
        "father_name" to "Father Name",
        "caste" to "Caste",
        "project_component" to "Project Component",
        "phase_code" to "Phase Code",
        "chainage_m" to "Chainage (m)",
        "structure_status" to "Structure Status",
        "structure_name" to "Structure Name",
        "structure_count" to "Structure Count",
        "tenure_status" to "Tenure Status",
        "ownership_documents" to "Ownership Documents",
        "length_ft" to "Length (ft)",
        "width_ft" to "Width (ft)",
        "area_value" to "Area (sq ft)",
        "construction_nature" to "Construction Nature",
        "unit_rate_rs" to "Unit Rate (Rs)",
        "compensation_million" to "Compensation (M)",
        "impact_extent" to "Impact Extent",
        "river_location" to "River Location",
        "in_row_yn" to "In Row",
        "cl_offset_m" to "CL Offset (m)",
        "khasra_number" to "Khasra Number",
        "mauza_number" to "Mauza Number",
        "latitude" to "Latitude",
        "longitude" to "Longitude",
        "cnic" to "CNIC",
        "contact_number" to "Contact Number",
        "sr_no" to "SR No",
        "affected_persons_count" to "Affected Persons",
        "extra_note" to "Extra Note"
    )

    private val orderedKeys = listOf(
        "parcel_code", "source_nid", "village", "tehsil", "district",
        "owner_name", "father_name", "caste",
        "project_component", "phase_code", "chainage_m",
        "structure_status", "structure_name", "structure_count",
        "tenure_status", "ownership_documents",
        "length_ft", "width_ft", "area_value",
        "construction_nature", "unit_rate_rs", "compensation_million",
        "impact_extent", "river_location", "in_row_yn", "cl_offset_m",
        "khasra_number", "mauza_number",
        "latitude", "longitude",
        "cnic", "contact_number",
        "sr_no", "affected_persons_count", "extra_note"
    )

    fun generate(parcelCode: String): ByteArray {
        val parcel = runBlocking {
            dao.getParcel(parcelCode)
        } ?: throw IllegalArgumentException("Parcel not found: $parcelCode")

        val latestRevision = runBlocking {
            dao.getLatestRevision(parcelCode)
        }

        val images = runBlocking {
            val revNo = latestRevision?.revision_no ?: parcel.current_revision_no
            dao.getImages(parcelCode, revNo).ifEmpty {
                dao.getAllImagesForParcel(parcelCode)
            }
        }

        val fieldsMap = parseFields(parcel, latestRevision)

        val document = PdfDocument()

        renderFieldsPage(document, parcelCode, fieldsMap, latestRevision)

        renderImagePages(document, parcelCode, images)

        val output = ByteArrayOutputStream()
        document.writeTo(output)
        document.close()

        return output.toByteArray()
    }

    private fun parseFields(
        parcel: DemoParcel,
        revision: DemoRevision?
    ): LinkedHashMap<String, String> {
        val resultMap = linkedMapOf<String, String>()

        val originalFields: Map<String, Any?> = parseJson(parcel.original_fields_json)
        val currentFields: Map<String, Any?> = parseJson(parcel.current_fields_json)
        val revisionFields: Map<String, Any?> = if (revision != null) {
            parseJson(revision.full_payload_json)
        } else {
            emptyMap()
        }

        for (key in orderedKeys) {
            val value = revisionFields[key] ?: currentFields[key] ?: originalFields[key]
            if (isNonEmpty(value)) {
                val label = fieldLabels[key] ?: humanizeKey(key)
                resultMap[label] = formatValue(key, value)
            }
        }

        for ((key, value) in originalFields) {
            if (key !in orderedKeys && isNonEmpty(value)) {
                val label = fieldLabels[key] ?: humanizeKey(key)
                resultMap[label] = formatValue(key, value)
            }
        }

        return resultMap
    }

    private fun parseJson(json: String): Map<String, Any?> {
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson<Map<String, Any?>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun isNonEmpty(value: Any?): Boolean {
        if (value == null) return false
        val str = value.toString()
        return str.isNotBlank() && str != "null"
    }

    private fun humanizeKey(key: String): String {
        return key.replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun formatValue(key: String, value: Any?): String {
        if (value == null) return ""
        val str = value.toString()
        if (str == "null" || str.isBlank()) return ""

        return when (key) {
            "latitude", "longitude" -> formatDouble(str, 6)
            "compensation_million" -> formatDouble(str, 6) + " M"
            "area_value" -> formatDouble(str, 2)
            "length_ft", "width_ft", "chainage_m", "cl_offset_m" -> formatDouble(str, 2)
            "unit_rate_rs" -> formatDouble(str, 0)
            else -> str
        }
    }

    private fun formatDouble(str: String, decimals: Int): String {
        return try {
            val d = str.toDouble()
            String.format("%.${decimals}f", d)
        } catch (e: NumberFormatException) {
            str
        }
    }

    private fun renderFieldsPage(
        document: PdfDocument,
        parcelCode: String,
        fields: LinkedHashMap<String, String>,
        revision: DemoRevision?
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = createPaint(Typeface.BOLD, 20f, Color.BLACK, Paint.Align.CENTER)
        val subtitlePaint = createPaint(Typeface.NORMAL, 12f, Color.DKGRAY, Paint.Align.CENTER)
        val labelPaint = createPaint(Typeface.BOLD, 11f, Color.parseColor("#333333"))
        val valuePaint = createPaint(Typeface.NORMAL, 11f, Color.BLACK)
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.5f
        }
        val demoStampPaint = createPaint(Typeface.BOLD, 10f, Color.parseColor("#FF6600"), Paint.Align.CENTER)

        val fieldLabelWidth = CONTENT_WIDTH * FIELD_LABEL_RATIO
        val fieldValueX = MARGIN_LEFT + fieldLabelWidth + 10f
        val fieldValueWidth = CONTENT_WIDTH - fieldLabelWidth - 10f

        var y = MARGIN_TOP

        canvas.drawText("RUDA Survey Report", PAGE_WIDTH / 2f, y, titlePaint)
        y += 28f

        canvas.drawText("Parcel: $parcelCode", PAGE_WIDTH / 2f, y, subtitlePaint)
        y += 18f

        if (revision != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
            val dateStr = dateFormat.format(Date(revision.created_at))
            canvas.drawText(
                "Revision #${revision.revision_no} | $dateStr",
                PAGE_WIDTH / 2f, y, subtitlePaint
            )
            y += 14f
        }

        y += 14f
        canvas.drawText("DEMO MODE", PAGE_WIDTH / 2f, y, demoStampPaint)
        y += 24f

        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, dividerPaint)
        y += 16f

        val entries = fields.entries.toList()
        var i = 0

        while (i < entries.size) {
            if (y > PAGE_HEIGHT - MARGIN_BOTTOM - LINE_SPACING) {
                val footerPaint = createPaint(Typeface.ITALIC, 8f, Color.GRAY, Paint.Align.CENTER)
                val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US)
                canvas.drawText(
                    "Generated: ${dateFormat.format(Date())}",
                    PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2f, footerPaint
                )
                document.finishPage(page)

                val newPageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1
                ).create()
                val newPage = document.startPage(newPageInfo)
                y = MARGIN_TOP + 10f
                continueFieldsOnPage(newPage.canvas, entries, i, y, fieldLabelWidth, fieldValueX, fieldValueWidth, labelPaint, valuePaint, dividerPaint)
                return
            }

            val (label, value) = entries[i]
            y = drawFieldRow(canvas, label, value, y, fieldLabelWidth, fieldValueX, fieldValueWidth, labelPaint, valuePaint, dividerPaint)
            i++
        }

        val footerPaint = createPaint(Typeface.ITALIC, 8f, Color.GRAY, Paint.Align.CENTER)
        val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US)
        canvas.drawText(
            "Generated: ${dateFormat.format(Date())}",
            PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2f, footerPaint
        )

        document.finishPage(page)
    }

    private fun continueFieldsOnPage(
        canvas: Canvas,
        entries: List<Map.Entry<String, String>>,
        startIndex: Int,
        startY: Float,
        fieldLabelWidth: Float,
        fieldValueX: Float,
        fieldValueWidth: Float,
        labelPaint: Paint,
        valuePaint: Paint,
        dividerPaint: Paint
    ) {
        var y = startY

        canvas.drawLine(MARGIN_LEFT, y - 10f, PAGE_WIDTH - MARGIN_RIGHT, y - 10f, dividerPaint)

        for (i in startIndex until entries.size) {
            if (y > PAGE_HEIGHT - MARGIN_BOTTOM - LINE_SPACING) {
                break
            }

            val (label, value) = entries[i]
            y = drawFieldRow(canvas, label, value, y, fieldLabelWidth, fieldValueX, fieldValueWidth, labelPaint, valuePaint, dividerPaint)
        }

        val footerPaint = createPaint(Typeface.ITALIC, 8f, Color.GRAY, Paint.Align.CENTER)
        canvas.drawText(
            "Continued",
            PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2f, footerPaint
        )
    }

    private fun drawFieldRow(
        canvas: Canvas,
        label: String,
        value: String,
        startY: Float,
        fieldLabelWidth: Float,
        fieldValueX: Float,
        fieldValueWidth: Float,
        labelPaint: Paint,
        valuePaint: Paint,
        dividerPaint: Paint
    ): Float {
        var y = startY

        canvas.drawText(label, MARGIN_LEFT, y, labelPaint)

        val textPaint = TextPaint().apply {
            typeface = valuePaint.typeface
            textSize = valuePaint.textSize
            color = valuePaint.color
            isAntiAlias = valuePaint.isAntiAlias
        }

        val staticLayout = buildStaticLayout(value, textPaint, fieldValueWidth.toInt())

        canvas.save()
        canvas.translate(fieldValueX, y - LINE_SPACING + 4f)
        staticLayout.draw(canvas)
        canvas.restore()

        val blockHeight = staticLayout.height.toFloat().coerceAtLeast(LINE_SPACING)
        y += blockHeight + 2f

        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, dividerPaint)
        y += 6f

        return y
    }

    private fun renderImagePages(
        document: PdfDocument,
        parcelCode: String,
        images: List<DemoImage>
    ) {
        if (images.isEmpty()) return

        val headerPaint = createPaint(Typeface.BOLD, 14f, Color.BLACK)
        val subtitlePaint = createPaint(Typeface.NORMAL, 11f, Color.DKGRAY)
        val infoPaint = createPaint(Typeface.NORMAL, 9f, Color.GRAY)
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.5f
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        val footerPaint = createPaint(Typeface.ITALIC, 8f, Color.GRAY, Paint.Align.CENTER)

        for ((index, image) in images.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1
            ).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = MARGIN_TOP

            val imageTypeLabel = image.image_type
                .replace("_", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            canvas.drawText(
                "Image ${index + 1} of ${images.size} - $imageTypeLabel",
                MARGIN_LEFT, y, headerPaint
            )
            y += 20f

            canvas.drawText("Parcel: $parcelCode", MARGIN_LEFT, y, subtitlePaint)
            y += 16f

            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, dividerPaint)
            y += 12f

            val bitmap = decodeSampledBitmap(image.file_path, 1200, 900)
            if (bitmap != null) {
                val maxHeight = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM - 140
                val scale = minOf(
                    CONTENT_WIDTH.toFloat() / bitmap.width,
                    maxHeight.toFloat() / bitmap.height,
                    1f
                )

                val drawWidth = (bitmap.width * scale).toInt()
                val drawHeight = (bitmap.height * scale).toInt()
                val drawX = MARGIN_LEFT + (CONTENT_WIDTH - drawWidth) / 2f

                val borderPaint = Paint().apply {
                    color = Color.parseColor("#CCCCCC")
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }
                canvas.drawRect(
                    drawX - 1f, y - 1f,
                    drawX + drawWidth + 1f, y + drawHeight + 1f,
                    borderPaint
                )

                val destRect = RectF(drawX, y, drawX + drawWidth, y + drawHeight)
                canvas.drawBitmap(bitmap, null, destRect, null)

                y += drawHeight + 16f
            } else {
                val unavailablePaint = createPaint(Typeface.ITALIC, 12f, Color.RED, Paint.Align.CENTER)
                canvas.drawText("[Image not available]", PAGE_WIDTH / 2f, y + 30f, unavailablePaint)
                y += 60f
            }

            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, dividerPaint)
            y += 12f

            y = drawImageMetadata(canvas, image, imageTypeLabel, y, infoPaint, dateFormat)

            canvas.drawText(
                "Page ${pageInfo.pageNumber}",
                PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM / 2f, footerPaint
            )

            document.finishPage(page)
        }
    }

    private fun drawImageMetadata(
        canvas: Canvas,
        image: DemoImage,
        imageTypeLabel: String,
        startY: Float,
        infoPaint: Paint,
        dateFormat: SimpleDateFormat
    ): Float {
        var y = startY

        val metaLines = mutableListOf<String>()
        metaLines.add("Type: $imageTypeLabel")
        metaLines.add("File: ${File(image.file_path).name}")

        if (image.latitude != null && image.longitude != null) {
            metaLines.add("GPS: ${String.format("%.6f, %.6f", image.latitude, image.longitude)}")
        }
        if (!image.area_name.isNullOrBlank()) {
            metaLines.add("Area: ${image.area_name}")
        }
        if (image.captured_at != null) {
            metaLines.add("Captured: ${dateFormat.format(Date(image.captured_at))}")
        }
        if (image.accuracy != null) {
            metaLines.add("Accuracy: ${String.format("%.1f m", image.accuracy)}")
        }
        if (image.file_size != null) {
            val kb = image.file_size / 1024.0
            metaLines.add("Size: ${String.format("%.1f KB", kb)}")
        }

        for (line in metaLines) {
            if (y > PAGE_HEIGHT - MARGIN_BOTTOM) break
            canvas.drawText(line, MARGIN_LEFT, y, infoPaint)
            y += 14f
        }

        return y
    }

    private fun createPaint(
        style: Int,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint {
        return Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, style)
            textSize = size
            this.color = color
            textAlign = align
            isAntiAlias = true
        }
    }

    private fun buildStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 1f, true)
        }
    }

    private fun decodeSampledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
