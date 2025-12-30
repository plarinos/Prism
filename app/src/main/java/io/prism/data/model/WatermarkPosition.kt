package io.prism.data.model

enum class WatermarkPosition {
    TOP,
    BOTTOM;

    fun isHorizontal(): Boolean = true
}