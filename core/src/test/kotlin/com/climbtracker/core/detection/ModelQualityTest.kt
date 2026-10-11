package com.climbtracker.core.detection

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * The same measure as [DetectionQualityTest], of what the app shows when it has the hold model:
 * holds found by the model, outlined by the segmentation one. Without the models in the app's
 * assets the tests are skipped.
 */
class ModelQualityTest : DetectionQualityTest() {

    override val stage: String get() = "model"

    // Measured: 226 of 230 holds and 47 false positives, 104 of 108 and 29 on the photos kept
    // aside. In the pictures looked at, much of what counts as false is real and was not annotated: route tags, holds of a
    // few pixels, black holds on black volumes.
    override val minColourRecall: Float get() = 0.90f
    override val minTotalRecall: Float get() = 0.96f
    override val maxFalsePositives: Int get() = 49
    override val minHeldOutRecall: Float get() = 0.94f
    override val maxHeldOutFalsePositives: Int get() = 31

    private val assets = File("../app/src/main/assets")
    private val holds = File(assets, "hold_detector.onnx")
    private val encoder = File(assets, "sam_encoder.onnx")
    private val decoder = File(assets, "sam_decoder.onnx")

    override fun detect(image: PixelImage): List<DetectedHold> {
        assumeTrue(holds.exists() && encoder.exists() && decoder.exists(), "the models are not in app/src/main/assets")
        val detector = ModelHoldDetector(
            HoldModelRunner({ holds.readBytes() }),
            SilhouetteRunner({ encoder.readBytes() }, { decoder.readBytes() }),
            ColorHoldDetector(),
        )
        return detector.detect(image, 0.5f).holds
    }
}
