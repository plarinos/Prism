package io.prism.data.model

data class ExifData(
    val make: String? = null,
    val model: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val exposureTime: String? = null,
    val dateTime: String? = null
) {
    fun formatForWatermark(settings: ExifSettings = ExifSettings()): String {
        val separator = settings.separator.ifBlank { " • " }
        val parts = mutableListOf<String>()

        if (settings.showDevice && settings.deviceInSameLine) {
            val deviceName = settings.customDevice ?: buildDeviceName()
            if (deviceName.isNotBlank()) {
                parts.add(deviceName)
            }
        }

        val techSpecs = buildTechSpecs(settings, separator)
        if (techSpecs.isNotBlank()) {
            parts.add(techSpecs)
        }

        return parts.joinToString(separator)
    }

    fun formatDeviceSeparate(settings: ExifSettings = ExifSettings()): String {
        if (!settings.showDevice || settings.deviceInSameLine) return ""
        return settings.customDevice ?: buildDeviceName()
    }

    fun formatExifOnly(settings: ExifSettings = ExifSettings()): String {
        val separator = settings.separator.ifBlank { " • " }
        val parts = mutableListOf<String>()

        if (settings.showDevice && settings.deviceInSameLine) {
            val deviceName = settings.customDevice ?: buildDeviceName()
            if (deviceName.isNotBlank()) {
                parts.add(deviceName)
            }
        }

        val techSpecs = buildTechSpecs(settings, separator)
        if (techSpecs.isNotBlank()) {
            if (parts.isNotEmpty()) {
                return parts.joinToString(separator) + separator + techSpecs
            }
            return techSpecs
        }

        return parts.joinToString(separator)
    }

    fun getDeviceName(): String = buildDeviceName()

    private fun buildDeviceName(): String {
        return when {
            !make.isNullOrBlank() && !model.isNullOrBlank() -> {
                val modelUpper = model.uppercase()
                val makeUpper = make.uppercase()
                if (modelUpper.contains(makeUpper) || modelUpper.startsWith(makeUpper)) {
                    model
                } else {
                    "$make $model"
                }
            }
            !model.isNullOrBlank() -> model
            !make.isNullOrBlank() -> make
            else -> ""
        }
    }

    private fun buildTechSpecs(settings: ExifSettings, separator: String): String {
        val specs = mutableListOf<String>()

        if (settings.showIso) {
            val isoValue = settings.customIso ?: iso
            isoValue?.let {
                if (it.isNotBlank()) specs.add("ISO $it")
            }
        }

        if (settings.showFocalLength) {
            val focalValue = settings.customFocalLength ?: focalLength
            focalValue?.let {
                if (it.isNotBlank()) specs.add("${it}mm")
            }
        }

        if (settings.showAperture) {
            val apertureValue = settings.customAperture ?: aperture
            apertureValue?.let {
                if (it.isNotBlank()) specs.add("f/$it")
            }
        }

        if (settings.showExposure) {
            val exposureValue = settings.customExposure ?: exposureTime
            exposureValue?.let {
                if (it.isNotBlank()) specs.add("${it}s")
            }
        }

        return specs.joinToString(separator)
    }

    companion object {
        val EMPTY = ExifData()
    }
}