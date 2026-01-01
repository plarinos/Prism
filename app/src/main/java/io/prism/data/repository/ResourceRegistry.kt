package io.prism.data.repository

import android.graphics.Color
import io.prism.R
import io.prism.data.model.ColorLocks
import io.prism.data.model.FontResource
import io.prism.data.model.LogoResource
import io.prism.data.model.WatermarkStyle

object ResourceRegistry {

    val fonts: List<FontResource> = listOf(
        FontResource(
            id = "roboto_regular",
            nameResId = R.string.font_roboto,
            fontResId = R.font.roboto_regular
        ),
        FontResource(
            id = "roboto_bold",
            nameResId = R.string.font_roboto_bold,
            fontResId = R.font.roboto_bold
        ),
        FontResource(
            id = "playfair",
            nameResId = R.string.font_playfair,
            fontResId = R.font.playfair_display
        ),
        FontResource(
            id = "playfair_bold",
            nameResId = R.string.font_playfair_bold,
            fontResId = R.font.playfairdisplay_bold
        ),
        FontResource(
            id = "badscript_regular",
            nameResId = R.string.badscript_regular,
            fontResId = R.font.badscript_regular
        ),
        FontResource(
            id = "oswald",
            nameResId = R.string.font_oswald,
            fontResId = R.font.oswald_regular
        ),
        FontResource(
            id = "jetbrains_mono",
            nameResId = R.string.font_jetbrains,
            fontResId = R.font.jetbrains_mono
        ),
        FontResource(
            id = "jetbrains_mono_bold",
            nameResId = R.string.font_jetbrains_bold,
            fontResId = R.font.jetbrains_mono_bold
        ),
        FontResource(
            id = "pacifico_regular",
            nameResId = R.string.pacifico_regular,
            fontResId = R.font.pacifico_regular
        )
    )

    val defaultFont: FontResource = fonts.find { it.id == "roboto_regular" } ?: fonts.first()

    val builtInLogos: List<LogoResource> = listOf(
        LogoResource.NO_LOGO.copy(nameResId = R.string.logo_none),
        LogoResource(
            id = "prism",
            nameResId = R.string.logo_prism,
            drawableResId = R.drawable.logo_prism,
            isMonochrome = false
        )
    )

    val defaultLogo: LogoResource = builtInLogos.find { it.id == "prism" } ?: builtInLogos[1]

    val styles: List<WatermarkStyle> = listOf(
        WatermarkStyle(
            id = "standard",
            nameResId = R.string.style_standard,
            layoutResId = R.layout.watermark_standard,
            descriptionResId = R.string.style_standard_desc
        ),
        WatermarkStyle(
            id = "minimal",
            nameResId = R.string.style_minimal,
            layoutResId = R.layout.watermark_minimal,
            descriptionResId = R.string.style_minimal_desc
        ),
        WatermarkStyle(
            id = "classic",
            nameResId = R.string.style_classic,
            layoutResId = R.layout.watermark_classic,
            descriptionResId = R.string.style_classic_desc
        ),
        WatermarkStyle(
            id = "classic_v2",
            nameResId = R.string.style_classic_v2,
            layoutResId = R.layout.watermark_classic_v2,
            descriptionResId = R.string.style_classic_v2_desc
        ),
        WatermarkStyle(
            id = "center",
            nameResId = R.string.style_center,
            layoutResId = R.layout.watermark_center,
            descriptionResId = R.string.style_center_desc
        ),
    )

    val defaultStyle: WatermarkStyle = styles.find { it.id == "classic" } ?: styles.first()

    fun findFontById(id: String): FontResource? = fonts.find { it.id == id }
    fun findLogoById(id: String): LogoResource? = builtInLogos.find { it.id == id }
    fun findStyleById(id: String): WatermarkStyle? = styles.find { it.id == id }
}