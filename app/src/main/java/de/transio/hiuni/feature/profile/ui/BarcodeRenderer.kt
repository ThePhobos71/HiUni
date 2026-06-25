package de.transio.hiuni.feature.profile.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

/**
 * Code-128-Barcode für [content]. MARGIN=0, weil die umgebende Card schon Padding bringt.
 * Cached via [remember]. Gibt null zurück wenn der Encode fehlschlägt (z. B. leerer String).
 */
@Composable
fun rememberCode128Bitmap(
    content: String,
    widthPx: Int,
    heightPx: Int
): ImageBitmap? = remember(content, widthPx, heightPx) {
    if (content.isBlank() || widthPx <= 0 || heightPx <= 0) return@remember null
    runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.CODE_128,
            widthPx,
            heightPx,
            hints
        )
        matrix.toImageBitmap()
    }.recover { ex ->
        if (ex is WriterException || ex is IllegalArgumentException) null else throw ex
    }.getOrNull()
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val w = width
    val h = height
    val pixels = IntArray(w * h)
    val black = 0xFF000000.toInt()
    val transparent = 0x00000000
    for (y in 0 until h) {
        val rowOffset = y * w
        for (x in 0 until w) {
            pixels[rowOffset + x] = if (this[x, y]) black else transparent
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
