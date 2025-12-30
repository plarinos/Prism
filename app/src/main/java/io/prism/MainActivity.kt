package io.prism

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.prism.data.model.ColorSettings
import io.prism.data.model.ExifSettings
import io.prism.data.model.LogoResource
import io.prism.data.model.WatermarkStyle
import io.prism.data.model.WatermarkTheme
import io.prism.databinding.ActivityMainBinding
import io.prism.databinding.BottomSheetColorsBinding
import io.prism.databinding.BottomSheetExifBinding
import io.prism.databinding.BottomSheetFontsBinding
import io.prism.databinding.BottomSheetLogoBinding
import io.prism.databinding.BottomSheetPositionBinding
import io.prism.databinding.BottomSheetScaleBinding
import io.prism.databinding.BottomSheetStyleBinding
import io.prism.databinding.BottomSheetTextBinding
import io.prism.databinding.BottomSheetThemeBinding
import io.prism.ui.BottomSheetType
import io.prism.ui.MainUiState
import io.prism.ui.MainViewModel
import io.prism.ui.adapters.FontGridAdapter
import io.prism.ui.adapters.LogoGridAdapter
import io.prism.ui.adapters.PositionAdapter
import io.prism.ui.adapters.StyleGridAdapter
import io.prism.ui.adapters.ToolbarAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var toolbarAdapter: ToolbarAdapter
    private var pendingLogoUri: Uri? = null
    private var currentDialog: BottomSheetDialog? = null
    private var saveSwitchChanging = false

    
    private var currentSheetJob: Job? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.selectImage(it) }
    }

    private val logoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            pendingLogoUri = it
            showAddLogoDialogWithPreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTopBar()
        setupViews()
        observeState()
    }

    private fun setupTopBar() {
        binding.appTitle.text = getString(R.string.app_name)
        binding.saveSettingsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!saveSwitchChanging) {
                viewModel.toggleSaveSettings(isChecked)
            }
        }

        binding.resetButton.setOnClickListener {
            showResetConfirmation()
        }
    }

    private fun showResetConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_settings_title)
            .setMessage(R.string.reset_settings_message)
            .setPositiveButton(R.string.reset) { _, _ ->
                viewModel.resetToDefaults()
                Toast.makeText(this, R.string.settings_reset_success, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupToolbar() {
        toolbarAdapter = ToolbarAdapter { type ->
            showBottomSheet(type)
        }

        binding.toolbarRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = toolbarAdapter
        }
    }

    private fun setupViews() {
        binding.imageContainer.setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.processButton.setOnClickListener {
            viewModel.processImage()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: MainUiState) {
        if (state.isLoadingImage) {
            binding.imagePreview.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
            binding.placeholderText.text = getString(R.string.loading_image)
        } else if (state.previewBitmap != null) {
            binding.imagePreview.setImageBitmap(state.previewBitmap)
            binding.imagePreview.visibility = View.VISIBLE
            binding.placeholderContainer.visibility = View.GONE
            binding.imageSizeText.text = getString(
                R.string.image_size_format,
                state.actualImageWidth,
                state.actualImageHeight
            )
            binding.imageSizeText.visibility = View.VISIBLE
        } else {
            binding.imagePreview.visibility = View.GONE
            binding.placeholderContainer.visibility = View.VISIBLE
            binding.placeholderText.text = getString(R.string.tap_to_select_image)
            binding.imageSizeText.visibility = View.GONE
        }

        if (state.previewWatermarkBitmap != null) {
            binding.watermarkPreview.setImageBitmap(state.previewWatermarkBitmap)
            binding.watermarkPreview.visibility = View.VISIBLE
        } else {
            binding.watermarkPreview.visibility = View.GONE
        }

        if (state.isProcessing) {
            binding.processingOverlay.visibility = View.VISIBLE
            binding.processingText.text = state.processingProgress
            binding.processButton.isEnabled = false
        } else {
            binding.processingOverlay.visibility = View.GONE
            binding.processButton.isEnabled = state.isReadyToProcess
        }

        if (binding.saveSettingsSwitch.isChecked != state.saveSettings) {
            saveSwitchChanging = true
            binding.saveSettingsSwitch.isChecked = state.saveSettings
            saveSwitchChanging = false
        }

        state.savedImageUri?.let {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.image_saved_success),
                Toast.LENGTH_LONG
            ).show()
            viewModel.clearSavedUri()
        }

        state.error?.let { error ->
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.error_title)
                .setMessage(error)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .show()
            viewModel.clearError()
        }
    }

    private fun showBottomSheet(type: BottomSheetType) {
        
        currentSheetJob?.cancel()
        currentDialog?.dismiss()

        val dialog = BottomSheetDialog(this)
        currentDialog = dialog

        when (type) {
            BottomSheetType.TEXT -> {
                val sheetBinding = BottomSheetTextBinding.inflate(layoutInflater)
                setupTextSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.STYLE -> {
                val sheetBinding = BottomSheetStyleBinding.inflate(layoutInflater)
                setupStyleSheet(sheetBinding, dialog)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.LOGO -> {
                val sheetBinding = BottomSheetLogoBinding.inflate(layoutInflater)
                setupLogoSheet(sheetBinding, dialog)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.POSITION -> {
                val sheetBinding = BottomSheetPositionBinding.inflate(layoutInflater)
                setupPositionSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.SCALE -> {
                val sheetBinding = BottomSheetScaleBinding.inflate(layoutInflater)
                setupScaleSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.THEME -> {
                val sheetBinding = BottomSheetThemeBinding.inflate(layoutInflater)
                setupThemeSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.FONTS -> {
                val sheetBinding = BottomSheetFontsBinding.inflate(layoutInflater)
                setupFontsSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.EXIF -> {
                val sheetBinding = BottomSheetExifBinding.inflate(layoutInflater)
                setupExifSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
            BottomSheetType.COLORS -> {
                val sheetBinding = BottomSheetColorsBinding.inflate(layoutInflater)
                setupColorsSheet(sheetBinding)
                dialog.setContentView(sheetBinding.root)
            }
        }

        dialog.setOnDismissListener {
            
            currentSheetJob?.cancel()
            currentSheetJob = null
            if (currentDialog == dialog) {
                currentDialog = null
            }
        }

        dialog.show()
    }

    private fun setupTextSheet(binding: BottomSheetTextBinding) {
        val state = viewModel.uiState.value
        binding.mainTextInput.setText(state.config.mainText)
        binding.mainTextInput.doAfterTextChanged { text ->
            viewModel.updateMainText(text?.toString() ?: "")
        }
    }

    private fun setupStyleSheet(binding: BottomSheetStyleBinding, dialog: BottomSheetDialog) {
        val adapter = StyleGridAdapter(
            onStyleSelected = { style ->
                viewModel.updateStyle(style)
            },
            onAddCustomTemplate = {
                dialog.dismiss()
                showAddTemplateDialog()
            },
            onDeleteCustomTemplate = { style ->
                showDeleteTemplateConfirmation(style)
            }
        )

        binding.styleRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            this.adapter = adapter
        }

        binding.templateHelpLink.setOnClickListener {
            val url = getString(R.string.template_guide_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        
        currentSheetJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                adapter.submitStyles(state.allStyles)
                adapter.setSelectedStyle(state.config.style)
            }
        }
    }

    private fun setupLogoSheet(binding: BottomSheetLogoBinding, dialog: BottomSheetDialog) {
        val adapter = LogoGridAdapter(
            onLogoSelected = { logo ->
                viewModel.updateLogo(logo)
            },
            onAddCustomLogo = {
                dialog.dismiss()
                logoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDeleteCustomLogo = { logo ->
                showDeleteLogoConfirmation(logo)
            }
        )

        binding.logoRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            this.adapter = adapter
        }

        
        currentSheetJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                adapter.submitLogos(state.allLogos)
                adapter.setSelectedLogo(state.config.logo)
            }
        }
    }

    private fun setupPositionSheet(binding: BottomSheetPositionBinding) {
        val state = viewModel.uiState.value
        val adapter = PositionAdapter { position ->
            viewModel.updatePosition(position)
        }

        binding.positionRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            this.adapter = adapter
        }

        adapter.setSelectedPosition(state.config.position)

        
        currentSheetJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { newState ->
                adapter.setSelectedPosition(newState.config.position)
            }
        }
    }

    private fun setupScaleSheet(binding: BottomSheetScaleBinding) {
        val state = viewModel.uiState.value
        binding.scaleSlider.value = state.config.scalePercent
        binding.scaleValueText.text =
            getString(R.string.scale_value_format, state.config.scalePercent.toInt())

        binding.scaleSlider.addOnChangeListener { _, value, _ ->
            binding.scaleValueText.text = getString(R.string.scale_value_format, value.toInt())
        }

        binding.scaleSlider.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    viewModel.updateScale(slider.value)
                }
            }
        )
    }

    private fun setupThemeSheet(binding: BottomSheetThemeBinding) {
        val state = viewModel.uiState.value
        val isThemeLocked = state.config.style.isThemeLocked

        if (isThemeLocked) {
            binding.themeLockedCard.visibility = View.VISIBLE
            binding.themeToggleGroup.isEnabled = false
            binding.lightThemeButton.isEnabled = false
            binding.darkThemeButton.isEnabled = false
            binding.themeToggleGroup.alpha = 0.5f
        } else {
            binding.themeLockedCard.visibility = View.GONE
            binding.themeToggleGroup.isEnabled = true
            binding.lightThemeButton.isEnabled = true
            binding.darkThemeButton.isEnabled = true
            binding.themeToggleGroup.alpha = 1f
        }

        when (state.watermarkTheme) {
            WatermarkTheme.LIGHT -> binding.lightThemeButton.isChecked = true
            WatermarkTheme.DARK -> binding.darkThemeButton.isChecked = true
        }

        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !isThemeLocked) {
                val theme = when (checkedId) {
                    R.id.lightThemeButton -> WatermarkTheme.LIGHT
                    R.id.darkThemeButton -> WatermarkTheme.DARK
                    else -> return@addOnButtonCheckedListener
                }
                viewModel.updateWatermarkTheme(theme)
            }
        }
    }

    private fun setupFontsSheet(binding: BottomSheetFontsBinding) {
        val state = viewModel.uiState.value

        val mainFontAdapter = FontGridAdapter { font ->
            viewModel.updateMainTextFont(font)
        }

        val exifFontAdapter = FontGridAdapter { font ->
            viewModel.updateExifTextFont(font)
        }

        binding.mainFontRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = mainFontAdapter
        }

        binding.exifFontRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = exifFontAdapter
        }

        mainFontAdapter.submitList(viewModel.fonts)
        exifFontAdapter.submitList(viewModel.fonts)

        mainFontAdapter.setSelectedFont(state.config.mainTextFont)
        exifFontAdapter.setSelectedFont(state.config.exifTextFont)

        
        currentSheetJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { newState ->
                mainFontAdapter.setSelectedFont(newState.config.mainTextFont)
                exifFontAdapter.setSelectedFont(newState.config.exifTextFont)
            }
        }
    }

    private fun setupExifSheet(binding: BottomSheetExifBinding) {
        val state = viewModel.uiState.value
        val settings = state.config.exifSettings

        binding.useExifSwitch.isChecked = state.config.useExifData
        binding.showDeviceSwitch.isChecked = settings.showDevice
        binding.deviceInSameLineSwitch.isChecked = settings.deviceInSameLine
        binding.showIsoSwitch.isChecked = settings.showIso
        binding.showFocalSwitch.isChecked = settings.showFocalLength
        binding.showApertureSwitch.isChecked = settings.showAperture
        binding.showExposureSwitch.isChecked = settings.showExposure

        binding.customDeviceInput.setText(settings.customDevice ?: "")
        binding.customIsoInput.setText(settings.customIso ?: "")
        binding.customFocalInput.setText(settings.customFocalLength ?: "")
        binding.customApertureInput.setText(settings.customAperture ?: "")
        binding.customExposureInput.setText(settings.customExposure ?: "")
        binding.separatorInput.setText(settings.separator)

        if (state.exifData != io.prism.data.model.ExifData.EMPTY) {
            binding.detectedDeviceText.text = state.exifData.getDeviceName().ifBlank { "-" }
            binding.detectedIsoText.text = state.exifData.iso ?: "-"
            binding.detectedFocalText.text = state.exifData.focalLength?.let { "${it}mm" } ?: "-"
            binding.detectedApertureText.text = state.exifData.aperture?.let { "f/$it" } ?: "-"
            binding.detectedExposureText.text = state.exifData.exposureTime?.let { "${it}s" } ?: "-"
        }

        binding.exifOptionsContainer.visibility =
            if (state.config.useExifData) View.VISIBLE else View.GONE

        binding.deviceInSameLineSwitch.visibility =
            if (settings.showDevice) View.VISIBLE else View.GONE

        binding.useExifSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateUseExifData(isChecked)
            binding.exifOptionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.showDeviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.deviceInSameLineSwitch.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateExifSettings(binding)
        }

        val updateSettings = { updateExifSettings(binding) }

        binding.deviceInSameLineSwitch.setOnCheckedChangeListener { _, _ -> updateSettings() }
        binding.showIsoSwitch.setOnCheckedChangeListener { _, _ -> updateSettings() }
        binding.showFocalSwitch.setOnCheckedChangeListener { _, _ -> updateSettings() }
        binding.showApertureSwitch.setOnCheckedChangeListener { _, _ -> updateSettings() }
        binding.showExposureSwitch.setOnCheckedChangeListener { _, _ -> updateSettings() }

        binding.customDeviceInput.doAfterTextChanged { updateSettings() }
        binding.customIsoInput.doAfterTextChanged { updateSettings() }
        binding.customFocalInput.doAfterTextChanged { updateSettings() }
        binding.customApertureInput.doAfterTextChanged { updateSettings() }
        binding.customExposureInput.doAfterTextChanged { updateSettings() }
        binding.separatorInput.doAfterTextChanged { updateSettings() }
    }

    private fun updateExifSettings(binding: BottomSheetExifBinding) {
        val separator = binding.separatorInput.text?.toString()?.take(3) ?: " • "

        val newSettings = ExifSettings(
            showDevice = binding.showDeviceSwitch.isChecked,
            showIso = binding.showIsoSwitch.isChecked,
            showFocalLength = binding.showFocalSwitch.isChecked,
            showAperture = binding.showApertureSwitch.isChecked,
            showExposure = binding.showExposureSwitch.isChecked,
            customDevice = binding.customDeviceInput.text?.toString()?.takeIf { it.isNotBlank() },
            customIso = binding.customIsoInput.text?.toString()?.takeIf { it.isNotBlank() },
            customFocalLength = binding.customFocalInput.text?.toString()?.takeIf { it.isNotBlank() },
            customAperture = binding.customApertureInput.text?.toString()?.takeIf { it.isNotBlank() },
            customExposure = binding.customExposureInput.text?.toString()?.takeIf { it.isNotBlank() },
            deviceInSameLine = binding.deviceInSameLineSwitch.isChecked,
            separator = separator.ifBlank { " • " }
        )
        viewModel.updateExifSettings(newSettings)
    }

    private fun setupColorField(
        input: TextInputEditText,
        inputLayout: TextInputLayout,
        preview: View,
        lockIcon: ImageView,
        card: MaterialCardView,
        currentColor: Int?,
        isLocked: Boolean,
        forcedColor: Int?
    ) {
        if (isLocked) {
            input.isEnabled = false
            inputLayout.isEnabled = false
            inputLayout.hint = getString(R.string.color_locked)
            lockIcon.visibility = View.VISIBLE
            card.cardElevation = 0f
            card.alpha = 0.6f

            forcedColor?.let {
                preview.setBackgroundColor(it)
                input.setText(String.format("#%06X", 0xFFFFFF and it))
            }
        } else {
            input.isEnabled = true
            inputLayout.isEnabled = true
            inputLayout.hint = getString(R.string.color_hex)
            lockIcon.visibility = View.GONE
            card.alpha = 1f

            currentColor?.let {
                preview.setBackgroundColor(it)
                input.setText(String.format("#%06X", 0xFFFFFF and it))
            }
        }
    }

    private fun setupColorsSheet(binding: BottomSheetColorsBinding) {
        val state = viewModel.uiState.value
        val colorSettings = state.config.colorSettings
        val colorLocks = state.config.style.colorLocks

        binding.useCustomColorsSwitch.isChecked = colorSettings.useCustomColors

        binding.colorOptionsContainer.visibility =
            if (colorSettings.useCustomColors) View.VISIBLE else View.GONE

        setupColorField(
            input = binding.bgColorHexInput,
            inputLayout = binding.bgColorInputLayout,
            preview = binding.bgColorPreview,
            lockIcon = binding.bgLockedIcon,
            card = binding.bgColorCard,
            currentColor = colorSettings.backgroundColor,
            isLocked = colorLocks.backgroundColorLocked,
            forcedColor = colorLocks.forcedBackgroundColor
        )

        setupColorField(
            input = binding.textColorHexInput,
            inputLayout = binding.textColorInputLayout,
            preview = binding.textColorPreview,
            lockIcon = binding.textLockedIcon,
            card = binding.textColorCard,
            currentColor = colorSettings.mainTextColor,
            isLocked = colorLocks.mainTextColorLocked,
            forcedColor = colorLocks.forcedMainTextColor
        )

        setupColorField(
            input = binding.exifColorHexInput,
            inputLayout = binding.exifColorInputLayout,
            preview = binding.exifColorPreview,
            lockIcon = binding.exifLockedIcon,
            card = binding.exifColorCard,
            currentColor = colorSettings.exifTextColor,
            isLocked = colorLocks.exifTextColorLocked,
            forcedColor = colorLocks.forcedExifTextColor
        )

        binding.useCustomColorsSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.colorOptionsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateColorSettings(binding)
        }

        binding.bgColorHexInput.doAfterTextChanged { updateColorSettings(binding) }
        binding.textColorHexInput.doAfterTextChanged { updateColorSettings(binding) }
        binding.exifColorHexInput.doAfterTextChanged { updateColorSettings(binding) }
    }

    private fun setupColorInput(
        editText: TextInputEditText,
        inputLayout: TextInputLayout,
        preview: View,
        color: Int?,
        locked: Boolean
    ) {
        if (locked) {
            editText.isEnabled = false
            inputLayout.isEnabled = false
            inputLayout.hint = getString(R.string.color_locked)
            inputLayout.alpha = 0.5f
            editText.setText("")
            preview.alpha = 0.5f
        } else {
            editText.isEnabled = true
            inputLayout.isEnabled = true
            inputLayout.hint = getString(R.string.color_hex)
            inputLayout.alpha = 1f
            preview.alpha = 1f
            color?.let {
                preview.setBackgroundColor(it)
                editText.setText(String.format("#%06X", 0xFFFFFF and it))
            }
        }
    }

    private fun setupColorInput(
        editText: TextInputEditText,
        inputLayout: TextInputLayout,
        preview: View,
        color: Int?
    ) {
        color?.let {
            preview.setBackgroundColor(it)
            editText.setText(String.format("#%06X", 0xFFFFFF and it))
        }
    }

    private fun updateColorSettings(binding: BottomSheetColorsBinding) {
        val colorLocks = viewModel.uiState.value.config.style.colorLocks

        val bgColor = if (colorLocks.backgroundColorLocked) null
        else parseColor(binding.bgColorHexInput.text?.toString())
        val textColor = if (colorLocks.mainTextColorLocked) null
        else parseColor(binding.textColorHexInput.text?.toString())
        val exifColor = if (colorLocks.exifTextColorLocked) null
        else parseColor(binding.exifColorHexInput.text?.toString())

        bgColor?.let { binding.bgColorPreview.setBackgroundColor(it) }
        textColor?.let { binding.textColorPreview.setBackgroundColor(it) }
        exifColor?.let { binding.exifColorPreview.setBackgroundColor(it) }

        val newSettings = ColorSettings(
            useCustomColors = binding.useCustomColorsSwitch.isChecked,
            backgroundColor = bgColor,
            mainTextColor = textColor,
            exifTextColor = exifColor
        )
        viewModel.updateColorSettings(newSettings)
    }

    private fun parseColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
        } catch (e: Exception) {
            null
        }
    }

    private fun showAddTemplateDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_template, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.templateNameInput)
        val descInput = dialogView.findViewById<TextInputEditText>(R.id.templateDescInput)
        val jsonInput = dialogView.findViewById<TextInputEditText>(R.id.templateJsonInput)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val desc = descInput.text?.toString()?.trim() ?: ""
                val json = jsonInput.text?.toString()?.trim() ?: ""

                if (name.isNotBlank() && json.isNotBlank()) {
                    viewModel.addCustomTemplate(name, desc, json)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddLogoDialogWithPreview(uri: Uri) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_logo, null)

        val logoPreview = dialogView.findViewById<ImageView>(R.id.logoPreview)
        val selectButton = dialogView.findViewById<View>(R.id.selectLogoButton)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.logoNameInput)
        val monochromeSwitch = dialogView.findViewById<MaterialSwitch>(R.id.monochromeSwitch)

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                logoPreview.setImageBitmap(bitmap)
                logoPreview.visibility = View.VISIBLE
                selectButton.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val isMonochrome = monochromeSwitch.isChecked

                if (name.isNotBlank()) {
                    viewModel.addCustomLogo(uri, name, isMonochrome)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteLogoConfirmation(logo: LogoResource) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_logo_title)
            .setMessage(getString(R.string.delete_logo_message, logo.customName ?: ""))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteCustomLogo(logo.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteTemplateConfirmation(style: WatermarkStyle) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_template_title)
            .setMessage(getString(R.string.delete_template_message, style.customName ?: ""))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteCustomTemplate(style.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}