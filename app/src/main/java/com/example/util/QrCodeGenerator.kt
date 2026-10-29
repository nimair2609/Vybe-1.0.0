package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * QR Code Generator for VYBE profiles.
 * Generates high-contrast, error-corrected QR code bitmaps suitable for on-screen display,
 * camera scanning, and social sharing.
 */
object QrCodeGenerator {

  fun generateQrBitmap(
    content: String,
    sizePx: Int = 512,
    foregroundHex: Int = Color.WHITE,
    backgroundHex: Int = Color.TRANSPARENT
  ): Bitmap? {
    if (content.isBlank()) return null
    return try {
      val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
        put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
        put(EncodeHintType.MARGIN, 1)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
      }
      val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val width = bitMatrix.width
      val height = bitMatrix.height
      val pixels = IntArray(width * height)

      for (y in 0 until height) {
        val offset = y * width
        for (x in 0 until width) {
          pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundHex else backgroundHex
        }
      }

      Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
      }
    } catch (_: Throwable) {
      null
    }
  }

  const val CANONICAL_WEB_HOST = "ais-dev-ms5nkc6key5usu7rzmjfqx-730319811920.asia-northeast1.run.app"
  const val CANONICAL_WEB_BASE = "https://$CANONICAL_WEB_HOST"

  /**
   * Generates the official app deep link for instant Android applet routing (`vybe://profile/{userId}`).
   */
  fun buildProfileDeepLink(userId: String): String {
    val cleanId = userId.trim()
    return "vybe://profile/$cleanId"
  }

  /**
   * Generates the canonical web URL fallback that routes to the profile with universal deep linking.
   */
  fun buildProfileShareUrl(username: String, userId: String): String {
    val cleanUser = username.removePrefix("@").trim()
    val cleanId = userId.trim()
    return "$CANONICAL_WEB_BASE/u/$cleanId?username=$cleanUser"
  }
}
