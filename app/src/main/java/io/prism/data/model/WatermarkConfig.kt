package io.prism.data.model

data class WatermarkConfig(
    val mainText: String = "",
    val useExifData: Boolean = true,
    val exifSettings: ExifSettings = ExifSettings(),
    val mainTextFont: FontResource,
    val exifTextFont: FontResource,
    val position: WatermarkPosition = WatermarkPosition.BOTTOM,
    val logo: LogoResource?,
    val style: WatermarkStyle,
    val scalePercent: Float = 10f,
    val colorSettings: ColorSettings = ColorSettings()
) {
    companion object {
        const val MAX_TEXT_LENGTH = 45
        const val MIN_SCALE = 5f
        const val MAX_SCALE = 50f
        const val SCALE_STEP = 2.5f
        const val DEFAULT_SEPARATOR = " • "
    }
}

data class ExifSettings(
    val showDevice: Boolean = true,
    val showIso: Boolean = true,
    val showFocalLength: Boolean = true,
    val showAperture: Boolean = true,
    val showExposure: Boolean = true,
    val customDevice: String? = null,
    val customIso: String? = null,
    val customFocalLength: String? = null,
    val customAperture: String? = null,
    val customExposure: String? = null,
    val deviceInSameLine: Boolean = false,
    val separator: String = " • "
)

data class ColorSettings(
    val useCustomColors: Boolean = false,
    val backgroundColor: Int? = null,
    val mainTextColor: Int? = null,
    val exifTextColor: Int? = null
)