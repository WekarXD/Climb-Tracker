package com.climbtracker.core.tracker

enum class GradeScale { FONT, V }

object Grades {
    val FONT: List<String> = listOf(
        "4", "4+", "5", "5+",
        "6A", "6A+", "6B", "6B+", "6C", "6C+",
        "7A", "7A+", "7B", "7B+", "7C", "7C+",
        "8A", "8A+", "8B", "8B+", "8C", "8C+",
    )

    val V: List<String> = (0..16).map { "V$it" }

    fun of(scale: GradeScale): List<String> = if (scale == GradeScale.FONT) FONT else V
}
