package com.climbtracker

import android.content.Context
import android.util.Log
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.HoldDetector
import com.climbtracker.core.detection.PixelImage
import com.climbtracker.core.detection.SilhouetteRunner

/**
 * Draws the outline of holds with the segmentation model kept in the app's assets, which runs
 * on the phone. See [SilhouetteRunner].
 */
class SilhouetteModel(private val context: Context) {
    private val runner = SilhouetteRunner(
        encoder = { asset(ENCODER) },
        decoder = { asset(DECODER) },
        log = { Log.i(TAG, it) },
    )

    private fun asset(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }

    /** [holds] with the silhouettes the model draws for them on [image]. */
    fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> = runner.refine(image, holds)

    private companion object {
        const val TAG = "ClimbTracker"
        const val ENCODER = "sam_encoder.onnx"
        const val DECODER = "sam_decoder.onnx"
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
