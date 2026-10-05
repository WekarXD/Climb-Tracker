package com.climbtracker

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.HoldDetector
import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.detection.Silhouettes
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the outline of holds with a segmentation model (MobileSAM) that runs on the phone. The
 * model is told where each hold is, as a box, and answers with its silhouette; it does not find
 * holds or know their colour. It is loaded from the app's assets for each photo and let go of
 * afterwards: kept in memory it takes over a gigabyte, and loading it is quick.
 */
class SilhouetteModel(private val context: Context) {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private fun open(asset: String): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Without the arena the memory of a run goes back to the system when it ends.
            setCPUArenaAllocator(false)
        }
        return environment.createSession(context.assets.open(asset).use { it.readBytes() }, options)
    }

    /** [holds] with the silhouettes the model draws for them on [image]. */
    @Synchronized
    fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> {
        // Holds of a few pixels are left as the detector drew them: there is no silhouette to gain.
        val (worth, small) = holds.partition { Silhouettes.worthRefining(it, image.width, image.height) }
        if (worth.isEmpty()) return holds
        val started = SystemClock.elapsedRealtime()
        return open(ENCODER).use { encode -> open(DECODER).use { decode -> refine(image, worth, encode, decode, started) + small } }
    }

    private fun refine(image: PixelImage, holds: List<DetectedHold>, encode: OrtSession, decode: OrtSession, started: Long): List<DetectedHold> {
        val loaded = SystemClock.elapsedRealtime()

        val frame = Silhouettes.FRAME
        val scale = frame / max(image.width, image.height).toFloat()
        val width = (image.width * scale).roundToInt().coerceIn(1, frame)
        val height = (image.height * scale).roundToInt().coerceIn(1, frame)
        val source = Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        // Red, green and blue planes of the frame; what the photo does not fill stays at zero.
        val plane = frame * frame
        val data = FloatArray(3 * plane)
        for (y in 0 until height) for (x in 0 until width) {
            val p = pixels[y * width + x]
            val i = y * frame + x
            data[i] = ((p shr 16) and 0xFF).toFloat()
            data[plane + i] = ((p shr 8) and 0xFF).toFloat()
            data[2 * plane + i] = (p and 0xFF).toFloat()
        }

        val out = ArrayList<DetectedHold>(holds.size)
        var encoded = 0L
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, frame.toLong(), frame.toLong())).use { input ->
            encode.run(mapOf("image" to input)).use { result ->
                encoded = SystemClock.elapsedRealtime()
                val embedding = result.get(0) as OnnxTensor
                val maskSize = Silhouettes.MASK * Silhouettes.MASK
                for (batch in holds.chunked(BATCH)) {
                    val boxes = FloatArray(batch.size * 4)
                    batch.forEachIndexed { i, hold -> Silhouettes.box(hold, image.width, image.height).copyInto(boxes, i * 4) }
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(boxes), longArrayOf(batch.size.toLong(), 4)).use { prompt ->
                        decode.run(mapOf("embedding" to embedding, "boxes" to prompt)).use { answer ->
                            val buffer = (answer.get(0) as OnnxTensor).floatBuffer
                            val masks = FloatArray(buffer.remaining())
                            buffer.get(masks)
                            batch.forEachIndexed { i, hold -> out += Silhouettes.refine(hold, masks, i * maskSize, image.width, image.height) }
                        }
                    }
                }
            }
        }
        val done = SystemClock.elapsedRealtime()
        Log.i(TAG, "silhouettes: load ${loaded - started} ms, encoder ${encoded - loaded} ms, ${holds.size} holds in ${done - encoded} ms, total ${done - started} ms")
        // Larger first, so that of two detections of one hold the fuller one is kept.
        return Silhouettes.dedupe(out.sortedByDescending { it.area })
    }

    private companion object {
        const val TAG = "ClimbTracker"
        const val ENCODER = "sam_encoder.onnx"
        const val DECODER = "sam_decoder.onnx"
        const val BATCH = 8
    }
}

/**
 * [base] finds the holds and their colours; [model] then redraws their outlines. If the model
 * cannot run on this phone, the holds keep the outlines of [base].
 */
class RefiningDetector(private val base: HoldDetector, private val model: SilhouetteModel) : HoldDetector {

    override fun detect(image: PixelImage, sensitivity: Float): DetectionResult {
        val found = base.detect(image, sensitivity)
        val holds = try {
            model.refine(image, found.holds)
        } catch (e: Throwable) {
            Log.w("ClimbTracker", "Silhouettes unavailable, keeping the detector's outlines", e)
            return found
        }
        val groups = found.groups.map { group -> group.copy(holdCount = holds.count { it.colorGroup == group.index }) }
        return DetectionResult(holds, groups.filter { it.holdCount > 0 })
    }

    override fun growRegion(image: PixelImage, x: Int, y: Int): DetectedHold? = base.growRegion(image, x, y)
}
