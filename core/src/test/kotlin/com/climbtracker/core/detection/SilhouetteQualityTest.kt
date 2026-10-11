package com.climbtracker.core.detection

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * The same measure as [DetectionQualityTest], taken after the segmentation model has redrawn the
 * outlines and the duplicates it reveals have been dropped: what the app actually shows. The
 * models are the ones the app build downloads; without them the tests are skipped.
 */
class SilhouetteQualityTest : DetectionQualityTest() {

    override val stage: String get() = "silhouettes"

    // Measured: 190 of 230 holds and 59 false positives, 90 of 108 and 26 on the photos kept
    // aside. A few holds fewer than the detector alone, where an outline that took in two holds
    // is redrawn around one of them.
    override val minTotalRecall: Float get() = 0.81f
    override val minHeldOutRecall: Float get() = 0.81f

    private val encoder = File("../app/src/main/assets/sam_encoder.onnx")
    private val decoder = File("../app/src/main/assets/sam_decoder.onnx")

    override fun refine(image: PixelImage, holds: List<DetectedHold>): List<DetectedHold> {
        assumeTrue(encoder.exists() && decoder.exists(), "the segmentation models are not in app/src/main/assets")
        return SilhouetteRunner({ encoder.readBytes() }, { decoder.readBytes() }).refine(image, holds)
    }
}
