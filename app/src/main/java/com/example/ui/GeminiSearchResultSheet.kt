package com.example.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.example.ai.GeminiSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.roundToInt

// Colours for light and dark mode (follows the system). Every floating element gets the same thin
// liquid-glass edge light, flat on purpose: no shadows, no bevels.
private class SheetPal(val dark: Boolean) {
    // RenderEffect blur exists from Android 12. Below that the floating bars get a more solid fill instead,
    // so text scrolling underneath never shows through unblurred.
    val blurOk = Build.VERSION.SDK_INT >= 31
    val text = if (dark) Color.White else Color(0xFF1C1C1E)
    val sub = if (dark) Color(0xFFA1A1AA) else Color(0xFF6B6B72)
    val body = if (dark) Color(0xFFF1F1F4) else Color(0xFF2C2C2E)
    val accent = if (dark) Color(0xFF93C5FD) else Color(0xFF2563EB)
    // The card: a very light pink / yellow / blue wash, 70% opaque over a blurred copy of the photo.
    val tintPink = if (dark) Color(0xFF3B2431) else Color(0xFFFFD9EA)
    val tintYellow = if (dark) Color(0xFF38341F) else Color(0xFFFFF4C9)
    val tintBlue = if (dark) Color(0xFF1F2E48) else Color(0xFFD6E8FF)
    val panelAlpha = 0.20f
    val card = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    // flat colour with the same feel as the wash, only used for the soft fade under the bottom bars
    val panel = if (dark) Color(0xFF2A2833) else Color(0xFFF3EDFB)
    val bar = when {
        blurOk && dark -> Color(0xFF2C2C2E).copy(alpha = 0.14f)
        blurOk -> Color.White.copy(alpha = 0.16f)
        dark -> Color(0xFF2C2C2E).copy(alpha = 0.88f)
        else -> Color.White.copy(alpha = 0.88f)
    }
    val chip = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val scrim = if (dark) Color.Black else Color.White
    val glow = if (dark) Color.White else Color(0xFF2563EB)
    val sendFill = if (dark) Color.White else Color(0xFF1C1C1E)
    val onSend = if (dark) Color.Black else Color.White
    val sendIdleFill = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    val sendIdleIcon = if (dark) Color.White.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.35f)
}

private val LocalPal = staticCompositionLocalOf { SheetPal(true) }
private val ErrorFill = Color(0xFF3A1D20).copy(alpha = 0.70f)

// ---------------------------------------------------------------------------------------------
// Text clean-up: the model must not show * or #, so anything that slips through is turned into
// plain text and bullet points (• ) here as well.
// ---------------------------------------------------------------------------------------------

fun cleanAiText(raw: String): String {
    val lines = raw.replace("\r", "").split("\n").map { line ->
        var l = line.trim()
        l = l.replace(Regex("^#{1,6}\\s*"), "")
        l = l.replace(Regex("^[*\\-\u2022]\\s+"), "\u2022 ")
        l = l.replace("**", "").replace("__", "").replace("`", "")
        l = l.replace("*", "").replace("#", "")
        l.trim()
    }.filterNot { Regex("^(TITLE|CATEGORY)\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .map { it.replace(Regex("^(SUMMARY|OVERVIEW)\\s*:\\s*", RegexOption.IGNORE_CASE), "") }
    return lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
}

fun cleanAiTitle(raw: String): String =
    raw.replace("*", "").replace("#", "")
        .replace(Regex("^\\s*TITLE\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (ignored: Exception) {}
}

/**
 * Bottom padding that lifts content exactly above the keyboard / navigation bar, without double counting.
 * [gapBelowPx] is how far this screen already ends above the bottom of the window (non-zero when the
 * window itself was resized for the keyboard). Read in the layout phase, so animating the keyboard
 * never recomposes anything.
 */
private fun Modifier.liftAboveKeyboard(
    ime: WindowInsets,
    nav: WindowInsets,
    density: Density,
    gapBelowPx: () -> Int
): Modifier = this.layout { measurable, constraints ->
    val pad = (max(nav.getBottom(density), ime.getBottom(density)) - gapBelowPx()).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(vertical = -pad))
    layout(placeable.width, placeable.height + pad) { placeable.placeRelative(0, 0) }
}

/** One follow-up question and its answer. The answer streams into the same card. */
data class FollowUpTurn(
    val id: Long,
    val question: String,
    val answer: String? = null,
    val loading: Boolean = true,
    val error: String? = null
)

/** Soft blur for any Android version: halve the photo until it is tiny; drawn back up with filtering it reads as a blur. */
private fun softBlurFrame(src: Bitmap): ImageBitmap? = try {
    var cur = src
    var w = src.width
    var h = src.height
    while (w > 90 && h > 90) {
        val nw = max(w / 2, 1)
        val nh = max(h / 2, 1)
        val next = Bitmap.createScaledBitmap(cur, nw, nh, true)
        if (cur !== src) cur.recycle()
        cur = next
        w = nw
        h = nh
    }
    cur.asImageBitmap()
} catch (e: Throwable) {
    null
}

/** Draws [img] centre-cropped into a [dstW] x [dstH] area at the current origin. */
private fun DrawScope.drawFrameCropInto(img: ImageBitmap, dstW: Float, dstH: Float) {
    if (img.width <= 0 || img.height <= 0 || dstW <= 0f || dstH <= 0f) return
    val scale = max(dstW / img.width, dstH / img.height)
    val srcW = (dstW / scale).toInt().coerceIn(1, img.width)
    val srcH = (dstH / scale).toInt().coerceIn(1, img.height)
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(dstW.toInt(), dstH.toInt()),
        filterQuality = FilterQuality.High
    )
}

@Composable
private fun ThinkingDots() {
    val pal = LocalPal.current
    val t = rememberInfiniteTransition(label = "FollowUpDots")
    val phase = t.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "FollowUpDotsPhase"
    )
    val dotColor = pal.text
    Canvas(modifier = Modifier.size(width = 34.dp, height = 12.dp)) {
        val r = 3.dp.toPx()
        for (i in 0..2) {
            val d = (phase.value - i + 3f) % 3f
            val a = (1f - d / 1.6f).coerceIn(0.25f, 1f)
            drawCircle(dotColor.copy(alpha = a), r, Offset(r + i * 12.dp.toPx(), size.height / 2f))
        }
    }
}

/** The follow-up conversation, shown inside the card under the first answer. */
@Composable
private fun FollowUpThread(turns: List<FollowUpTurn>, onPlaced: (Long, Float) -> Unit) {
    val pal = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        turns.forEach { turn ->
            key(turn.id) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onPlaced(turn.id, it.positionInRoot().y) },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = turn.question,
                            color = pal.onSend,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(pal.sendFill)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    SectionCard {
                        when {
                            turn.loading -> ThinkingDots()
                            turn.error != null -> Text(
                                text = turn.error,
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            else -> AiText(turn.answer.orEmpty())
                        }
                    }
                }
            }
        }
    }
}

/** Blurred glass bar: draws the blurred copy of whatever is behind it (see the recorder in the sheet). */
@Composable
private fun GlassBar(
    glass: SettingsGlass,
    pal: SheetPal,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.settingsGlassBar(shape, glass, pal.bar, !pal.dark),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Draws the frozen camera frame centre-cropped over the whole draw area (used only for the blur copy). */
private fun DrawScope.drawFrameCrop(img: ImageBitmap) {
    if (img.width <= 0 || img.height <= 0) return
    val scale = max(size.width / img.width, size.height / img.height)
    val srcW = (size.width / scale).toInt().coerceIn(1, img.width)
    val srcH = (size.height / scale).toInt().coerceIn(1, img.height)
    drawImage(
        image = img,
        srcOffset = IntOffset((img.width - srcW) / 2, (img.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt(), size.height.toInt())
    )
}

@Composable
private fun AiText(text: String, fontSize: TextUnit = 14.5.sp) {
    val pal = LocalPal.current
    val lines = remember(text) { cleanAiText(text).split("\n") }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        lines.forEach { line ->
            when {
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                line.startsWith("\u2022 ") -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("\u2022", color = pal.accent, fontSize = fontSize, fontWeight = FontWeight.Bold)
                    Text(
                        text = line.removePrefix("\u2022 "),
                        color = pal.body,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.42f,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> Text(
                    text = line,
                    color = pal.text,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.42f
                )
            }
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassBar(RoundedCornerShape(20.dp), pal.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    val pal = LocalPal.current
    Text(text = text, color = pal.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun GeminiSearchResultSheet(
    result: GeminiSearchResult,
    capturedBitmap: Bitmap?,
    onClose: () -> Unit,
    onSearchAgain: () -> Unit,
    onFollowUp: (String) -> Unit = {},
    frameBitmap: Bitmap? = null,
    images: List<WebImageResult> = emptyList(),
    imagesLoading: Boolean = false,
    imagesError: String? = null,
    followUps: List<FollowUpTurn> = emptyList()
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val pal = remember(dark) { SheetPal(dark) }
    SystemBarIcons(darkIcons = !dark)
    val thumb = remember(capturedBitmap) { capturedBitmap?.asImageBitmap() }
    val frameImg = remember(frameBitmap) { frameBitmap?.asImageBitmap() }
    // Two recordings of the sheet (normal + blurred). The bars draw the blurred one behind themselves.
    val glassContent = rememberGraphicsLayer()
    val glassBlurred = rememberGraphicsLayer()
    // This sheet records its top AND bottom strips into the one blurred layer; the bars read glass.blurred.
    val glass = remember(glassContent, glassBlurred) { SettingsGlass(glassContent, glassBlurred) }
    SideEffect {
        glassBlurred.renderEffect = if (Build.VERSION.SDK_INT >= 31) BlurEffect(56f, 56f, TileMode.Clamp) else null
    }
    var followUp by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    var gapBelowPx by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // 0 -> 1 on open: no sliding. The card fades in while a glow spreads out from the centre of the screen.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }

    // 0 = half height (peek), 1 = full height. Plain state, only read in graphicsLayer: dragging never recomposes.
    var expandF by remember { mutableFloatStateOf(0f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()

    // Blurred copy of the photo that sits behind the card (works on every Android version)
    var blurredFrame by remember(frameBitmap) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frameBitmap) {
        blurredFrame = frameBitmap?.let { withContext(Dispatchers.Default) { softBlurFrame(it) } }
    }

    // A new follow-up opens the card to full height and scrolls to the new question; the answer appears right under it.
    var scrollTopRoot by remember { mutableFloatStateOf(0f) }
    val turnTops = remember { HashMap<Long, Float>() }
    LaunchedEffect(followUps.size) {
        val last = followUps.lastOrNull() ?: return@LaunchedEffect
        animJob?.cancel()
        animJob = scope.launch {
            animate(expandF, 1f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { v, _ -> expandF = v }
        }
        delay(420)
        val y = turnTops[last.id]
        if (y != null) {
            val target = (scrollState.value + (y - scrollTopRoot) - with(density) { 12.dp.toPx() }).roundToInt().coerceAtLeast(0)
            scrollState.animateScrollTo(target)
        }
    }

    val sendFollowUp = {
        val q = followUp.trim()
        if (q.isNotEmpty()) {
            followUp = ""
            keyboard?.hide()
            focusManager.clearFocus()
            onFollowUp(q)
        }
    }

    // Speech to text through the system voice dialog (no extra permission needed).
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) followUp = (followUp + " " + said).trim()
        }
    }
    val startVoice = {
        try {
            speechLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about this photo")
                }
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Speech recognition isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalPal provides pal) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                val bottom = it.positionInWindow().y + it.size.height
                gapBelowPx = max(0, view.rootView.height - bottom.roundToInt())
            }
            // Swallow touches so nothing underneath (camera controls) can be pressed through the sheet.
            // Tapping empty space also closes the keyboard, which brings the bottom bars back into view.
            .pointerInput(Unit) {
                detectTapGestures {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
    ) {
        val fullH = maxHeight * 0.90f
        val peekH = maxHeight * 0.56f
        val rangePx = with(density) { (fullH - peekH).toPx() }
        val screenHpx = with(density) { maxHeight.toPx() }
        val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        val washColors = remember(pal) {
            listOf(pal.tintPink, pal.tintYellow, pal.tintBlue).map { it.copy(alpha = pal.panelAlpha) }
        }
        val edgeBrush = remember(pal) {
            Brush.linearGradient(
                if (pal.dark) listOf(
                    Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.40f)
                ) else listOf(
                    Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.08f),
                    Color.Black.copy(alpha = 0.08f), Color.White.copy(alpha = 0.75f)
                )
            )
        }

        val settleTo: (Float) -> Unit = { velocityY ->
            val target = when {
                velocityY < -700f -> 1f
                velocityY > 700f -> 0f
                expandF > 0.5f -> 1f
                else -> 0f
            }
            animJob?.cancel()
            animJob = scope.launch {
                animate(expandF, target, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { v, _ ->
                    expandF = v
                }
            }
        }

        // Pulling the content up first lifts the card to full height, then scrolls; pulling down scrolls
        // to the top first, then lowers the card back to half.
        val connection = remember(rangePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (rangePx <= 0f) return Offset.Zero
                    if (dy < 0f && expandF < 1f) {
                        val newV = (expandF + (-dy) / rangePx).coerceAtMost(1f)
                        val used = (newV - expandF) * rangePx
                        animJob?.cancel()
                        expandF = newV
                        return Offset(0f, -used)
                    }
                    if (dy > 0f && expandF > 0f && scrollState.value == 0) {
                        val newV = (expandF - dy / rangePx).coerceAtLeast(0f)
                        val used = (expandF - newV) * rangePx
                        animJob?.cancel()
                        expandF = newV
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (expandF > 0.001f && expandF < 0.999f) {
                        settleTo(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val o = it.positionInRoot()
                    glass.originX = o.x
                    glass.originY = o.y
                }
                .drawWithContent {
                    // The card itself must always end up on screen, so it is drawn directly as a fallback
                    // if recording into the layer throws; the blurred copy only shows behind the floating
                    // close/dismiss/search-again/follow-up bars, so that part is best-effort only.
                    val recorded = try {
                        glassContent.record { this@drawWithContent.drawContent() }
                        true
                    } catch (_: Throwable) {
                        false
                    }
                    if (recorded) {
                        drawLayer(glassContent)
                        try {
                            glassBlurred.record {
                                frameImg?.let { drawFrameCrop(it) }
                                drawLayer(glassContent)
                            }
                        } catch (_: Throwable) {}
                    } else {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
        // Soft dimming only at the very top so the close button stays readable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            pal.scrim.copy(alpha = 0.60f),
                            pal.scrim.copy(alpha = 0.42f),
                            pal.scrim.copy(alpha = 0.22f),
                            pal.scrim.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // The result panel: full height, pushed down so only half shows. Pull it up to see everything.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(fullH)
                .graphicsLayer {
                    translationY = (1f - expandF) * rangePx
                    val e = enter.value.coerceIn(0f, 1f)
                    alpha = e
                    scaleX = 0.965f + 0.035f * e
                    scaleY = 0.965f + 0.035f * e
                }
                .nestedScroll(connection)
                .clip(panelShape)
                .drawBehind {
                    // 1) the photo, blurred, kept where it is on screen while the card slides over it
                    val img = blurredFrame
                    if (Build.VERSION.SDK_INT < 31 && img != null) {
                        val panelTop = (screenHpx - size.height) + (1f - expandF) * rangePx
                        translate(top = -panelTop) { drawFrameCropInto(img, size.width, screenHpx) }
                    }
                    // 2) a very light pink, yellow and blue wash at 70% opacity
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = washColors,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height)
                        )
                    )
                }
                .border(0.75.dp, edgeBrush, panelShape)
        ) {
            if (Build.VERSION.SDK_INT >= 31 && frameImg != null) {
                // Real GPU blur of the live photo behind the panel (no fake transparency)
                val blurPx = with(density) { 30.dp.toPx() }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp) }
                ) {
                    val panelTop = (screenHpx - size.height) + (1f - expandF) * rangePx
                    translate(top = -panelTop) { drawFrameCropInto(frameImg, size.width, screenHpx) }
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: handle + thumbnail + title. Dragging it (or tapping it) moves the panel.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { d ->
                                if (rangePx > 0f) {
                                    animJob?.cancel()
                                    expandF = (expandF - d / rangePx).coerceIn(0f, 1f)
                                }
                            },
                            onDragStopped = { v -> settleTo(v) }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { settleTo(if (expandF < 0.5f) -2000f else 2000f) }
                        }
                        .padding(top = 10.dp, bottom = 12.dp, start = 18.dp, end = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(44.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(pal.text.copy(alpha = 0.25f))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb,
                                contentDescription = "Analyzed area",
                                modifier = Modifier
                                    .size(64.dp)
                                    .liquidGlassBar(RoundedCornerShape(16.dp), Color.Transparent),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (result.category.isNotBlank()) {
                                Text(
                                    text = cleanAiTitle(result.category).uppercase(),
                                    color = pal.accent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                            }
                            Text(
                                text = cleanAiTitle(result.title).ifBlank { "Analyzed Item" },
                                color = pal.text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { scrollTopRoot = it.positionInRoot().y }
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!result.isSuccess) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassBar(RoundedCornerShape(20.dp), ErrorFill)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444)
                                )
                                Text(
                                    text = "Unable to Complete Search",
                                    color = Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = result.errorMessage
                                    ?: "Please verify network connectivity and ensure your Gemini API key is configured.",
                                color = Color(0xFFFECACA),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    } else {
                        SectionCard {
                            SectionLabel("\u2728 Overview")
                            AiText(result.summary)
                        }

                        if (result.keyPoints.isNotEmpty()) {
                            SectionCard {
                                SectionLabel("\uD83D\uDCCC Key details")
                                AiText(
                                    result.keyPoints.joinToString("\n") {
                                        "\u2022 " + cleanAiText(it).removePrefix("\u2022 ")
                                    }
                                )
                            }
                        }

                        val sites = images.distinctBy { it.site }
                        if (sites.isNotEmpty()) {
                            SectionCard {
                                SectionLabel("\uD83D\uDD17 Open on the web")
                                sites.forEach { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bouncyClickable { openUrl(context, s.pageUrl) }
                                            .liquidGlassBar(RoundedCornerShape(14.dp), pal.chip)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = s.site,
                                                color = pal.text,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (s.title.isNotBlank()) {
                                                Text(
                                                    text = s.title,
                                                    color = pal.sub,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.OpenInNew,
                                            contentDescription = "Open",
                                            tint = pal.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (followUps.isNotEmpty() && result.isSuccess) {
                        FollowUpThread(
                            turns = followUps,
                            onPlaced = { id, y -> turnTops[id] = y }
                        )
                    }
                    // room so the last card can scroll clear of the floating bars
                    Spacer(modifier = Modifier.height(230.dp))
                }
            }
        }

        // Fade under the bottom bars so scrolling content melts away instead of running into them.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(250.dp)
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            pal.panel.copy(alpha = 0.10f),
                            pal.panel.copy(alpha = 0.24f),
                            pal.panel.copy(alpha = 0.40f),
                            pal.panel.copy(alpha = 0.52f)
                        )
                    )
                )
        )

        }

        // Floating close button (top right)
        GlassBar(glass = glass, pal = pal, 
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 16.dp)
                .size(44.dp)
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .bouncyClickable(onClose)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = pal.text,
                modifier = Modifier.size(20.dp)
            )
        }

        // Bottom floating bars: follow-up question (with mic) on top, Dismiss + Search Again below.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .liftAboveKeyboard(imeInsets, navInsets, density) { gapBelowPx }
                .graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassBar(glass = glass, pal = pal, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = pal.text,
                    modifier = Modifier.size(18.dp)
                )
                BasicTextField(
                    value = followUp,
                    onValueChange = { followUp = it },
                    singleLine = true,
                    textStyle = TextStyle(color = pal.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(pal.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendFollowUp() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (followUp.isEmpty()) {
                                Text(
                                    text = "Ask a follow-up question",
                                    style = TextStyle(
                                        color = pal.text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        shadow = Shadow(
                                            color = pal.glow.copy(alpha = 0.95f),
                                            offset = Offset.Zero,
                                            blurRadius = 22f
                                        )
                                    ),
                                    maxLines = 1
                                )
                            }
                            inner()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyClickable { startVoice() }
                        .liquidGlassBar(CircleShape, pal.chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Speak",
                        tint = pal.text,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyClickable { sendFollowUp() }
                        .liquidGlassBar(
                            CircleShape,
                            if (followUp.isBlank()) pal.sendIdleFill else pal.sendFill
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (followUp.isBlank()) pal.sendIdleIcon else pal.onSend,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassBar(glass = glass, pal = pal, 
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .bouncyClickable(onClose)
                ) {
                    Text("Dismiss", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                GlassBar(glass = glass, pal = pal, 
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .bouncyClickable(onSearchAgain)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = pal.text,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Again", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

    }
    }
}
