package com.climbtracker

import android.content.Context
import android.util.Log
import com.climbtracker.core.detection.ColorHoldDetector
import com.climbtracker.core.detection.HoldDetector
import com.climbtracker.core.detection.HoldModelRunner
import com.climbtracker.core.detection.ModelHoldDetector
import com.climbtracker.core.detection.SilhouetteRunner

/**
 * The detector of the app, with the models kept in its assets, which run on the phone: one finds
 * the holds and another draws their outlines. See [ModelHoldDetector]. Where a model is missing
 * or cannot run on this phone, the colour detector does its part.
 */
object Models {
    private const val TAG = "ClimbTracker"
    private const val HOLDS = "hold_detector.onnx"
    private const val ENCODER = "sam_encoder.onnx"
    private const val DECODER = "sam_decoder.onnx"

    fun detector(context: Context): HoldDetector {
        fun asset(name: String): ByteArray = context.assets.open(name).use { it.readBytes() }
        val log: (String) -> Unit = { Log.i(TAG, it) }
        return ModelHoldDetector(
            model = HoldModelRunner({ asset(HOLDS) }, log),
            silhouettes = SilhouetteRunner({ asset(ENCODER) }, { asset(DECODER) }, log),
            fallback = ColorHoldDetector(),
            log = log,
        )
    }
}
