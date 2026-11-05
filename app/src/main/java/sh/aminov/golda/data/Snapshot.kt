package sh.aminov.golda.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A photo of a receipt on its way to Gemini: taken by the phone's camera into the cache, cut down and
 * put in the voice queue as a note. From there it goes the voice note's way, and its file with it.
 */
object Snapshot {
    /** The long side a receipt stays readable at, and still a small request. */
    const val LONGEST_SIDE = 1600

    private const val QUALITY = 85

    /** Where the camera writes: one file, known again after the app was closed behind the camera. */
    fun shot(context: Context): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")

    /** The largest power of two that leaves the long side at or over [longest], for decoding at a fraction of the size. */
    fun sampleSize(width: Int, height: Int, longest: Int = LONGEST_SIDE): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= longest) sample *= 2
        return sample
    }

    /** [width] × [height] with the long side brought down to [longest], never up. */
    fun fitted(width: Int, height: Int, longest: Int = LONGEST_SIDE): Pair<Int, Int> {
        val side = max(width, height)
        if (side <= longest) return width to height
        val scale = longest.toFloat() / side
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** [from] as a JPEG in [to], upright and no bigger than [LONGEST_SIDE]; false when it cannot be read. */
    fun shrink(from: File, to: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(from.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeFile(from.path, options) ?: return false
        val (width, height) = fitted(decoded.width, decoded.height)
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        val upright = turned(scaled, rotation(from))
        val written = to.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (!written) to.delete()
        return written
    }

    private fun rotation(file: File): Int = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun turned(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
