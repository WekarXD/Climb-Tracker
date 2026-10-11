package com.climbtracker.core.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/** A hold as the model reports it: its box in pixels of the photo, how sure it is of it, and whether it is a volume. */
class HoldBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float, val volume: Boolean) {
    val area: Float get() = (right - left) * (bottom - top)
}

/** What the hold model answers, turned into boxes on the photo. */
object HoldBoxes {
    /** Side of the square frame the model takes. */
    const val FRAME = 960

    /** Values the model gives for each candidate: centre x and y, width, height, objectness, hold, volume. */
    const val VALUES = 7

    /** Least score for a box to count: 0.4 at the default sensitivity of 0.5, lower for fainter holds. */
    fun minScore(sensitivity: Float): Float = 0.7f - 0.6f * sensitivity.coerceIn(0f, 1f)

    /**
     * The candidates in [output] that reach [minScore], as boxes on a photo of [width] x
     * [height] that was scaled into the frame of the model by [scale]. A candidate's score is how
     * sure the model is that something is there times how sure it is of what it is.
     */
    fun decode(output: FloatArray, scale: Float, minScore: Float, width: Int, height: Int): List<HoldBox> {
        val boxes = ArrayList<HoldBox>()
        var i = 0
        while (i + VALUES <= output.size) {
            val hold = output[i + 5]
            val volume = output[i + 6]
            val score = output[i + 4] * max(hold, volume)
            if (score >= minScore) {
                val halfW = output[i + 2] / 2
                val halfH = output[i + 3] / 2
                val left = ((output[i] - halfW) / scale).coerceIn(0f, width.toFloat())
                val top = ((output[i + 1] - halfH) / scale).coerceIn(0f, height.toFloat())
                val right = ((output[i] + halfW) / scale).coerceIn(0f, width.toFloat())
                val bottom = ((output[i + 1] + halfH) / scale).coerceIn(0f, height.toFloat())
                if (right - left >= MIN_SIDE && bottom - top >= MIN_SIDE) boxes += HoldBox(left, top, right, bottom, score, volume > hold)
            }
            i += VALUES
        }
        return boxes
    }

    /**
     * [boxes] without the repeats: the model proposes each hold several times over. Of two
     * boxes that overlap by more than [maxOverlap] of their union, the surer one stays.
     */
    fun suppress(boxes: List<HoldBox>, maxOverlap: Float = 0.5f): List<HoldBox> {
        val kept = ArrayList<HoldBox>()
        for (box in boxes.sortedByDescending { it.score }) {
            if (kept.none { overlap(it, box) > maxOverlap }) kept += box
        }
        return kept
    }

    private fun overlap(a: HoldBox, b: HoldBox): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        val both = w * h
        return both / (a.area + b.area - both)
    }

    private const val MIN_SIDE = 2f
}

/**
 * Runs the model that finds holds and volumes: a detector trained on photos of climbing walls,
 * which answers with boxes. It does not draw outlines or know colours. [model] hands the model
 * over; like the segmentation one it is loaded for each photo and let go of afterwards.
 */
class HoldModelRunner(private val model: () -> ByteArray, private val log: (String) -> Unit = {}) {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    /** The holds on [image], more of them and less sure the higher [sensitivity] is. */
    @Synchronized
    fun find(image: PixelImage, sensitivity: Float): List<HoldBox> {
        val started = System.nanoTime()
        val frame = HoldBoxes.FRAME
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(min(4, Runtime.getRuntime().availableProcessors()))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setCPUArenaAllocator(false)
        }
        val output = environment.createSession(model(), options).use { session ->
            // The model was trained on pictures with blue first and grey around them.
            val data = Frames.planes(image, frame, blueFirst = true, fill = PADDING)
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), longArrayOf(1, 3, frame.toLong(), frame.toLong())).use { input ->
                session.run(mapOf(session.inputNames.first() to input)).use { result ->
                    val buffer = (result.get(0) as OnnxTensor).floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }
        }
        val scale = frame / max(image.width, image.height).toFloat()
        val boxes = HoldBoxes.suppress(HoldBoxes.decode(output, scale, HoldBoxes.minScore(sensitivity), image.width, image.height))
        log("hold model: ${boxes.size} holds in ${(System.nanoTime() - started) / 1_000_000} ms")
        return boxes
    }

    private companion object {
        const val PADDING = 114f
    }
}
