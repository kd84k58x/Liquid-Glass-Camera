package com.example.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** What macro mode ended up using on this phone. */
enum class MacroLens {
    /** Macro mode is off. */
    NONE,

    /** The normal rear camera, with the autofocus switched to its close-focus (macro) mode. */
    MAIN_MACRO_FOCUS,

    /** A different rear camera that can focus closer (usually the ultra-wide one), plus macro autofocus. */
    CLOSE_UP_LENS
}

data class MacroInfo(val lens: MacroLens, val closestFocusCm: Int?) {
    companion object {
        val None = MacroInfo(MacroLens.NONE, null)
    }
}

/**
 * Macro mode for CameraX's [LifecycleCameraController].
 *
 * Two things are done when macro mode starts (both use only what the phone itself reports):
 *  1. If another rear camera can focus clearly closer than the main one (many phones use the ultra-wide
 *     camera for macro), that camera is selected.
 *  2. The autofocus is switched to macro mode when the camera supports it, so it hunts in the close range
 *     (a few centimetres) instead of giving up.
 *
 * Leaving macro mode undoes both. Phones that report neither still get a normal photo mode.
 */
@OptIn(ExperimentalCamera2Interop::class)
class MacroController(
    private val context: Context,
    private val controller: LifecycleCameraController
) {
    private var active = false
    private var macroSelector: CameraSelector? = null

    private suspend fun provider(): ProcessCameraProvider? = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resume(try { future.get() } catch (e: Exception) { null }) },
            ContextCompat.getMainExecutor(context)
        )
    }

    private suspend fun waitForCamera(): Boolean {
        repeat(60) {
            if (controller.cameraControl != null && controller.cameraInfo != null) return true
            delay(50)
        }
        return false
    }

    /** Closest distance (cm) a camera can focus on, from the diopter value the phone reports. */
    private fun closestFocusCm(minFocusDiopters: Float?): Int? =
        if (minFocusDiopters != null && minFocusDiopters > 0f) (100f / minFocusDiopters).roundToInt() else null

    /** Id of the rear camera that focuses clearly closer than the default one, or null. */
    private fun closeUpCameraId(provider: ProcessCameraProvider): Pair<String, Float>? {
        val infos = provider.availableCameraInfos
        val defaultInfo = CameraSelector.DEFAULT_BACK_CAMERA.filter(infos).firstOrNull() ?: return null
        val defaultId = Camera2CameraInfo.from(defaultInfo).cameraId
        val defaultDiopters = Camera2CameraInfo.from(defaultInfo)
            .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        var bestId: String? = null
        var bestDiopters = 0f
        for (info in infos) {
            val c2 = Camera2CameraInfo.from(info)
            if (c2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val diopters = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            if (diopters > bestDiopters) {
                bestDiopters = diopters
                bestId = c2.cameraId
            }
        }
        // only worth switching when it focuses at least 1.4x closer than the main camera
        return if (bestId != null && bestId != defaultId && bestDiopters > defaultDiopters * 1.4f) bestId to bestDiopters else null
    }

    private fun selectorFor(cameraId: String): CameraSelector =
        CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == cameraId } }
            .build()

    /** Switches macro mode on. Call when the rear camera is the active one. */
    suspend fun enter(): MacroInfo {
        active = true
        var lens = MacroLens.MAIN_MACRO_FOCUS
        var diopters: Float? = null

        try {
            val prov = provider()
            val close = if (prov != null) closeUpCameraId(prov) else null
            if (close != null) {
                val selector = selectorFor(close.first)
                if (controller.hasCamera(selector)) {
                    controller.cameraSelector = selector
                    macroSelector = selector
                    lens = MacroLens.CLOSE_UP_LENS
                    diopters = close.second
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!waitForCamera()) return MacroInfo(lens, closestFocusCm(diopters))
        delay(120)   // let a lens switch finish rebinding

        try {
            val info = controller.cameraInfo ?: return MacroInfo(lens, closestFocusCm(diopters))
            val c2 = Camera2CameraInfo.from(info)
            if (diopters == null) {
                diopters = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            }
            val afModes = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            if (afModes != null && afModes.contains(CameraMetadata.CONTROL_AF_MODE_MACRO)) {
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_MACRO)
                    .build()
                controller.cameraControl?.let { Camera2CameraControl.from(it).setCaptureRequestOptions(options) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        controller.setZoomRatio(1f)
        return MacroInfo(lens, closestFocusCm(diopters))
    }

    /** Switches macro mode off and goes back to the normal rear camera. */
    suspend fun exit() {
        if (!active) return
        active = false
        try {
            controller.cameraControl?.let { Camera2CameraControl.from(it).clearCaptureRequestOptions() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Only undo the lens change if it is still the one macro mode picked (not if the user
        // flipped to the selfie camera in the meantime).
        val picked = macroSelector
        macroSelector = null
        if (picked != null && controller.cameraSelector === picked) {
            try {
                controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/** Small floating badge shown at the top in Macro mode. */
@Composable
fun MacroBadge(info: MacroInfo, modifier: Modifier = Modifier) {
    val subtitle = when (info.lens) {
        MacroLens.CLOSE_UP_LENS ->
            "Close-up lens" + (info.closestFocusCm?.let { " \u2022 focuses from ~$it cm" } ?: "")
        MacroLens.MAIN_MACRO_FOCUS ->
            "Macro focus on" + (info.closestFocusCm?.let { " \u2022 from ~$it cm" } ?: "") + " \u2022 move in close"
        MacroLens.NONE -> "Getting ready\u2026"
    }
    BlurGlassBar(modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "\uD83C\uDF38", fontSize = 18.sp)
            Column {
                Text(
                    text = "Macro",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
