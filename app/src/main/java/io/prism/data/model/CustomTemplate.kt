package io.prism.data.model

import android.graphics.Color
import org.json.JSONObject
import java.util.UUID

data class CustomTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val templateJson: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toWatermarkStyle(): WatermarkStyle {
        val colorLocks = parseColorLocks(templateJson)

        return WatermarkStyle(
            id = id,
            customName = name,
            customDescription = description,
            customTemplateJson = templateJson,
            isCustom = true,
            colorLocks = colorLocks
        )
    }

    companion object {
        fun parseColorLocks(json: String): ColorLocks {
            return try {
                val obj = JSONObject(json)
                val locksObj = obj.optJSONObject("lockColors") ?: return ColorLocks.NONE

                val bgColorStr = locksObj.optString("background", "").takeIf { it.isNotBlank() }
                val textColorStr = locksObj.optString("mainText", "").takeIf { it.isNotBlank() }
                val exifColorStr = locksObj.optString("exifText", "").takeIf { it.isNotBlank() }

                ColorLocks(
                    backgroundColorLocked = bgColorStr != null,
                    mainTextColorLocked = textColorStr != null,
                    exifTextColorLocked = exifColorStr != null,
                    forcedBackgroundColor = bgColorStr?.let { parseColor(it) },
                    forcedMainTextColor = textColorStr?.let { parseColor(it) },
                    forcedExifTextColor = exifColorStr?.let { parseColor(it) }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                ColorLocks.NONE
            }
        }

        private fun parseColor(colorStr: String): Int? {
            return try {
                Color.parseColor(if (colorStr.startsWith("#")) colorStr else "#$colorStr")
            } catch (e: Exception) {
                null
            }
        }
    }
}