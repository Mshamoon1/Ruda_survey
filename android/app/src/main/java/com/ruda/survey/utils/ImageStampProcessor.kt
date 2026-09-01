package com.ruda.survey.utils

import android.graphics.*
import java.text.SimpleDateFormat
import java.util.*

object ImageStampProcessor {

    data class StampData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float?,
        val areaName: String?,
        val capturedAt: Long,
        val parcelCode: String,
        val pointId: String?,
        val qrBitmap: Bitmap?
    )

    fun stampImage(originalBitmap: Bitmap, data: StampData): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        val overlayHeight = (height * 0.28f).toInt().coerceAtLeast(320)
        val overlayTop = height - overlayHeight

        val overlayPaint = Paint().apply {
            color = Color.argb(220, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, overlayTop.toFloat(), width.toFloat(), height.toFloat(), overlayPaint)

        val accentPaint = Paint().apply {
            color = Color.argb(255, 11, 95, 165)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, overlayTop.toFloat(), width.toFloat(), (overlayTop + 6).toFloat(), accentPaint)

        val padding = (width * 0.04f).toInt()
        val textLeft = padding.toFloat()
        val textRight = (width - padding).toFloat()
        val contentTop = overlayTop + 24f

        val titleSize = (width * 0.038f).coerceIn(22f, 40f)
        val bodySize = (width * 0.032f).coerceIn(18f, 32f)
        val smallSize = (width * 0.026f).coerceIn(14f, 24f)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titleSize
            typeface = Typeface.DEFAULT_BOLD
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = bodySize
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = smallSize
        }

        val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 100, 180, 255)
            textSize = smallSize
            typeface = Typeface.DEFAULT_BOLD
        }

        var y = contentTop + titleSize

        val appName = "RUDA Survey Evidence"
        canvas.drawText(appName, textLeft, y, titlePaint)
        y += titleSize + 12f

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val stf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(data.capturedAt))
        val timeStr = stf.format(Date(data.capturedAt))

        canvas.drawText("Date: $dateStr  Time: $timeStr", textLeft, y, bodyPaint)
        y += bodySize + 16f

        if (!data.areaName.isNullOrBlank()) {
            canvas.drawText(data.areaName, textLeft, y, bodyPaint)
            y += bodySize + 8f
        }

        val latStr = String.format(Locale.US, "Lat  %.6f\u00B0", data.latitude)
        val lngStr = String.format(Locale.US, "Long %.6f\u00B0", data.longitude)
        canvas.drawText(latStr, textLeft, y, accentTextPaint)
        y += bodySize + 6f
        canvas.drawText(lngStr, textLeft, y, accentTextPaint)
        y += bodySize + 6f

        if (data.accuracy != null) {
            canvas.drawText("Accuracy: ${String.format(Locale.US, "%.1f", data.accuracy)}m", textLeft, y, labelPaint)
            y += smallSize + 6f
        }

        canvas.drawText("Parcel: ${data.parcelCode}", textLeft, y, labelPaint)
        y += smallSize + 4f

        if (!data.pointId.isNullOrBlank()) {
            canvas.drawText("Point: ${data.pointId}", textLeft, y, labelPaint)
        }

        if (data.qrBitmap != null) {
            val qrSize = (overlayHeight * 0.7f).toInt().coerceAtMost(200)
            val qrLeft = (width - padding - qrSize).toFloat()
            val qrTop = overlayTop + 30f
            canvas.drawBitmap(data.qrBitmap, null, RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize), null)
        }

        return result
    }
}
