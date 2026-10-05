package com.climbtracker.core.detection

object TestImages {
    const val BEIGE = 0xFFE8E0D0.toInt()
    const val DARK = 0xFF303030.toInt()
    const val RED = 0xFFD02020.toInt()
    const val BLUE = 0xFF2060D0.toInt()
    const val YELLOW = 0xFFE8C020.toInt()
    const val BLACK = 0xFF151515.toInt()

    fun solid(width: Int, height: Int, argb: Int): PixelImage =
        PixelImage(width, height, IntArray(width * height) { argb })

    fun rect(image: PixelImage, x: Int, y: Int, w: Int, h: Int, argb: Int) {
        for (yy in y until y + h) for (xx in x until x + w) image.argb[yy * image.width + xx] = argb
    }
}
