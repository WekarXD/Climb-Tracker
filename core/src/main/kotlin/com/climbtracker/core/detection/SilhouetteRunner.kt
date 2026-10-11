package com.climbtracker.core.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Runs the segmentation model (MobileSAM) that draws the outline of holds. The model is told
 * where each hold is, as a box, and answers with its silhouette; it does not find holds or know
 * their colour. [encoder] and [decoder] hand over the two parts of the model, which are loaded
 * for each photo and let go of afterwards: kept in memory they take over a gigabyte, and loading
 * them is quick.
 *
 * It only needs the runtime, not a phone, so the tests measure it on the reference photos.
 */
class SilhouetteRunner(
    private val encoder: () -> ByteArray,
    private val decoder: () -> ByteArray,
    private val log: (String) -> Unit = {},
) {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private fun open(model: ByteArray): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // Without the arena the memory of a run goes back to the system when it ends.
            setCPUArenaAllocator(false)
        }
        return environment.createSession(model, options)
    }

    /** [holds] with the silhouettes the model draws for them on [image]. */
    @Synchronized
    fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> {
        // Holds of a few pixels are left as the detector drew them: there is no silhouette to gain.
        val (worth, small) = holds.partition { Silhouettes.worthRefining(it, image.width, image.height) }
        if (worth.isEmpty()) return holds
        val started = System.nanoTime()
        return open(encoder()).use { encode -> open(decoder()).use { decode -> refine(image, worth, encode, decode, started) + small } }
    }

    private fun refine(image: PixelImage, holds: List<DetectedHold>, encode: OrtSession, decode: OrtSession, started: Long): List<DetectedHold> {
        val loaded = System.nanoTime()
        val frame = Silhouettes.FRAME
        val data = frame(image)

        val out = ArrayList<DetectedHold>(holds.size)
        var encoded = 0L
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, frame.toLong(), frame.toLong())).use { input ->
            encode.run(mapOf("image" to input)).use { result ->
                encoded = System.nanoTime()
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
        val done = System.nanoTime()
        log("silhouettes: load ${millis(started, loaded)} ms, encoder ${millis(loaded, encoded)} ms, ${holds.size} holds in ${millis(encoded, done)} ms, total ${millis(started, done)} ms")
        // Larger first, so that of two detections of one hold the fuller one is kept.
        return Silhouettes.dedupe(out.sortedByDescending { it.area })
    }

    private fun millis(from: Long, to: Long) = (to - from) / 1_000_000

    companion object {
        private const val BATCH = 8

        /**
         * Red, green and blue planes of the model's frame, with [image] scaled into its top left
         * corner; what the photo does not fill stays at zero.
         */
        fun frame(image: PixelImage): FloatArray = Frames.planes(image, Silhouettes.FRAME)
    }
}
