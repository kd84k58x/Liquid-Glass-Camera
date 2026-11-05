package com.example.ui

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** The controls of Pro mode. */
enum class ProControl { EV, ISO, SHUTTER, FOCUS, WB }

/** One white-balance preset of the phone (the phone's own calibrated colours), ordered cool to warm. */
data class WbStop(val mode: Int, val label: String, val kelvin: Int)

/** What this phone's camera can do manually. Only the supported controls are shown. */
data class ProCaps(
    val evMin: Int = 0,
    val evMax: Int = 0,
    val evStep: Float = 0f,
    val manualSensor: Boolean = false,
    val isoMin: Int = 100,
    val isoMax: Int = 100,
    val shutterMinNs: Long = 125_000L,
    val shutterMaxNs: Long = 250_000_000L,
    val manualFocus: Boolean = false,
    val minFocusDiopters: Float = 0f,
    val wbStops: List<WbStop> = emptyList()
) {
    val hasEv: Boolean get() = evMax > evMin && evStep > 0f
}

/**
 * Pro mode: real-time manual controls on top of CameraX, using the phone's own Camera2 settings.
 *
 *  - Brightness (EV): exposure compensation.
 *  - ISO and shutter speed: manual exposure (switching one of them on switches the automatic exposure off).
 *  - Focus: manual focus distance, from infinity to the closest distance the lens can focus.
 *  - White balance: the phone's calibrated presets from cool (Tungsten) to warm (Shade).
 *
 * Every change is applied immediately to the preview and to the photo that is taken next.
 * Leaving Pro mode puts everything back to automatic.
 */
@OptIn(ExperimentalCamera2Interop::class)
class ProController(private val controller: LifecycleCameraController) {

    var caps by mutableStateOf(ProCaps())
        private set

    var evIndex by mutableIntStateOf(0)
        private set
    var manualExposure by mutableStateOf(false)
        private set
    var iso by mutableIntStateOf(200)
        private set
    var shutterNs by mutableLongStateOf(16_666_667L)
        private set
    var manualFocus by mutableStateOf(false)
        private set
    var focusDiopters by mutableFloatStateOf(0f)
        private set

    /** -1 = automatic, otherwise an index into [ProCaps.wbStops]. */
    var wbIndex by mutableIntStateOf(-1)
        private set

    /** The control whose slider is open (null = only the chips are shown). */
    var selected by mutableStateOf<ProControl?>(null)

    private var active = false

    // ---- entering / leaving ------------------------------------------------------------------------------

    suspend fun enter() {
        active = true
        var tries = 0
        while ((controller.cameraInfo == null || controller.cameraControl == null) && tries < 60) {
            delay(50)
            tries++
        }
        val info = controller.cameraInfo ?: return
        caps = readCaps(info)
        resetValues()
        applyAll()
    }

    fun exit() {
        if (!active) return
        active = false
        selected = null
        try {
            controller.cameraControl?.let {
                Camera2CameraControl.from(it).clearCaptureRequestOptions()
                it.setExposureCompensationIndex(0)
            }
            controller.isTapToFocusEnabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        resetValues()
    }

    private fun resetValues() {
        evIndex = 0
        manualExposure = false
        manualFocus = false
        wbIndex = -1
        iso = 200.coerceIn(caps.isoMin, maxOf(caps.isoMin, caps.isoMax))
        shutterNs = 16_666_667L.coerceIn(caps.shutterMinNs, maxOf(caps.shutterMinNs, caps.shutterMaxNs))
        focusDiopters = caps.minFocusDiopters * 0.35f
    }

    private fun readCaps(info: CameraInfo): ProCaps {
        val c2 = Camera2CameraInfo.from(info)
        val ev = info.exposureState
        val evRange = ev.exposureCompensationRange
        val evStep = ev.exposureCompensationStep.toFloat()

        val isoRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val capabilities = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val manualSensor = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) &&
            isoRange != null && expRange != null

        val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val afModes = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
        val manualFocus = minFocus > 0f && afModes.contains(CameraMetadata.CONTROL_AF_MODE_OFF)

        val awbModes = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: IntArray(0)
        val stops = listOf(
            WbStop(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, "Tungsten", 2700),
            WbStop(CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT, "Warm fluorescent", 3000),
            WbStop(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, "Fluorescent", 4200),
            WbStop(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, "Daylight", 5500),
            WbStop(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, "Cloudy", 6500),
            WbStop(CameraMetadata.CONTROL_AWB_MODE_SHADE, "Shade", 7500)
        ).filter { awbModes.contains(it.mode) }

        return ProCaps(
            evMin = evRange.lower,
            evMax = evRange.upper,
            evStep = evStep,
            manualSensor = manualSensor,
            isoMin = isoRange?.lower ?: 100,
            isoMax = isoRange?.upper ?: 100,
            shutterMinNs = maxOf(expRange?.lower ?: 125_000L, 125_000L),           // not faster than 1/8000 s
            shutterMaxNs = minOf(expRange?.upper ?: 250_000_000L, 250_000_000L),   // not slower than 1/4 s
            manualFocus = manualFocus,
            minFocusDiopters = minFocus,
            wbStops = stops
        )
    }

    // ---- changing values ---------------------------------------------------------------------------------

    fun setEv(index: Int) {
        evIndex = index.coerceIn(caps.evMin, caps.evMax)
        if (!manualExposure) {
            try { controller.cameraControl?.setExposureCompensationIndex(evIndex) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun setIsoValue(value: Int) {
        manualExposure = true
        iso = value.coerceIn(caps.isoMin, maxOf(caps.isoMin, caps.isoMax))
        applyAll()
    }

    fun setShutter(ns: Long) {
        manualExposure = true
        shutterNs = ns.coerceIn(caps.shutterMinNs, maxOf(caps.shutterMinNs, caps.shutterMaxNs))
        applyAll()
    }

    fun autoExposure() {
        manualExposure = false
        applyAll()
        try { controller.cameraControl?.setExposureCompensationIndex(evIndex) } catch (e: Exception) { e.printStackTrace() }
    }

    fun setFocus(diopters: Float) {
        manualFocus = true
        focusDiopters = diopters.coerceIn(0f, caps.minFocusDiopters)
        applyAll()
    }

    fun autoFocus() {
        manualFocus = false
        applyAll()
    }

    /** [index] -1 = automatic. */
    fun setWb(index: Int) {
        wbIndex = if (index in caps.wbStops.indices) index else -1
        applyAll()
    }

    fun resetAll() {
        resetValues()
        applyAll()
        try { controller.cameraControl?.setExposureCompensationIndex(0) } catch (e: Exception) { e.printStackTrace() }
    }

    /** Sends everything that is manual to the camera in one go (the camera keeps the last set it received). */
    private fun applyAll() {
        val cc = controller.cameraControl ?: return
        try {
            val b = CaptureRequestOptions.Builder()
            if (manualExposure && caps.manualSensor) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
                b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, shutterNs)
            }
            val mf = manualFocus && caps.manualFocus
            if (mf) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopters)
            }
            if (wbIndex in caps.wbStops.indices) {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, caps.wbStops[wbIndex].mode)
            }
            Camera2CameraControl.from(cc).setCaptureRequestOptions(b.build())
            controller.isTapToFocusEnabled = !mf      // tap-to-focus would fight the manual focus
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// ---- text helpers --------------------------------------------------------------------------------------------

private fun shutterText(ns: Long): String {
    val s = ns / 1_000_000_000.0
    return if (s >= 0.4) String.format("%.1f s", s) else "1/" + (1.0 / s).roundToInt()
}

private fun focusText(diopters: Float): String =
    if (diopters < 0.05f) "\u221E" else "${(100f / diopters).roundToInt()} cm"

// ---- UI ------------------------------------------------------------------------------------------------------

/**
 * The Pro panel above the shutter: a row of chips (EV, ISO, S, MF, WB, reset). Tapping a chip opens its slider
 * above the row; every move of the slider is applied to the camera at once.
 * The chips fade in one after the other (left to right) when the panel appears. Nothing slides.
 */
@Composable
fun ProPanel(
    pro: ProController,
    uiColor: Color,
    onHaptic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val caps = pro.caps
    val selected = pro.selected

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (selected != null) {
                ProSliderCard(pro = pro, control = selected, uiColor = uiColor)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            val toggle: (ProControl) -> Unit = { c ->
                onHaptic()
                pro.selected = if (pro.selected == c) null else c
            }
            // position of each chip in the row = its turn to fade in
            var order = 0
            if (caps.hasEv) {
                FadeInOneByOne(order++) {
                    ProChip("EV", String.format("%+.1f", pro.evIndex * caps.evStep), selected == ProControl.EV, uiColor) { toggle(ProControl.EV) }
                }
            }
            if (caps.manualSensor) {
                FadeInOneByOne(order++) {
                    ProChip("ISO", if (pro.manualExposure) pro.iso.toString() else "AUTO", selected == ProControl.ISO, uiColor) { toggle(ProControl.ISO) }
                }
                FadeInOneByOne(order++) {
                    ProChip("S", if (pro.manualExposure) shutterText(pro.shutterNs) else "AUTO", selected == ProControl.SHUTTER, uiColor) { toggle(ProControl.SHUTTER) }
                }
            }
            if (caps.manualFocus) {
                FadeInOneByOne(order++) {
                    ProChip("FOCUS", if (pro.manualFocus) focusText(pro.focusDiopters) else "AUTO", selected == ProControl.FOCUS, uiColor) { toggle(ProControl.FOCUS) }
                }
            }
            if (caps.wbStops.isNotEmpty()) {
                FadeInOneByOne(order++) {
                    ProChip("WB", if (pro.wbIndex >= 0) "${caps.wbStops[pro.wbIndex].kelvin}K" else "AUTO", selected == ProControl.WB, uiColor) { toggle(ProControl.WB) }
                }
            }
            FadeInOneByOne(order++) {
                ProChip("RESET", "\u21BA", false, uiColor) {
                    onHaptic()
                    pro.resetAll()
                }
            }
        }
    }
}

private const val PRO_CHIP_START_DELAY_MS = 100L   // lets the previous panel fade out first
private const val PRO_CHIP_STEP_MS = 70L            // gap between one chip and the next
private const val PRO_CHIP_FADE_MS = 260

/** Fades its content in after [index] steps, so a row of chips appears one after the other. Fade only, no movement. */
@Composable
private fun FadeInOneByOne(index: Int, content: @Composable () -> Unit) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(index) {
        delay(PRO_CHIP_START_DELAY_MS + index * PRO_CHIP_STEP_MS)
        fade.animateTo(1f, tween(PRO_CHIP_FADE_MS, easing = FastOutSlowInEasing))
    }
    Box(modifier = Modifier.graphicsLayer { alpha = fade.value }) {
        content()
    }
}

@Composable
private fun ProChip(
    label: String,
    value: String,
    selected: Boolean,
    uiColor: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    LiquidGlassContainer(
        shape = shape,
        modifier = Modifier
            .width(62.dp)
            .then(if (selected) Modifier.border(1.5.dp, uiColor, shape) else Modifier)
            .bouncyClickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
            Text(
                text = value,
                color = if (selected) uiColor else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProSliderCard(
    pro: ProController,
    control: ProControl,
    uiColor: Color
) {
    val caps = pro.caps
    val title: String
    val valueText: String
    val showAuto: Boolean
    val onAuto: () -> Unit
    val hint: String?

    when (control) {
        ProControl.EV -> {
            title = "Brightness"
            valueText = String.format("%+.1f EV", pro.evIndex * caps.evStep)
            showAuto = pro.evIndex != 0
            onAuto = { pro.setEv(0) }
            hint = if (pro.manualExposure) "Manual exposure is on - tap AUTO on ISO or S to use brightness" else null
        }
        ProControl.ISO -> {
            title = "ISO"
            valueText = if (pro.manualExposure) pro.iso.toString() else "AUTO"
            showAuto = pro.manualExposure
            onAuto = { pro.autoExposure() }
            hint = null
        }
        ProControl.SHUTTER -> {
            title = "Shutter speed"
            valueText = if (pro.manualExposure) shutterText(pro.shutterNs) else "AUTO"
            showAuto = pro.manualExposure
            onAuto = { pro.autoExposure() }
            hint = null
        }
        ProControl.FOCUS -> {
            title = "Focus"
            valueText = if (pro.manualFocus) focusText(pro.focusDiopters) else "AUTO"
            showAuto = pro.manualFocus
            onAuto = { pro.autoFocus() }
            hint = "Slide to the right to focus closer"
        }
        ProControl.WB -> {
            title = "Temperature"
            valueText = if (pro.wbIndex >= 0) "${caps.wbStops[pro.wbIndex].label}  ${caps.wbStops[pro.wbIndex].kelvin}K" else "AUTO"
            showAuto = pro.wbIndex >= 0
            onAuto = { pro.setWb(-1) }
            hint = "\u2190 cooler          warmer \u2192"
        }
    }

    LiquidGlassContainer(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = valueText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (showAuto) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, uiColor, CircleShape)
                            .bouncyClickable(onClick = onAuto)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(text = "AUTO", color = uiColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    }
                }
            }

            val colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
            when (control) {
                ProControl.EV -> Slider(
                    value = pro.evIndex.toFloat(),
                    onValueChange = { pro.setEv(it.roundToInt()) },
                    valueRange = caps.evMin.toFloat()..caps.evMax.toFloat(),
                    steps = (caps.evMax - caps.evMin - 1).coerceAtLeast(0),
                    colors = colors
                )
                ProControl.ISO -> {
                    val ratio = (caps.isoMax.toFloat() / caps.isoMin).coerceAtLeast(1.0001f)
                    Slider(
                        value = (ln(pro.iso.toFloat() / caps.isoMin) / ln(ratio)).coerceIn(0f, 1f),
                        onValueChange = { v -> pro.setIsoValue((caps.isoMin * ratio.pow(v)).roundToInt()) },
                        colors = colors
                    )
                }
                ProControl.SHUTTER -> {
                    val ratio = (caps.shutterMaxNs.toFloat() / caps.shutterMinNs).coerceAtLeast(1.0001f)
                    Slider(
                        value = (ln(pro.shutterNs.toFloat() / caps.shutterMinNs) / ln(ratio)).coerceIn(0f, 1f),
                        // left = fast (1/8000), right = slow (up to 1/4 s)
                        onValueChange = { v -> pro.setShutter((caps.shutterMinNs * ratio.pow(v)).toLong()) },
                        colors = colors
                    )
                }
                ProControl.FOCUS -> Slider(
                    value = if (caps.minFocusDiopters > 0f) (pro.focusDiopters / caps.minFocusDiopters).coerceIn(0f, 1f) else 0f,
                    onValueChange = { v -> pro.setFocus(v * caps.minFocusDiopters) },
                    colors = colors
                )
                ProControl.WB -> Slider(
                    // position 0 = automatic, then the presets from cool to warm
                    value = (pro.wbIndex + 1).toFloat(),
                    onValueChange = { v -> pro.setWb(v.roundToInt() - 1) },
                    valueRange = 0f..caps.wbStops.size.toFloat(),
                    steps = (caps.wbStops.size - 1).coerceAtLeast(0),
                    colors = colors
                )
            }
            if (hint != null) {
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
