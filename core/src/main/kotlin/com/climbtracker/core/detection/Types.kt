package com.climbtracker.core.detection

/** Image as packed ARGB pixels, row by row. */
class PixelImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "argb size ${argb.size} != $width x $height" }
    }
}

/** Point in normalised image coordinates (0..1). */
data class PointF(val x: Float, val y: Float)

/** Bounding box in normalised image coordinates (0..1). */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class DetectedHold(
    val contour: List<PointF>,
    val bounds: Bounds,
    val center: PointF,
    val argb: Int,
    val colorGroup: Int,
    val area: Int,
)

data class ColorGroup(val index: Int, val argb: Int, val holdCount: Int)

data class DetectionResult(val holds: List<DetectedHold>, val groups: List<ColorGroup>)

interface HoldDetector {
    /** [sensitivity] in 0..1; 0.5 is the default, higher detects fainter holds. */
    fun detect(image: PixelImage, sensitivity: Float): DetectionResult

    /** Grows a hold from pixel ([x], [y]); null when no valid region results. colorGroup is -1. */
    fun growRegion(image: PixelImage, x: Int, y: Int): DetectedHold?
}
