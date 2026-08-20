package dev.fanchao.myscore.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fanchao.myscore.R
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.PageLayoutPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.abs
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler

private class OpenPdf(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer) : AutoCloseable {
    override fun close() {
        synchronized(renderer) { renderer.close() }
        descriptor.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewer(
    score: ScoreDocument,
    initialPage: Int,
    layoutPreference: PageLayoutPreference,
    paperModeEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onLayoutPreferenceChanged: (PageLayoutPreference) -> Unit,
    onPaperModeChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val documentUri = remember(score.uri) { score.uri.toUri() }
    var openPdf by remember(score.uri) { mutableStateOf<OpenPdf?>(null) }
    var error by remember(score.uri) { mutableStateOf<String?>(null) }
    var fullScreen by rememberSaveable(score.uri) { mutableStateOf(false) }
    var readerOptionsExpanded by remember { mutableStateOf(false) }
    var anchorPage by remember(score.uri) { mutableIntStateOf(initialPage) }
    ImmersiveSystemBars(fullScreen)
    BackHandler {
        if (fullScreen) fullScreen = false else onBack()
    }

    DisposableEffect(documentUri) {
        runCatching {
            val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(documentUri, "r"))
            OpenPdf(descriptor, PdfRenderer(descriptor))
        }.onSuccess { openPdf = it }
            .onFailure { error = it.message ?: "Could not open this PDF" }
        onDispose { openPdf?.close() }
    }

    Box(Modifier.fillMaxSize().keepScreenOn()) {
    Scaffold(
        topBar = {
            if (!fullScreen) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to scores",
                            )
                        }
                    },
                    title = { Text(score.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { readerOptionsExpanded = true },
                                modifier = Modifier.semantics {
                                    contentDescription = "Reader options"
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_score_page_24),
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = readerOptionsExpanded,
                                onDismissRequest = { readerOptionsExpanded = false },
                            ) {
                                openPdf?.let { pdf ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_score_page_24),
                                                contentDescription = null,
                                            )
                                        },
                                        text = { Text("${pdf.renderer.pageCount} pages") },
                                        enabled = false,
                                        onClick = {},
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_paper_mode_24),
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = {
                                        if (paperModeEnabled) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    text = { Text("Paper mode") },
                                    onClick = {
                                        readerOptionsExpanded = false
                                        onPaperModeChanged(!paperModeEnabled)
                                    },
                                )
                                HorizontalDivider()
                                PageLayoutPreference.entries.forEach { preference ->
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            if (preference == layoutPreference) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            } else {
                                                Spacer(Modifier.size(18.dp))
                                            }
                                        },
                                        text = {
                                            Text(
                                                when (preference) {
                                                    PageLayoutPreference.Auto -> "Layout: Auto"
                                                    PageLayoutPreference.Single -> "Layout: Single page"
                                                    PageLayoutPreference.Two -> "Layout: Two pages"
                                                },
                                            )
                                        },
                                        onClick = {
                                            readerOptionsExpanded = false
                                            onLayoutPreferenceChanged(preference)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { fullScreen = true },
                            modifier = Modifier.semantics { contentDescription = "Enter full screen" },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_fullscreen_24),
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            openPdf == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                val pdf = requireNotNull(openPdf)
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AdaptivePageLayout(
                        preference = layoutPreference,
                        modifier = Modifier.fillMaxSize(),
                    ) { effectivePagesPerPane ->
                        key(effectivePagesPerPane) {
                            val paneCount = ceil(
                                pdf.renderer.pageCount / effectivePagesPerPane.toDouble(),
                            ).toInt()
                            val pagerState = rememberPagerState(
                                initialPage = (anchorPage / effectivePagesPerPane)
                                    .coerceIn(0, (paneCount - 1).coerceAtLeast(0)),
                                pageCount = { paneCount },
                            )
                            var zoomedPageIndices by remember(effectivePagesPerPane) {
                                mutableStateOf(emptySet<Int>())
                            }
                            LaunchedEffect(pagerState, effectivePagesPerPane) {
                                snapshotFlow { pagerState.settledPage }
                                    .distinctUntilChanged()
                                    .collect { pane ->
                                        anchorPage = pane * effectivePagesPerPane
                                        onPageChanged(anchorPage)
                                    }
                            }
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                beyondViewportPageCount = 1,
                                pageSpacing = 8.dp,
                                userScrollEnabled = isPagerScrollEnabled(
                                    currentPane = pagerState.currentPage,
                                    pagesPerPane = effectivePagesPerPane,
                                    zoomedPageIndices = zoomedPageIndices,
                                ),
                            ) { paneIndex ->
                                val firstPage = paneIndex * effectivePagesPerPane
                                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                                    PdfPage(
                                        renderer = pdf.renderer,
                                        pageIndex = firstPage,
                                        paperModeEnabled = paperModeEnabled,
                                        modifier = Modifier.weight(1f),
                                        onZoomChanged = { zoomed ->
                                            zoomedPageIndices = zoomedPageIndices.withZoomState(
                                                firstPage,
                                                zoomed,
                                            )
                                        },
                                    )
                                    if (
                                        effectivePagesPerPane == 2 &&
                                        firstPage + 1 < pdf.renderer.pageCount
                                    ) {
                                        PdfPage(
                                            renderer = pdf.renderer,
                                            pageIndex = firstPage + 1,
                                            paperModeEnabled = paperModeEnabled,
                                            modifier = Modifier.weight(1f),
                                            onZoomChanged = { zoomed ->
                                                zoomedPageIndices = zoomedPageIndices.withZoomState(
                                                    firstPage + 1,
                                                    zoomed,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
        if (fullScreen) {
            FilledTonalIconButton(
                onClick = { fullScreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .semantics { contentDescription = "Exit full screen" },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen_exit_24),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
internal fun AdaptivePageLayout(
    preference: PageLayoutPreference,
    modifier: Modifier = Modifier,
    content: @Composable (pagesPerPane: Int) -> Unit,
) {
    BoxWithConstraints(modifier) {
        content(pagesPerPane(maxWidth.value, preference))
    }
}

private val PageLayoutPreference.label: String
    get() = when (this) {
        PageLayoutPreference.Auto -> "Auto"
        PageLayoutPreference.Single -> "Single page"
        PageLayoutPreference.Two -> "Two pages"
    }

@Composable
private fun ImmersiveSystemBars(enabled: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enabled) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled) controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    paperModeEnabled: Boolean,
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, renderer, pageIndex, paperModeEnabled) {
        value = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val scale = (1600f / page.width).coerceAtMost(2f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
                        output.eraseColor(if (paperModeEnabled) PAPER_COLOR else Color.WHITE)
                        page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (paperModeEnabled) {
                            applyPaperMode(output)
                        }
                    }
                }
            }
        }
    }
    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }
    var viewportSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    val currentScale by rememberUpdatedState(scale)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val scope = rememberCoroutineScope()
    val offsetXAnimation = remember(pageIndex) { Animatable(0f) }
    val offsetYAnimation = remember(pageIndex) { Animatable(0f) }
    val flingDecay = remember { exponentialDecay<Float>() }
    DisposableEffect(pageIndex) {
        onDispose { currentOnZoomChanged(false) }
    }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        scope.launch {
            offsetXAnimation.stop()
            offsetYAnimation.stop()
        }
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val appliedZoom = newScale / scale
            val centerX = viewportSize.width / 2f
            val centerY = viewportSize.height / 2f
            offsetX = constrainZoomOffset(
                offset = offsetX * appliedZoom +
                    (centroid.x - centerX) * (1f - appliedZoom) +
                    zoomGestureDeltaToOffsetDelta(panChange.x, newScale),
                containerSize = viewportSize.width,
                scale = newScale,
            )
            offsetY = constrainZoomOffset(
                offset = offsetY * appliedZoom +
                    (centroid.y - centerY) * (1f - appliedZoom) +
                    zoomGestureDeltaToOffsetDelta(panChange.y, newScale),
                containerSize = viewportSize.height,
                scale = newScale,
            )
        }
        scale = newScale
        currentOnZoomChanged(newScale > 1f)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val rendered = bitmap
        if (rendered == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .padding(vertical = 8.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .pointerInput(pageIndex) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (currentScale > 1f) {
                                scope.launch {
                                    offsetXAnimation.stop()
                                    offsetYAnimation.stop()
                                    offsetXAnimation.snapTo(0f)
                                    offsetYAnimation.snapTo(0f)
                                }
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                currentOnZoomChanged(false)
                            } else {
                                scale = 2.5f
                                offsetX = constrainZoomOffset(
                                    offset = (size.width / 2f - tap.x) * 1.5f,
                                    containerSize = size.width,
                                    scale = scale,
                                )
                                offsetY = constrainZoomOffset(
                                    offset = (size.height / 2f - tap.y) * 1.5f,
                                    containerSize = size.height,
                                    scale = scale,
                                )
                                currentOnZoomChanged(true)
                            }
                        })
                    }
                    .pointerInput(pageIndex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            scope.launch {
                                offsetXAnimation.stop()
                                offsetYAnimation.stop()
                            }
                            if (currentScale <= 1f) return@awaitEachGesture

                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            var activePointer = down.id
                            var singlePointerPan = true

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.size > 1) singlePointerPan = false
                                val activeChange = event.changes.firstOrNull { it.id == activePointer }
                                    ?: pressedChanges.firstOrNull()?.also { activePointer = it.id }

                                if (
                                    singlePointerPan &&
                                    activeChange != null &&
                                    activeChange.pressed &&
                                    pressedChanges.size == 1
                                ) {
                                    velocityTracker.addPosition(
                                        activeChange.uptimeMillis,
                                        activeChange.position,
                                    )
                                }
                            } while (event.changes.any { it.pressed })

                            if (!singlePointerPan || currentScale <= 1f) return@awaitEachGesture

                            val velocity = velocityTracker.calculateVelocity()
                            if (
                                abs(velocity.x) < ZoomFlingMinimumVelocity &&
                                abs(velocity.y) < ZoomFlingMinimumVelocity
                            ) {
                                return@awaitEachGesture
                            }
                            scope.launch {
                                val maxOffsetX = zoomOffsetLimit(viewportSize.width, scale)
                                val maxOffsetY = zoomOffsetLimit(viewportSize.height, scale)
                                offsetXAnimation.updateBounds(-maxOffsetX, maxOffsetX)
                                offsetYAnimation.updateBounds(-maxOffsetY, maxOffsetY)
                                offsetXAnimation.snapTo(offsetX)
                                offsetYAnimation.snapTo(offsetY)
                                launch {
                                    offsetXAnimation.animateDecay(
                                        zoomGestureDeltaToOffsetDelta(velocity.x, scale),
                                        flingDecay,
                                    ) {
                                        offsetX = value
                                    }
                                }
                                launch {
                                    offsetYAnimation.animateDecay(
                                        zoomGestureDeltaToOffsetDelta(velocity.y, scale),
                                        flingDecay,
                                    ) {
                                        offsetY = value
                                    }
                                }
                            }
                        }
                    }
                    .transformable(
                        state = transformState,
                        canPan = { scale > 1f },
                    ),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private const val ZoomFlingMinimumVelocity = 80f

internal fun isPagerScrollEnabled(
    currentPane: Int,
    pagesPerPane: Int,
    zoomedPageIndices: Set<Int>,
): Boolean {
    val firstPage = currentPane * pagesPerPane
    return (firstPage until firstPage + pagesPerPane).none(zoomedPageIndices::contains)
}

internal fun constrainZoomOffset(offset: Float, containerSize: Int, scale: Float): Float {
    if (scale <= 1f) return 0f
    val limit = zoomOffsetLimit(containerSize, scale)
    return offset.coerceIn(-limit, limit)
}

private fun zoomOffsetLimit(containerSize: Int, scale: Float): Float {
    return (containerSize * (scale - 1f) / 2f).coerceAtLeast(0f)
}

internal fun zoomGestureDeltaToOffsetDelta(delta: Float, scale: Float): Float {
    return if (scale <= 1f) delta else delta * scale
}

private fun Set<Int>.withZoomState(pageIndex: Int, zoomed: Boolean): Set<Int> = when {
    zoomed && pageIndex !in this -> this + pageIndex
    !zoomed && pageIndex in this -> this - pageIndex
    else -> this
}

private const val PAPER_COLOR = 0xFFF2E7C9.toInt()
private const val PAPER_MIN_LIGHTNESS = 215
private const val PAPER_MAX_CHROMA = 18
private const val PAPER_MAX_BLEND = 0.92f

private fun applyPaperMode(bitmap: Bitmap) {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        pixels[index] = paperTonePixel(pixels[index], PAPER_COLOR)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

internal fun paperTonePixel(pixel: Int, paperColor: Int): Int {
    val alpha = pixel ushr 24 and 0xFF
    if (alpha == 0) return pixel
    val red = pixel ushr 16 and 0xFF
    val green = pixel ushr 8 and 0xFF
    val blue = pixel and 0xFF
    val minChannel = minOf(red, green, blue)
    val maxChannel = maxOf(red, green, blue)
    if (minChannel < PAPER_MIN_LIGHTNESS || maxChannel - minChannel > PAPER_MAX_CHROMA) {
        return pixel
    }

    val average = (red + green + blue) / 3f
    val whiteness = ((average - PAPER_MIN_LIGHTNESS) / (255f - PAPER_MIN_LIGHTNESS))
        .coerceIn(0f, 1f)
    val blend = whiteness * PAPER_MAX_BLEND
    val paperRed = paperColor ushr 16 and 0xFF
    val paperGreen = paperColor ushr 8 and 0xFF
    val paperBlue = paperColor and 0xFF

    return (alpha shl 24) or
        (blendChannel(red, paperRed, blend) shl 16) or
        (blendChannel(green, paperGreen, blend) shl 8) or
        blendChannel(blue, paperBlue, blend)
}

private fun blendChannel(source: Int, target: Int, amount: Float): Int =
    (source + (target - source) * amount).toInt().coerceIn(0, 255)
