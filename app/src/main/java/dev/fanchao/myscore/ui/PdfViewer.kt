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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fanchao.myscore.data.ScoreDocument
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
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val documentUri = remember(score.uri) { score.uri.toUri() }
    var openPdf by remember(score.uri) { mutableStateOf<OpenPdf?>(null) }
    var error by remember(score.uri) { mutableStateOf<String?>(null) }
    var fullScreen by rememberSaveable(score.uri) { mutableStateOf(false) }
    ImmersiveSystemBars(fullScreen)
    BackHandler(enabled = fullScreen) { fullScreen = false }

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
                    navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
                    title = { Text(score.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        openPdf?.let { pdf -> Text("${pdf.renderer.pageCount} pages") }
                        IconButton(
                            onClick = { fullScreen = true },
                            modifier = Modifier.semantics { contentDescription = "Enter full screen" },
                        ) { Text("⛶", style = MaterialTheme.typography.titleLarge) }
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
                val windowWidth = with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.width.toDp()
                }
                val pagesPerPane = pagesPerPane(windowWidth.value)
                val paneCount = ceil(pdf.renderer.pageCount / pagesPerPane.toDouble()).toInt()
                val pagerState = rememberPagerState(
                    initialPage = (initialPage / pagesPerPane).coerceIn(0, (paneCount - 1).coerceAtLeast(0)),
                    pageCount = { paneCount },
                )
                LaunchedEffect(pagerState, pagesPerPane) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { pane -> onPageChanged(pane * pagesPerPane) }
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        beyondViewportPageCount = 1,
                        pageSpacing = 8.dp,
                    ) { paneIndex ->
                        val firstPage = paneIndex * pagesPerPane
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                            PdfPage(pdf.renderer, firstPage, Modifier.weight(1f))
                            if (pagesPerPane == 2 && firstPage + 1 < pdf.renderer.pageCount) {
                                PdfPage(pdf.renderer, firstPage + 1, Modifier.weight(1f))
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
            ) { Text("↙", style = MaterialTheme.typography.titleLarge) }
        }
    }
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
private fun PdfPage(renderer: PdfRenderer, pageIndex: Int, modifier: Modifier = Modifier) {
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
                    .pointerInput(pageIndex, scale) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                                offsetX = (size.width / 2f - tap.x) * 1.5f
                                offsetY = (size.height / 2f - tap.y) * 1.5f
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
