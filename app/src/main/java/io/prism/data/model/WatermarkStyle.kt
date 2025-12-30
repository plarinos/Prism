package io.prism.data.model

import androidx.annotation.LayoutRes
import androidx.annotation.StringRes

data class WatermarkStyle(
    val id: String,
    @StringRes val nameResId: Int = 0,
    val customName: String? = null,
    @LayoutRes val layoutResId: Int = 0,
    @StringRes val descriptionResId: Int? = null,
    val customDescription: String? = null,
    val customTemplateJson: String? = null,
    val isCustom: Boolean = false,
    val colorLocks: ColorLocks = ColorLocks()
) {
    val displayName: String
        get() = customName ?: ""

    val displayDescription: String?
        get() = customDescription

    val isThemeLocked: Boolean
        get() = colorLocks.backgroundColorLocked && colorLocks.mainTextColorLocked
}

data class ColorLocks(
    val backgroundColorLocked: Boolean = false,
    val mainTextColorLocked: Boolean = false,
    val exifTextColorLocked: Boolean = false,
    val forcedBackgroundColor: Int? = null,
    val forcedMainTextColor: Int? = null,
    val forcedExifTextColor: Int? = null
) {
    companion object {
        val NONE = ColorLocks()
    }
}