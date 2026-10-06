package com.climbtracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.image.CropQuad
import com.climbtracker.core.image.ImageMath
import java.io.File
import java.util.UUID

/** Reads photos from the camera or gallery and keeps wall photos in the app's private storage. */
class PhotoStore(private val context: Context) {
    private val walls = File(context.filesDir, "walls")
    private val importFile = File(context.cacheDir, "import.jpg")

    /** Fixed destination for the camera app, so the capture survives the activity being recreated. */
    fun cameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return FileProvider.getUriForFile(context, AUTHORITY, File(dir, "capture.jpg"))
    }

    /** Decodes [uri] subsampled, applies its EXIF rotation and stores it as the working photo. */
    fun import(uri: Uri): Boolean {
        val bitmap = read(uri) ?: return false
        save(bitmap, importFile)
        return true
    }

    /** Decodes [uri] subsampled and upright, or null if it cannot be read. */
    fun read(uri: Uri): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (decoded == null) {
            null
        } else {
            val (w, h) = ImageMath.fitSize(decoded.width, decoded.height, MAX_SIDE)
            val matrix = Matrix().apply {
                postScale(w / decoded.width.toFloat(), h / decoded.height.toFloat())
                postRotate(rotation.toFloat())
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not import $uri", e)
        null
    }

    fun imported(maxSide: Int): Bitmap? = load(importFile.path, maxSide)

    /**
     * Keeps the part of the working photo inside [crop], straightened if its corners are not
     * square, and stores it as a wall photo. Returns its path.
     */
    fun cropAndSave(crop: CropQuad): String? = try {
        val source = BitmapFactory.decodeFile(importFile.path)
        if (source == null) {
            null
        } else {
            val rect = crop.asRect()
            val picture = if (rect != null) {
                val c = rect.normalized()
                val x = (c.left * source.width).toInt().coerceIn(0, source.width - 1)
                val y = (c.top * source.height).toInt().coerceIn(0, source.height - 1)
                val w = ((c.right - c.left) * source.width).toInt().coerceIn(1, source.width - x)
                val h = ((c.bottom - c.top) * source.height).toInt().coerceIn(1, source.height - y)
                Bitmap.createBitmap(source, x, y, w, h)
            } else {
                val argb = IntArray(source.width * source.height)
                source.getPixels(argb, 0, source.width, 0, 0, source.width, source.height)
                val straight = crop.straighten(PixelImage(source.width, source.height, argb))
                Bitmap.createBitmap(straight.argb, straight.width, straight.height, Bitmap.Config.ARGB_8888)
            }
            val file = File(walls, "${UUID.randomUUID()}.jpg")
            save(picture, file)
            file.path
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not crop the imported photo", e)
        null
    }

    /** Null when the file is missing or unreadable. */
    fun load(path: String, maxSide: Int): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    /**
     * Pixels of [bitmap] at the size detection runs on. Twice the detector's reference size:
     * small footholds are only a few pixels across at 640 px and get lost.
     */
    fun pixels(bitmap: Bitmap): PixelImage {
        val (w, h) = ImageMath.fitSize(bitmap.width, bitmap.height, DETECTION_SIDE)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val argb = IntArray(w * h)
        scaled.getPixels(argb, 0, w, 0, 0, w, h)
        return PixelImage(w, h, argb)
    }

    /** Stores [bitmap] where other apps can be given read access to it, and returns its address. */
    fun shareUri(bitmap: Bitmap): Uri {
        val file = File(File(context.cacheDir, "share"), "climb-tracker.jpg")
        save(bitmap, file)
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    fun delete(path: String) {
        File(path).delete()
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    companion object {
        const val AUTHORITY = "com.climbtracker.files"
        const val MAX_SIDE = 2048
        const val DETECTION_SIDE = 1280
        private const val TAG = "PhotoStore"
    }
}
