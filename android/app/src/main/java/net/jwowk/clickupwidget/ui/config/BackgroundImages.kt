package net.jwowk.clickupwidget.ui.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Turns a picked image into the small, pre-blurred JPEG a widget uses as its background. */
object BackgroundImages {
  /** Longest edge of the stored image; a widget never needs more. */
  const val MAX_EDGE = 720
  private const val JPEG_QUALITY = 88

  /** Final file for a saved widget background. */
  fun finalFile(context: Context, appWidgetId: Int) = File(context.filesDir, "widget_bg_$appWidgetId.jpg")

  /** Scratch file used while the config screen is open; promoted to [finalFile] on Save. */
  fun editFile(context: Context, appWidgetId: Int, version: Int) = File(context.filesDir, "widget_bg_${appWidgetId}_edit$version.jpg")

  /** Deletes every scratch file for [appWidgetId]. */
  fun deleteEditFiles(context: Context, appWidgetId: Int) {
    val prefix = "widget_bg_${appWidgetId}_edit"
    context.filesDir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
  }

  /**
   * Decodes [uri] downsampled to a long edge <= [MAX_EDGE] (honouring EXIF rotation), blurs it with
   * [blurRadius] (0..25) and writes a JPEG to [out]. Runs on a background dispatcher; returns false on any failure.
   */
  suspend fun render(context: Context, uri: Uri, blurRadius: Int, out: File): Boolean = withContext(Dispatchers.Default) {
    try {
      val resolver = context.contentResolver
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false
      coroutineContext.ensureActive()

      val longEdge = max(bounds.outWidth, bounds.outHeight)
      var sample = 1
      while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
      val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
      } ?: return@withContext false
      coroutineContext.ensureActive()

      val orientation = resolver.openInputStream(uri)?.use {
        runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()
      } ?: ExifInterface.ORIENTATION_NORMAL
      val matrix = Matrix().apply {
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) postScale(scale, scale)
        applyExifOrientation(this, orientation)
      }
      val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      if (oriented !== decoded) decoded.recycle()

      val pixels = IntArray(oriented.width * oriented.height)
      oriented.getPixels(pixels, 0, oriented.width, 0, 0, oriented.width, oriented.height)
      val w = oriented.width
      val h = oriented.height
      oriented.recycle()
      ImageBlur.blur(pixels, w, h, blurRadius)
      coroutineContext.ensureActive()

      val result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
      val tmp = File(out.parentFile, out.name + ".tmp")
      val written = FileOutputStream(tmp).use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
      result.recycle()
      if (!written) {
        tmp.delete()
        return@withContext false
      }
      out.delete()
      tmp.renameTo(out)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      false
    } catch (e: OutOfMemoryError) {
      false
    }
  }

  private fun applyExifOrientation(m: Matrix, orientation: Int) {
    when (orientation) {
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
      ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
      ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
    }
  }
}
