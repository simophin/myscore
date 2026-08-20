package dev.fanchao.myscore.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
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
    onPageChanged: (Int) -> Unit,
    onLayoutPreferenceChanged: (PageLayoutPreference) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val documentUri = remember(score.uri) { score.uri.toUri() }
    var openPdf by remember(score.uri) { mutableStateOf<OpenPdf?>(null) }
    var error by remember(score.uri) { mutableStateOf<String?>(null) }
    var fullScreen by rememberSaveable(score.uri) { mutableStateOf(false) }
    var layoutMenuExpanded by remember { mutableStateOf(false) }
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
                        openPdf?.let { pdf -> Text("${pdf.renderer.pageCount} pages") }
                        Box {
                            IconButton(
                                onClick = { layoutMenuExpanded = true },
                                modifier = Modifier.semantics {
                                    contentDescription = "Page layout: ${layoutPreference.label}"
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_view_week_24),
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = layoutMenuExpanded,
                                onDismissRequest = { layoutMenuExpanded = false },
                            ) {
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
                                        text = { Text(preference.label) },
                                        onClick = {
                                            layoutMenuExpanded = false
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
    modifier: Modifier = Modifier,
    onZoomChanged: (Boolean) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, renderer, pageIndex) {
        value = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val scale = (1600f / page.width).coerceAtMost(2f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
                        output.eraseColor(Color.WHITE)
                        page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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
    DisposableEffect(pageIndex) {
        onDispose { currentOnZoomChanged(false) }
    }
    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            val appliedZoom = newScale / scale
            val centerX = viewportSize.width / 2f
            val centerY = viewportSize.height / 2f
            offsetX = offsetX * appliedZoom + (centroid.x - centerX) * (1f - appliedZoom) + panChange.x
            offsetY = offsetY * appliedZoom + (centroid.y - centerY) * (1f - appliedZoom) + panChange.y
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
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                currentOnZoomChanged(false)
                            } else {
                                scale = 2.5f
                                offsetX = (size.width / 2f - tap.x) * 1.5f
                                offsetY = (size.height / 2f - tap.y) * 1.5f
                                currentOnZoomChanged(true)
                            }
                        })
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

internal fun isPagerScrollEnabled(
    currentPane: Int,
    pagesPerPane: Int,
    zoomedPageIndices: Set<Int>,
): Boolean {
    val firstPage = currentPane * pagesPerPane
    return (firstPage until firstPage + pagesPerPane).none(zoomedPageIndices::contains)
}

private fun Set<Int>.withZoomState(pageIndex: Int, zoomed: Boolean): Set<Int> = when {
    zoomed && pageIndex !in this -> this + pageIndex
    !zoomed && pageIndex in this -> this - pageIndex
    else -> this
}
