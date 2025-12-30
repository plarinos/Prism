package io.prism.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import io.prism.R
import io.prism.data.model.ExifData
import io.prism.data.model.WatermarkConfig
import io.prism.data.model.WatermarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object WatermarkRenderer {

    private const val BASE_LOGO_SIZE_DP = 48f
    private const val BASE_TEXT_SIZE_SP = 18f
    private const val BASE_EXIF_TEXT_SIZE_SP = 12f
    private const val BASE_DEVICE_TEXT_SIZE_SP = 14f
    private const val BASE_PADDING_DP = 24f
    private const val BASE_MARGIN_DP = 16f
    private const val BASE_DIVIDER_WIDTH_DP = 2f
    private const val BASE_DIVIDER_HEIGHT_DP = 40f

    suspend fun renderWatermark(
        context: Context,
        config: WatermarkConfig,
        exifData: ExifData,
        imageWidth: Int,
        imageHeight: Int,
        theme: WatermarkTheme
    ): Bitmap = withContext(Dispatchers.Main) {
        val style = config.style

        val layoutResId = when {
            style.isCustom && !style.customTemplateJson.isNullOrBlank() -> {
                getLayoutFromCustomTemplate(style.customTemplateJson)
            }
            style.layoutResId != 0 -> style.layoutResId
            else -> R.layout.watermark_standard
        }

        renderWithLayout(context, layoutResId, config, exifData, imageWidth, imageHeight, theme)
    }

    private fun getLayoutFromCustomTemplate(templateJson: String): Int {
        return try {
            val json = JSONObject(templateJson)
            val layoutName = json.optString("layout", "standard")
            when (layoutName.lowercase()) {
                "minimal" -> R.layout.watermark_minimal
                "classic" -> R.layout.watermark_classic
                "standard" -> R.layout.watermark_standard
                else -> R.layout.watermark_standard
            }
        } catch (e: Exception) {
            e.printStackTrace()
            R.layout.watermark_standard
        }
    }

    private suspend fun renderWithLayout(
        context: Context,
        layoutResId: Int,
        config: WatermarkConfig,
        exifData: ExifData,
        imageWidth: Int,
        imageHeight: Int,
        theme: WatermarkTheme
    ): Bitmap = withContext(Dispatchers.Main) {
        val inflater = LayoutInflater.from(context)
        val watermarkView = inflater.inflate(layoutResId, null) as ViewGroup

        val scaleFactor = calculateScaleFactor(context, imageWidth, imageHeight, config.scalePercent)

        configureView(context, watermarkView, config, exifData, scaleFactor, theme)
        measureAndLayoutView(watermarkView, imageWidth)
        renderViewToBitmap(watermarkView)
    }

    private fun calculateScaleFactor(
        context: Context,
        imageWidth: Int,
        imageHeight: Int,
        scalePercent: Float
    ): Float {
        val density = context.resources.displayMetrics.density
        val referenceSize = imageHeight
        val targetSize = referenceSize * scalePercent / 100f
        val baseSize = 100f * density
        return (targetSize / baseSize).coerceIn(0.5f, 10f)
    }

    private fun configureView(
        context: Context,
        view: ViewGroup,
        config: WatermarkConfig,
        exifData: ExifData,
        scaleFactor: Float,
        theme: WatermarkTheme
    ) {
        val density = context.resources.displayMetrics.density
        val colorLocks = config.style.colorLocks
        val useCustom = config.colorSettings.useCustomColors

        val bgColor = when {
            colorLocks.backgroundColorLocked && colorLocks.forcedBackgroundColor != null -> {
                colorLocks.forcedBackgroundColor
            }
            useCustom && config.colorSettings.backgroundColor != null -> {
                config.colorSettings.backgroundColor
            }
            else -> {
                ContextCompat.getColor(context, theme.backgroundColor)
            }
        }

        val textColor = when {
            colorLocks.mainTextColorLocked && colorLocks.forcedMainTextColor != null -> {
                colorLocks.forcedMainTextColor
            }
            useCustom && config.colorSettings.mainTextColor != null -> {
                config.colorSettings.mainTextColor
            }
            else -> {
                ContextCompat.getColor(context, theme.textColor)
            }
        }

        val exifTextColor = when {
            colorLocks.exifTextColorLocked && colorLocks.forcedExifTextColor != null -> {
                colorLocks.forcedExifTextColor
            }
            useCustom && config.colorSettings.exifTextColor != null -> {
                config.colorSettings.exifTextColor
            }
            else -> {
                ContextCompat.getColor(context, theme.textColor)
            }
        }

        view.findViewById<View>(R.id.watermarkBG)?.apply {
            setBackgroundColor(bgColor)
            val scaledPadding = (BASE_PADDING_DP * density * scaleFactor).toInt()
            setPadding(scaledPadding, scaledPadding, scaledPadding, scaledPadding)
        }

        view.findViewById<ImageView>(R.id.watermarkLogo)?.apply {
            val logo = config.logo

            if (logo == null || logo.isNoLogo) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE

                when {
                    logo.isCustom && !logo.customLogoPath.isNullOrEmpty() -> {
                        val file = File(logo.customLogoPath)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            setImageBitmap(bitmap)
                        }
                    }
                    logo.drawableResId != 0 -> {
                        setImageResource(logo.drawableResId)
                    }
                    else -> {
                        visibility = View.GONE
                    }
                }

                if (visibility == View.VISIBLE && logo.isMonochrome) {
                    setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
                } else {
                    colorFilter = null
                }

                if (visibility == View.VISIBLE) {
                    val scaledLogoSize = (BASE_LOGO_SIZE_DP * density * scaleFactor).toInt()
                    layoutParams = layoutParams?.apply {
                        width = scaledLogoSize
                        height = scaledLogoSize
                    } ?: LinearLayout.LayoutParams(scaledLogoSize, scaledLogoSize)

                    (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                        val scaledMargin = (BASE_MARGIN_DP * density * scaleFactor).toInt()
                        marginEnd = scaledMargin
                    }
                }
            }
        }

        view.findViewWithTag<View>("divider")?.apply {
            val hasLogo = config.logo != null && !config.logo.isNoLogo
            visibility = if (hasLogo) View.VISIBLE else View.GONE

            if (visibility == View.VISIBLE) {
                setBackgroundColor(textColor)
                alpha = 0.3f
                val scaledWidth = (BASE_DIVIDER_WIDTH_DP * density * scaleFactor).toInt()
                val scaledHeight = (BASE_DIVIDER_HEIGHT_DP * density * scaleFactor).toInt()
                layoutParams = layoutParams?.apply {
                    width = scaledWidth
                    height = scaledHeight
                }

                (layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    val scaledMargin = (BASE_MARGIN_DP * density * scaleFactor).toInt()
                    marginEnd = scaledMargin
                }
            }
        }

        view.findViewById<TextView>(R.id.watermarkText)?.apply {
            if (config.mainText.isBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = config.mainText
                setTextColor(textColor)

                val scaledTextSize = BASE_TEXT_SIZE_SP * scaleFactor
                setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledTextSize)

                typeface = if (config.mainTextFont.fontResId != 0) {
                    try {
                        ResourcesCompat.getFont(context, config.mainTextFont.fontResId)
                    } catch (e: Exception) {
                        Typeface.DEFAULT_BOLD
                    }
                } else {
                    Typeface.DEFAULT_BOLD
                }
            }
        }

        view.findViewById<TextView>(R.id.watermarkDeviceText)?.apply {
            val deviceName = if (config.useExifData) {
                exifData.formatDeviceSeparate(config.exifSettings)
            } else {
                ""
            }

            if (deviceName.isNotBlank()) {
                text = deviceName
                visibility = View.VISIBLE
                setTextColor(textColor)

                val scaledTextSize = BASE_DEVICE_TEXT_SIZE_SP * scaleFactor
                setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledTextSize)

                typeface = if (config.exifTextFont.fontResId != 0) {
                    try {
                        ResourcesCompat.getFont(context, config.exifTextFont.fontResId)
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                } else {
                    Typeface.DEFAULT
                }
            } else {
                visibility = View.GONE
            }
        }

        view.findViewById<TextView>(R.id.watermarkEXIFDataText)?.apply {
            val exifText = if (config.useExifData) {
                exifData.formatExifOnly(config.exifSettings).ifBlank { "" }
            } else {
                ""
            }

            if (exifText.isNotBlank()) {
                text = exifText
                visibility = View.VISIBLE
                setTextColor(exifTextColor)
                alpha = 0.7f

                val scaledTextSize = BASE_EXIF_TEXT_SIZE_SP * scaleFactor
                setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledTextSize)

                typeface = if (config.exifTextFont.fontResId != 0) {
                    try {
                        ResourcesCompat.getFont(context, config.exifTextFont.fontResId)
                    } catch (e: Exception) {
                        Typeface.DEFAULT
                    }
                } else {
                    Typeface.DEFAULT
                }
            } else {
                visibility = View.GONE
            }
        }

        scaleViewHierarchy(view, scaleFactor, density)
    }

    private fun scaleViewHierarchy(view: View, scaleFactor: Float, density: Float) {
        if (view is ViewGroup) {
            for (child in view.children) {
                scaleViewHierarchy(child, scaleFactor, density)
            }
        }

        when (view.id) {
            R.id.watermarkBG,
            R.id.watermarkLogo,
            R.id.watermarkText,
            R.id.watermarkEXIFDataText,
            R.id.watermarkDeviceText -> return
        }

        if (view.tag == "divider" || view.tag == "divider_horizontal") return

        val currentPadding = view.paddingLeft + view.paddingTop + view.paddingRight + view.paddingBottom
        if (currentPadding > 0) {
            view.setPadding(
                (view.paddingLeft * scaleFactor).toInt(),
                (view.paddingTop * scaleFactor).toInt(),
                (view.paddingRight * scaleFactor).toInt(),
                (view.paddingBottom * scaleFactor).toInt()
            )
        }

        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            if (leftMargin + topMargin + rightMargin + bottomMargin > 0) {
                leftMargin = (leftMargin * scaleFactor).toInt()
                topMargin = (topMargin * scaleFactor).toInt()
                rightMargin = (rightMargin * scaleFactor).toInt()
                bottomMargin = (bottomMargin * scaleFactor).toInt()
            }
        }
    }

    private fun measureAndLayoutView(view: View, imageWidth: Int) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(imageWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private suspend fun renderViewToBitmap(view: View): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bitmap).also { view.draw(it) }
        bitmap
    }
}