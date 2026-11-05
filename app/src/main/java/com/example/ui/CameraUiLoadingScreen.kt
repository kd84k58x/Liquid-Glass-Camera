package com.example.ui

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// "Setting up your camera UI" loading screen
//
// Shown by FirstRunSetupScreen right after the "Start Camera" slide is completed:
//   1. the screen cross-fades in over the setup page (the setup controls fade out at the same time). No text is shown
//      until that cross-fade has completely finished,
//   2. a looping, muted video (res/raw/background.mp4, or assets/background.mp4) fills the screen,
//   3. the first text is "Setting up your camera UI" with "This may take a few moments" under it; after that the other
//      status lines follow. All status text is bold, and every line stays on screen for its own, different length of
//      time (see LoaderStages),
//   4. the status line sits in the exact middle of the screen; a Google-colour (blue / green / yellow / red)
//      progress slider (no glow) sits low on the screen, where the arrow button will appear, and fills while the
//      status line changes,
//   5. every new status line arrives with a blur-in / blur-out animation,
//   6. when it is done, the slider fades away and a big white circular arrow button with a light glow ring pops in at the bottom centre,
//      where the under-display fingerprint sensor is (see FingerprintSensorFromBottom).
//      Tapping it calls [onContinue] (which opens the camera).
// ---------------------------------------------------------------------------------------------

private val LoaderBlue = Color(0xFF4285F4)
private val LoaderGreen = Color(0xFF34A853)
private val LoaderYellow = Color(0xFFFBBC05)
private val LoaderRed = Color(0xFFEA4335)
private val LoaderGoogleColors = listOf(LoaderBlue, LoaderGreen, LoaderYellow, LoaderRed)

/** How long the screen takes to cross-fade in over the setup page. The first text only appears after it is done. */
internal const val LoaderCrossFadeMs = 900

/** Small pause after the cross-fade has finished, before the first text blurs in. */
private const val LoaderTextDelayMs = 300L

/**
 * Speed of the text swap, in milliseconds. A bigger number = a slower blur. The new text blurs / fades / rises in over
 * [TextInMs] (after waiting [TextInDelayMs] for the old text to start leaving); the old text blurs out over [TextOutMs].
 */
private const val TextInMs = 1000
private const val TextInDelayMs = 200
private const val TextOutMs = 600

/** What is written on the screen: a big line and, optionally, a smaller line under it. */
private data class StatusLine(val title: String, val subtitle: String? = null)

/**
 * One step of the loading timeline.
 * @param line        the text shown during this step
 * @param durationMs  how long the slider takes to reach [target], which is also how long this text stays on screen
 * @param target      where the slider (0..1) is when this step ends
 */
private class LoaderStage(val line: StatusLine, val durationMs: Int, val target: Float)

/**
 * The status lines, in order. The first one comes right after the cross-fade. The times are NOT the same on purpose
 * (some lines are quick, some stay longer); change the numbers to speed up or slow down any single line.
 */
private val LoaderStages = listOf(
    LoaderStage(StatusLine("Setting up your camera UI", "This may take a few moments"), 3200, 0.14f),
    LoaderStage(StatusLine("Polishing the liquid glass"), 2400, 0.38f),
    LoaderStage(StatusLine("Warming up Gemini search"), 2000, 0.57f),
    LoaderStage(StatusLine("Tuning zoom and video"), 3400, 0.88f),
    LoaderStage(StatusLine("Almost there"), 2200, 1f)
)

private val LoaderReadyLine = StatusLine("Your camera is ready")

/** Shown while the cross-fade is still running: nothing is written yet. */
private val LoaderBlankLine = StatusLine("")

private val LoaderFallbackBrush = Brush.verticalGradient(
    listOf(Color(0xFF0B1020), Color(0xFF1B2540), Color(0xFF05070F))
)

/** Height of the status-text area in the middle of the screen: room for a title on two lines plus the small line. */
private val StatusTextHeight = 140.dp

/** Size of the white circle of the arrow button. */
private val ReadyDiscSize = 88.dp

/** The glow is a thin ring around the circle: how far it reaches past the circle's edge, and how strong it is (0..1). */
private val ReadyGlowReach = 6.dp
private const val ReadyGlowStrength = 0.08f

/** Size of the whole arrow button, including the room the glow ring needs. */
private val ReadyButtonSize = 120.dp

/**
 * Where the arrow button sits: horizontally centred, and this far above the very bottom edge of the screen, measured
 * to the CENTRE of the button (not from the navigation bar). That is where the under-display ultrasonic fingerprint
 * sensor is on phones like the Samsung Galaxy S series (about 18 mm from the bottom edge), so the thumb that is
 * already resting there taps the button. If the sensor on your phone sits higher or lower, change this one number.
 */
private val FingerprintSensorFromBottom = 112.dp

@Composable
fun CameraUiLoadingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val screenIn = remember { Animatable(0f) }       // the whole screen fades in over the setup page
    val leaveCover = remember { Animatable(0f) }     // a black cover fades in after the arrow is tapped
    val progress = remember { Animatable(0f) }       // 0..1, the slider

    var stage by remember { mutableIntStateOf(-1) }   // -1 while the cross-fade runs: no text yet
    var ready by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var videoReady by remember { mutableStateOf(false) }
    val onContinueNow = rememberUpdatedState(onContinue)

    val percent by remember { derivedStateOf { (progress.value * 100f).roundToInt().coerceIn(0, 100) } }
    val videoReveal by animateFloatAsState(
        targetValue = if (videoReady) 1f else 0f,
        animationSpec = tween(700),
        label = "LoaderVideoReveal"
    )

    // Back does nothing while the camera is being set up (the setup is already saved, nothing is lost).
    BackHandler(enabled = !ready || leaving) { }

    // The timeline: first the cross-fade runs to the very end (no text on screen), then the stages one after another,
    // each for its own length of time, then the ready state.
    LaunchedEffect(Unit) {
        screenIn.animateTo(1f, tween(LoaderCrossFadeMs, easing = FastOutSlowInEasing))
        delay(LoaderTextDelayMs)
        for (i in LoaderStages.indices) {
            stage = i
            progress.animateTo(
                LoaderStages[i].target,
                tween(LoaderStages[i].durationMs, easing = FastOutSlowInEasing)
            )
        }
        delay(350)
        ready = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(leaving) {
        if (leaving) {
            leaveCover.animateTo(1f, tween(320))
            onContinueNow.value()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = screenIn.value }
            .background(Color.Black)
            // swallow touches so the setup page underneath can never be used while this screen is up
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // ---- background video, with the dark gradient shown until the first frame arrives ----
        LoopingVideoBackground(modifier = Modifier.fillMaxSize()) { videoReady = true }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - videoReveal }
                .background(LoaderFallbackBrush)
        )
        // shading so the centred text stays readable on any video (a little darker in the middle and at the bottom,
        // where the arrow button sits)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.28f),
                        0.50f to Color.Black.copy(alpha = 0.42f),
                        1.00f to Color.Black.copy(alpha = 0.62f)
                    )
                )
        )

        // ---- status line: exactly in the middle of the screen. Every change blurs out the old text and
        //      blurs in the new one ----
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(StatusTextHeight),
            contentAlignment = Alignment.Center
        ) {
            BlurSwapText(
                line = when {
                    ready -> LoaderReadyLine
                    stage < 0 -> LoaderBlankLine
                    else -> LoaderStages[stage.coerceAtMost(LoaderStages.lastIndex)].line
                }
            )
        }

        // ---- the slider, low on the screen (its middle is at the same height as the arrow button's middle);
        //      it fades away when everything is ready and the arrow button takes its place ----
        val barAlpha by animateFloatAsState(
            targetValue = if (ready || stage < 0) 0f else 1f,
            animationSpec = tween(380),
            label = "LoaderBarAlpha"
        )
        if (barAlpha > 0.01f) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FingerprintSensorFromBottom - 14.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .height(28.dp)
                    .graphicsLayer {
                        alpha = barAlpha
                        translationY = (1f - barAlpha) * 18.dp.toPx()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleLoaderBar(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "$percent%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp)
                )
            }
        }

        // ---- the arrow button: where the under-display (ultrasonic) fingerprint sensor is, bottom centre ----
        if (ready) {
            ReadyArrowButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FingerprintSensorFromBottom - ReadyButtonSize / 2),
                onClick = {
                    if (!leaving) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        leaving = true
                    }
                }
            )
        }

        // black cover that fades in when the arrow is tapped, so the hand-over to the camera is smooth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = leaveCover.value }
                .background(Color.Black)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Status text with the blur transition
// ---------------------------------------------------------------------------------------------

private val StatusShadowStyle = TextStyle(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 2f),
        blurRadius = 16f
    )
)

/**
 * When [line] changes, the old text blurs out (and fades up a little) while the new text blurs in from a heavy blur
 * to sharp (and rises into place). The blur needs Android 12+; older phones get the fade and the slide only.
 * All of the text is bold.
 */
@Composable
private fun BlurSwapText(line: StatusLine, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = line,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (fadeIn(tween(TextInMs, delayMillis = TextInDelayMs, easing = LinearOutSlowInEasing)) +
                slideInVertically(tween(TextInMs, delayMillis = TextInDelayMs, easing = FastOutSlowInEasing)) { it / 5 })
                .togetherWith(
                    fadeOut(tween(TextOutMs, easing = FastOutLinearInEasing)) +
                        slideOutVertically(tween(TextOutMs, easing = FastOutLinearInEasing)) { -it / 6 }
                )
                .using(SizeTransform(clip = false))
        },
        label = "LoaderStageText"
    ) { current ->
        val blur by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.Visible) {
                    tween(TextInMs, delayMillis = TextInDelayMs, easing = FastOutSlowInEasing)
                } else {
                    tween(TextOutMs, easing = FastOutLinearInEasing)
                }
            },
            label = "LoaderTextBlur"
        ) { state -> if (state == EnterExitState.Visible) 0f else 24f }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    val r = blur
                    renderEffect = if (r > 0.5f && Build.VERSION.SDK_INT >= 31) {
                        BlurEffect(r, r, TileMode.Decal)
                    } else {
                        null
                    }
                }
                // room inside the layer, so the blurred halo is not cut off at the edges
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = current.title,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = StatusShadowStyle
            )
            current.subtitle?.let { sub ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sub,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = StatusShadowStyle
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Google-colour slider
// ---------------------------------------------------------------------------------------------

/**
 * The slider: a track, a fill with the four Google colours (the colours stay put, the fill only reveals more of
 * them), a light that runs along the fill and a round knob at the leading edge. There is no glow.
 */
@Composable
private fun GoogleLoaderBar(
    progress: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "LoaderBarShimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "LoaderShimmer"
    )

    Canvas(modifier = modifier) {
        val barH = 9.dp.toPx()
        val cy = size.height / 2f
        val top = cy - barH / 2f
        val radius = CornerRadius(barH / 2f, barH / 2f)

        // track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(0f, top),
            size = Size(size.width, barH),
            cornerRadius = radius
        )

        val fillW = size.width * progress.value.coerceIn(0f, 1f)
        if (fillW > 0.5f) {
            val brush = Brush.horizontalGradient(
                colors = LoaderGoogleColors,
                startX = 0f,
                endX = size.width
            )
            // the fill
            drawRoundRect(
                brush = brush,
                topLeft = Offset(0f, top),
                size = Size(fillW, barH),
                cornerRadius = radius
            )
            // a light that keeps running along the fill
            val fillPath = Path().apply {
                addRoundRect(RoundRect(0f, top, fillW, top + barH, barH / 2f, barH / 2f))
            }
            clipPath(fillPath) {
                val sx = size.width * (shimmer * 1.5f - 0.25f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startX = sx - 70.dp.toPx(),
                        endX = sx + 70.dp.toPx()
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(fillW, barH)
                )
            }
        }

        // knob
        val knobR = 8.dp.toPx()
        val kx = fillW.coerceIn(knobR, (size.width - knobR).coerceAtLeast(knobR))
        drawCircle(Color.White, radius = knobR, center = Offset(kx, cy))
    }
}

// ---------------------------------------------------------------------------------------------
// The circular arrow button shown when everything is ready
// ---------------------------------------------------------------------------------------------

/**
 * A big plain white circle with only a very light glow: a thin round ring of light that hugs the circle's edge. It pops
 * in with a bounce, the glow very slowly breathes, and it squishes a little while it is pressed. The arrow inside is
 * animated: it rests for a moment, slides out through the right side of the circle, and a new one slides in from
 * the left.
 */
@Composable
private fun ReadyArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 160f))
    }

    val transition = rememberInfiniteTransition(label = "ReadyArrowButton")
    // 0..1..0: the glow gets a little stronger and weaker, slowly
    val glow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ReadyGlow"
    )
    // 0..1, one loop of the arrow: rests (0 to 700 ms), then leaves to the right and the next one arrives from the left
    val arrowCycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1900
                0f at 0
                0f at 700 using FastOutSlowInEasing
                1f at 1900
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "ReadyArrowCycle"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "ReadyPress"
    )

    Box(
        modifier = modifier
            .size(ReadyButtonSize)
            .graphicsLayer {
                val s = pop.value * press
                scaleX = s
                scaleY = s
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // the glow: a thin, very light ring of white that hugs the edge of the circle and fades out within a few dp
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val discR = ReadyDiscSize.toPx() / 2f
            val glowR = discR + ReadyGlowReach.toPx() + 1.dp.toPx() * glow
            val edge = discR / glowR
            val a = ReadyGlowStrength * (0.75f + 0.25f * glow)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = a),
                    edge to Color.White.copy(alpha = a),
                    (edge + (1f - edge) * 0.4f) to Color.White.copy(alpha = a * 0.35f),
                    1f to Color.Transparent,
                    center = c,
                    radius = glowR
                ),
                radius = glowR,
                center = c
            )
        }

        // the white circle; it clips the arrow, so the arrow slides out of and into the circle's edge
        Box(
            modifier = Modifier
                .size(ReadyDiscSize)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open the camera",
                tint = Color(0xFF14151B),
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        val t = arrowCycle
                        val travel = (ReadyDiscSize / 2 + 4.dp).toPx()
                        if (t < 0.5f) {
                            val p = t * 2f                      // leaving: slides right and fades out
                            translationX = travel * p
                            alpha = 1f - p
                        } else {
                            val p = (t - 0.5f) * 2f             // arriving: comes in from the left and fades in
                            translationX = -travel * (1f - p)
                            alpha = p
                        }
                    }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Looping background video
// ---------------------------------------------------------------------------------------------

/**
 * Finds background.mp4. Put it in app/src/main/res/raw/background.mp4 (the usual place) or in
 * app/src/main/assets/background.mp4. Returns null when the file is not there (the dark gradient is shown instead).
 */
private fun openBackgroundVideo(context: Context): AssetFileDescriptor? {
    try {
        val id = context.resources.getIdentifier("background", "raw", context.packageName)
        if (id != 0) return context.resources.openRawResourceFd(id)
    } catch (_: Exception) {
    }
    try {
        return context.assets.openFd("background.mp4")
    } catch (_: Exception) {
    }
    return null
}

/** Plays the video on a TextureView: muted, looping, scaled to fill the screen (centre crop). */
private class VideoBackgroundPlayer(
    private val context: Context,
    private val view: TextureView,
    private val onRendering: () -> Unit
) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var wantPlaying = true
    private var videoW = 0
    private var videoH = 0

    fun start(texture: SurfaceTexture) {
        release()
        val afd = openBackgroundVideo(context) ?: return
        try {
            val s = Surface(texture)
            surface = s
            val mp = MediaPlayer()
            player = mp
            mp.setSurface(s)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                applyCenterCrop()
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onRendering()
                false
            }
            mp.setOnErrorListener { _, _, _ ->
                release()
                true
            }
            mp.setOnPreparedListener { p ->
                prepared = true
                if (wantPlaying) p.start()
                // safety net in case the "first frame" callback never comes on this phone
                view.postDelayed({ onRendering() }, 300L)
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            release()
        } finally {
            try {
                afd.close()
            } catch (_: Exception) {
            }
        }
    }

    /** The TextureView stretches the video to its own size; this scales it back up so it fills it without distortion. */
    fun applyCenterCrop() {
        val vw = view.width.toFloat()
        val vh = view.height.toFloat()
        if (videoW <= 0 || videoH <= 0 || vw <= 0f || vh <= 0f) return
        val scale = max(vw / videoW, vh / videoH)
        val m = Matrix()
        m.setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f)
        view.setTransform(m)
    }

    fun pause() {
        wantPlaying = false
        try {
            if (prepared && player?.isPlaying == true) player?.pause()
        } catch (_: Exception) {
        }
    }

    fun resume() {
        wantPlaying = true
        try {
            if (prepared && player?.isPlaying == false) player?.start()
        } catch (_: Exception) {
        }
    }

    fun release() {
        prepared = false
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            surface?.release()
        } catch (_: Exception) {
        }
        surface = null
    }
}

@Composable
private fun LoopingVideoBackground(
    modifier: Modifier = Modifier,
    onRendering: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val onRenderingNow = rememberUpdatedState(onRendering)
    val holder = remember { arrayOfNulls<VideoBackgroundPlayer>(1) }

    // pause while the app is in the background, resume when it comes back, release when this screen goes away
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> holder[0]?.pause()
                Lifecycle.Event.ON_RESUME -> holder[0]?.resume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder[0]?.release()
            holder[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                val player = VideoBackgroundPlayer(ctx, tv) { onRenderingNow.value() }
                holder[0] = player
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        player.start(st)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        player.applyCenterCrop()
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        player.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
                tv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> player.applyCenterCrop() }
            }
        }
    )
}
