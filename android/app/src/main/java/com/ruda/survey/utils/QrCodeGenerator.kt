package com.ruda.survey.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object QrCodeGenerator {

    fun generate(
        evidenceId: String,
        parcelCode: String,
        revisionNo: Int?,
        pointId: String?,
        latitude: Double,
        longitude: Double,
        capturedAt: Long,
        size: Int = 256
    ): Bitmap {
        val payload = JSONObject().apply {
            put("type", "SURVEY_EVIDENCE")
            put("evidence_id", evidenceId)
            put("parcel", parcelCode)
            put("revision", revisionNo ?: JSONObject.NULL)
            put("point", pointId ?: JSONObject.NULL)
            put("lat", Math.round(latitude * 1000000.0) / 1000000.0)
            put("lng", Math.round(longitude * 1000000.0) / 1000000.0)
            put("captured_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(capturedAt)))
        }

        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )

        val matrix = QRCodeWriter().encode(payload.toString(), BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    fun buildPayload(
        evidenceId: String,
        parcelCode: String,
        revisionNo: Int?,
        pointId: String?,
        latitude: Double,
        longitude: Double,
        capturedAt: Long
    ): String {
        return JSONObject().apply {
            put("type", "SURVEY_EVIDENCE")
            put("evidence_id", evidenceId)
            put("parcel", parcelCode)
            put("revision", revisionNo ?: JSONObject.NULL)
            put("point", pointId ?: JSONObject.NULL)
            put("lat", Math.round(latitude * 1000000.0) / 1000000.0)
            put("lng", Math.round(longitude * 1000000.0) / 1000000.0)
            put("captured_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(capturedAt)))
        }.toString()
    }
}
