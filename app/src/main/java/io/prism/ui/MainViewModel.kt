package io.prism.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.prism.data.model.ColorSettings
import io.prism.data.model.ExifData
import io.prism.data.model.ExifSettings
import io.prism.data.model.FontResource
import io.prism.data.model.LogoResource
import io.prism.data.model.WatermarkConfig
import io.prism.data.model.WatermarkPosition
import io.prism.data.model.WatermarkStyle
import io.prism.data.model.WatermarkTheme
import io.prism.data.repository.ResourceRegistry
import io.prism.data.repository.WatermarkRepository
import io.prism.utils.BitmapUtils
import io.prism.utils.ExifUtils
import io.prism.utils.ImageSaver
import io.prism.utils.WatermarkRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MainUiState(
    val selectedImageUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val actualImageWidth: Int = 0,
    val actualImageHeight: Int = 0,
    val exifData: ExifData = ExifData.EMPTY,
    val config: WatermarkConfig,
    val watermarkTheme: WatermarkTheme = WatermarkTheme.LIGHT,
    val allLogos: List<LogoResource> = emptyList(),
    val allStyles: List<WatermarkStyle> = emptyList(),
    val isProcessing: Boolean = false,
    val isLoadingImage: Boolean = false,
    val processingProgress: String = "",
    val savedImageUri: Uri? = null,
    val error: String? = null,
    val previewWatermarkBitmap: Bitmap? = null,
    val saveSettings: Boolean = false
) {
    val isReadyToProcess: Boolean
        get() = selectedImageUri != null &&
                previewBitmap != null &&
                actualImageWidth > 0 &&
                actualImageHeight > 0 &&
                !isProcessing &&
                !isLoadingImage
}

enum class BottomSheetType {
    TEXT,
    STYLE,
    LOGO,
    POSITION,
    SCALE,
    THEME,
    FONTS,
    EXIF,
    COLORS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WatermarkRepository(application)
    private val prefs = application.getSharedPreferences("user_settings", Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MainUiState(config = repository.getDefaultConfig())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val fonts: List<FontResource> = repository.getAllFonts()
    val positions: List<WatermarkPosition> = repository.getAllPositions()

    init {
        val saved = prefs.getBoolean("save_settings", false)
        _uiState.update { it.copy(saveSettings = saved) }
        if (saved) {
            loadSavedConfig()
        }
        loadLogosAndStyles()
    }

    private fun loadLogosAndStyles() {
        viewModelScope.launch {
            val logos = repository.getAllLogos()
            val styles = repository.getAllStyles()

            _uiState.update {
                it.copy(
                    allLogos = logos,
                    allStyles = styles
                )
            }
        }
    }

    fun refreshLogosAndStyles() {
        loadLogosAndStyles()
    }

    private fun updateConfigAndRefresh(
        refreshPreview: Boolean = true,
        block: (WatermarkConfig) -> WatermarkConfig
    ) {
        _uiState.update { state ->
            val newConfig = block(state.config)
            state.copy(config = newConfig)
        }
        if (refreshPreview) {
            updateWatermarkPreview()
        }
        saveConfigIfNeeded()
    }

    private fun saveConfigIfNeeded() {
        val state = _uiState.value
        if (!state.saveSettings) return

        val config = state.config
        val json = JSONObject().apply {
            put("mainText", config.mainText)
            put("useExifData", config.useExifData)
            put("scalePercent", config.scalePercent)
            put("position", config.position.name)
            put("watermarkTheme", state.watermarkTheme.name)

            put("mainFontId", config.mainTextFont.id)
            put("exifFontId", config.exifTextFont.id)

            put("logoId", config.logo?.takeIf { !it.isCustom }?.id ?: "")
            put("styleId", config.style.takeIf { !it.isCustom }?.id ?: "")

            val exif = JSONObject().apply {
                put("showDevice", config.exifSettings.showDevice)
                put("showIso", config.exifSettings.showIso)
                put("showFocalLength", config.exifSettings.showFocalLength)
                put("showAperture", config.exifSettings.showAperture)
                put("showExposure", config.exifSettings.showExposure)
                put("customDevice", config.exifSettings.customDevice ?: "")
                put("customIso", config.exifSettings.customIso ?: "")
                put("customFocalLength", config.exifSettings.customFocalLength ?: "")
                put("customAperture", config.exifSettings.customAperture ?: "")
                put("customExposure", config.exifSettings.customExposure ?: "")
                put("deviceInSameLine", config.exifSettings.deviceInSameLine)
                put("separator", config.exifSettings.separator)
            }
            put("exifSettings", exif)

            val colors = JSONObject().apply {
                put("useCustomColors", config.colorSettings.useCustomColors)
                put("backgroundColor", config.colorSettings.backgroundColor ?: 0)
                put("mainTextColor", config.colorSettings.mainTextColor ?: 0)
                put("exifTextColor", config.colorSettings.exifTextColor ?: 0)
            }
            put("colorSettings", colors)
        }

        prefs.edit().putString("saved_config", json.toString()).apply()
    }

    private fun loadSavedConfig() {
        val jsonString = prefs.getString("saved_config", null) ?: return
        try {
            val obj = JSONObject(jsonString)
            val default = repository.getDefaultConfig()

            val mainText = obj.optString("mainText", default.mainText)
            val useExifData = obj.optBoolean("useExifData", default.useExifData)
            val scalePercent = obj.optDouble("scalePercent", default.scalePercent.toDouble()).toFloat()
            val positionName = obj.optString("position", default.position.name)
            val themeName = obj.optString("watermarkTheme", WatermarkTheme.LIGHT.name)

            val mainFontId = obj.optString("mainFontId", default.mainTextFont.id)
            val exifFontId = obj.optString("exifFontId", default.exifTextFont.id)
            val logoId = obj.optString("logoId", "")
            val styleId = obj.optString("styleId", "")

            val exifObj = obj.optJSONObject("exifSettings")
            val exifSettings = if (exifObj != null) {
                ExifSettings(
                    showDevice = exifObj.optBoolean("showDevice", true),
                    showIso = exifObj.optBoolean("showIso", true),
                    showFocalLength = exifObj.optBoolean("showFocalLength", true),
                    showAperture = exifObj.optBoolean("showAperture", true),
                    showExposure = exifObj.optBoolean("showExposure", true),
                    customDevice = exifObj.optString("customDevice", "").takeIf { it.isNotBlank() },
                    customIso = exifObj.optString("customIso", "").takeIf { it.isNotBlank() },
                    customFocalLength = exifObj.optString("customFocalLength", "").takeIf { it.isNotBlank() },
                    customAperture = exifObj.optString("customAperture", "").takeIf { it.isNotBlank() },
                    customExposure = exifObj.optString("customExposure", "").takeIf { it.isNotBlank() },
                    deviceInSameLine = exifObj.optBoolean("deviceInSameLine", false),
                    separator = exifObj.optString("separator", " • ")
                )
            } else {
                default.exifSettings
            }

            val colorsObj = obj.optJSONObject("colorSettings")
            val colorSettings = if (colorsObj != null) {
                val useCustom = colorsObj.optBoolean("useCustomColors", false)
                val bg = colorsObj.optInt("backgroundColor", 0).takeIf { it != 0 }
                val text = colorsObj.optInt("mainTextColor", 0).takeIf { it != 0 }
                val exif = colorsObj.optInt("exifTextColor", 0).takeIf { it != 0 }
                ColorSettings(
                    useCustomColors = useCustom,
                    backgroundColor = bg,
                    mainTextColor = text,
                    exifTextColor = exif
                )
            } else {
                default.colorSettings
            }

            val position = runCatching { WatermarkPosition.valueOf(positionName) }.getOrDefault(default.position)
            val watermarkTheme = runCatching { WatermarkTheme.valueOf(themeName) }.getOrDefault(WatermarkTheme.LIGHT)

            val mainFont = ResourceRegistry.findFontById(mainFontId) ?: default.mainTextFont
            val exifFont = ResourceRegistry.findFontById(exifFontId) ?: default.exifTextFont
            val logo = if (logoId.isNotBlank()) {
                ResourceRegistry.findLogoById(logoId) ?: default.logo
            } else {
                default.logo
            }
            val style = if (styleId.isNotBlank()) {
                ResourceRegistry.findStyleById(styleId) ?: default.style
            } else {
                default.style
            }

            val newConfig = default.copy(
                mainText = mainText,
                useExifData = useExifData,
                exifSettings = exifSettings,
                mainTextFont = mainFont,
                exifTextFont = exifFont,
                position = position,
                logo = logo,
                style = style,
                scalePercent = scalePercent,
                colorSettings = colorSettings
            )

            _uiState.update {
                it.copy(
                    config = newConfig,
                    watermarkTheme = watermarkTheme
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetToDefaults() {
        val defaultConfig = repository.getDefaultConfig()
        _uiState.update {
            it.copy(
                config = defaultConfig,
                watermarkTheme = WatermarkTheme.LIGHT
            )
        }
        prefs.edit().remove("saved_config").apply()
        updateWatermarkPreview()
    }

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingImage = true, error = null) }

            try {
                val dimensions = BitmapUtils.getActualImageDimensions(getApplication(), uri)

                if (dimensions == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingImage = false,
                            error = "Failed to load image dimensions"
                        )
                    }
                    return@launch
                }

                val (actualWidth, actualHeight) = dimensions
                val previewBitmap = BitmapUtils.loadBitmapForPreview(getApplication(), uri)

                if (previewBitmap == null) {
                    _uiState.update {
                        it.copy(
                            isLoadingImage = false,
                            error = "Failed to load image preview"
                        )
                    }
                    return@launch
                }

                val exifData = ExifUtils.extractExifData(getApplication(), uri)

                _uiState.update {
                    it.copy(
                        selectedImageUri = uri,
                        previewBitmap = previewBitmap,
                        actualImageWidth = actualWidth,
                        actualImageHeight = actualHeight,
                        exifData = exifData,
                        isLoadingImage = false,
                        error = null
                    )
                }

                updateWatermarkPreview()
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isLoadingImage = false,
                        error = "Error loading image: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateMainText(text: String) {
        val trimmedText = text.take(WatermarkConfig.MAX_TEXT_LENGTH)
        updateConfigAndRefresh { it.copy(mainText = trimmedText) }
    }

    fun updateUseExifData(use: Boolean) {
        updateConfigAndRefresh { it.copy(useExifData = use) }
    }

    fun updateExifSettings(settings: ExifSettings) {
        updateConfigAndRefresh { it.copy(exifSettings = settings) }
    }

    fun updateMainTextFont(font: FontResource) {
        updateConfigAndRefresh { it.copy(mainTextFont = font) }
    }

    fun updateExifTextFont(font: FontResource) {
        updateConfigAndRefresh { it.copy(exifTextFont = font) }
    }

    fun updatePosition(position: WatermarkPosition) {
        updateConfigAndRefresh(refreshPreview = false) { it.copy(position = position) }
    }

    fun updateLogo(logo: LogoResource?) {
        val newLogo = if (logo?.isNoLogo == true) null else logo
        updateConfigAndRefresh { it.copy(logo = newLogo) }
    }

    fun updateWatermarkTheme(theme: WatermarkTheme) {
        _uiState.update { it.copy(watermarkTheme = theme) }
        updateWatermarkPreview()
        saveConfigIfNeeded()
    }

    fun updateStyle(style: WatermarkStyle) {
        updateConfigAndRefresh { it.copy(style = style) }
    }

    fun updateScale(scale: Float) {
        updateConfigAndRefresh { it.copy(scalePercent = scale) }
    }

    fun updateColorSettings(colorSettings: ColorSettings) {
        updateConfigAndRefresh { it.copy(colorSettings = colorSettings) }
    }

    fun toggleSaveSettings(save: Boolean) {
        prefs.edit().putBoolean("save_settings", save).apply()
        if (!save) {
            prefs.edit().remove("saved_config").apply()
        } else {
            saveConfigIfNeeded()
        }
        _uiState.update { it.copy(saveSettings = save) }
    }

    fun addCustomLogo(uri: Uri, name: String, isMonochrome: Boolean) {
        viewModelScope.launch {
            try {
                val newLogo = repository.addCustomLogo(getApplication(), uri, name, isMonochrome)
                val updatedLogos = repository.getAllLogos()
                _uiState.update {
                    it.copy(
                        allLogos = updatedLogos,
                        config = it.config.copy(logo = newLogo)
                    )
                }
                updateWatermarkPreview()
                saveConfigIfNeeded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add logo: ${e.message}")
                }
            }
        }
    }

    fun addCustomTemplate(
        name: String,
        description: String,
        templateJson: String
    ) {
        viewModelScope.launch {
            try {
                val newStyle = repository.addCustomTemplate(name, description, templateJson)
                val updatedStyles = repository.getAllStyles()
                _uiState.update {
                    it.copy(
                        allStyles = updatedStyles,
                        config = it.config.copy(style = newStyle)
                    )
                }
                updateWatermarkPreview()
                saveConfigIfNeeded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add template: ${e.message}")
                }
            }
        }
    }

    fun deleteCustomLogo(logoId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomLogo(logoId)
                val updatedLogos = repository.getAllLogos()

                val newConfig = if (_uiState.value.config.logo?.id == logoId) {
                    _uiState.value.config.copy(logo = repository.getDefaultConfig().logo)
                } else {
                    _uiState.value.config
                }

                _uiState.update {
                    it.copy(
                        allLogos = updatedLogos,
                        config = newConfig
                    )
                }
                updateWatermarkPreview()
                saveConfigIfNeeded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to delete logo: ${e.message}")
                }
            }
        }
    }

    fun deleteCustomTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomTemplate(templateId)
                val updatedStyles = repository.getAllStyles()

                val newConfig = if (_uiState.value.config.style.id == templateId) {
                    _uiState.value.config.copy(style = repository.getDefaultConfig().style)
                } else {
                    _uiState.value.config
                }

                _uiState.update {
                    it.copy(
                        allStyles = updatedStyles,
                        config = newConfig
                    )
                }
                updateWatermarkPreview()
                saveConfigIfNeeded()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to delete template: ${e.message}")
                }
            }
        }
    }

    private fun updateWatermarkPreview() {
        val currentState = _uiState.value
        if (currentState.previewBitmap == null) return

        viewModelScope.launch {
            try {
                val previewWidth = 800
                val previewHeight = (previewWidth * currentState.actualImageHeight /
                        currentState.actualImageWidth.coerceAtLeast(1))

                val watermarkBitmap = WatermarkRenderer.renderWatermark(
                    context = getApplication(),
                    config = currentState.config,
                    exifData = currentState.exifData,
                    imageWidth = previewWidth,
                    imageHeight = previewHeight,
                    theme = currentState.watermarkTheme
                )

                _uiState.update {
                    it.copy(previewWatermarkBitmap = watermarkBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun processImage() {
        val currentState = _uiState.value

        if (!currentState.isReadyToProcess) {
            _uiState.update {
                it.copy(error = "Please select an image first")
            }
            return
        }

        val imageUri = currentState.selectedImageUri ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, processingProgress = "Loading full image...")
            }

            try {
                val originalBitmap = BitmapUtils.loadBitmapForProcessing(getApplication(), imageUri)

                if (originalBitmap == null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingProgress = "",
                            error = "Failed to load image for processing"
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(processingProgress = "Rendering watermark...") }

                val watermarkBitmap = WatermarkRenderer.renderWatermark(
                    context = getApplication(),
                    config = currentState.config,
                    exifData = currentState.exifData,
                    imageWidth = originalBitmap.width,
                    imageHeight = originalBitmap.height,
                    theme = currentState.watermarkTheme
                )

                _uiState.update { it.copy(processingProgress = "Applying watermark...") }

                val resultBitmap = BitmapUtils.appendWatermark(
                    original = originalBitmap,
                    watermark = watermarkBitmap,
                    position = currentState.config.position
                )

                _uiState.update { it.copy(processingProgress = "Saving to gallery...") }

                val saveResult = ImageSaver.saveToGallery(
                    context = getApplication(),
                    bitmap = resultBitmap
                )

                originalBitmap.recycle()
                watermarkBitmap.recycle()
                resultBitmap.recycle()

                saveResult.fold(
                    onSuccess = { uri ->
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                processingProgress = "",
                                savedImageUri = uri,
                                error = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                processingProgress = "",
                                error = "Failed to save: ${error.message}"
                            )
                        }
                    }
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = "",
                        error = "Processing error: ${e.message}"
                    )
                }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = "",
                        error = "Image too large. Try a smaller image."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSavedUri() {
        _uiState.update { it.copy(savedImageUri = null) }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.previewBitmap?.recycle()
        _uiState.value.previewWatermarkBitmap?.recycle()
    }
}