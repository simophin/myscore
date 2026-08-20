package dev.fanchao.myscore.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.data.ScoreDocument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
internal fun FindScreen(
    modifier: Modifier,
    webViewHolder: ImslpWebViewHolder,
    hasLibrary: Boolean,
    state: LibraryUiState,
    onDownloadPdf: (String, String?, String?, String?, String?) -> Unit,
    onOpenDownloadedScore: (ScoreDocument) -> Unit,
    searchVisible: Boolean,
    onDismissSearch: () -> Unit,
    onWebViewStateChanged: (FindWebViewState) -> Unit,
    initialPageLoaded: Boolean,
    onInitialPageLoaded: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var webViewState by remember { mutableStateOf(FindWebViewState()) }
    fun updateWebViewState(state: FindWebViewState) {
        webViewState = state
        onWebViewStateChanged(state)
    }
    BackHandler(enabled = webViewState.canGoBack) {
        webViewHolder.goBack()
    }
    fun submitSearch() {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        webView?.loadUrl("https://imslp.org/index.php?search=$encoded&title=Special%3ASearch&go=Go")
        onDismissSearch()
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (state.message != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.message,
                    modifier = Modifier.weight(1f),
                    color = if (state.message.contains("failed", true)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                state.downloadedScore
                    ?.takeIf { state.message == "${it.fileName} downloaded" }
                    ?.let { downloaded ->
                        TextButton(onClick = { onOpenDownloadedScore(downloaded.document) }) {
                            Text("Open")
                        }
                    }
            }
        }
        if (!hasLibrary) {
            Text(
                "Choose a score folder in Settings before downloading.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        ImslpWebView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            holder = webViewHolder,
            onReady = {
                webView = it
                updateWebViewState(it.navigationState())
            },
            onStateChanged = ::updateWebViewState,
            onInitialPageLoaded = onInitialPageLoaded,
            onDownload = onDownloadPdf,
        )
    }
    if (!initialPageLoaded) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading IMSLP")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Preparing the Find browser for the first page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (searchVisible) {
        AlertDialog(
            onDismissRequest = onDismissSearch,
            title = { Text("Search IMSLP") },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Composer, work, or catalogue number") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) submitSearch()
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = ::submitSearch, enabled = query.isNotBlank()) { Text("Search") }
            },
            dismissButton = { TextButton(onClick = onDismissSearch) { Text("Cancel") } },
        )
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled") // IMSLP's interactive download flow requires JavaScript.
private fun ImslpWebView(
    modifier: Modifier,
    holder: ImslpWebViewHolder,
    onReady: (android.webkit.WebView) -> Unit,
    onStateChanged: (FindWebViewState) -> Unit,
    onInitialPageLoaded: () -> Unit,
    onDownload: (String, String?, String?, String?, String?) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            holder.obtain(context) {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageStarted(
                        view: android.webkit.WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        onStateChanged(view.navigationState(isLoading = true))
                    }

                    override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                        super.onPageFinished(view, url)
                        onStateChanged(view.navigationState(isLoading = false))
                        onInitialPageLoaded()
                    }

                    override fun doUpdateVisitedHistory(
                        view: android.webkit.WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onStateChanged(view.navigationState())
                    }

                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean {
                        return when (request.url.scheme?.lowercase()) {
                            "https" -> false
                            "http" -> {
                                view.loadUrl(request.url.buildUpon().scheme("https").build().toString())
                                true
                            }
                            else -> {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                true
                            }
                        }
                    }
                }
                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    onDownload(url, userAgent, contentDisposition, mimeType, cookies)
                }
                onReady(this)
            }
        },
        onRelease = { view ->
            view.setDownloadListener(null)
        },
    )
}

internal class ImslpWebViewHolder {
    private var webView: android.webkit.WebView? = null

    fun obtain(
        context: Context,
        configure: android.webkit.WebView.() -> Unit,
    ): android.webkit.WebView {
        webView?.let { return it.apply(configure) }
        return android.webkit.WebView(context).apply {
            configure()
            loadUrl("https://imslp.org/")
        }.also { webView = it }
    }

    fun destroy() {
        webView?.run {
            stopLoading()
            setDownloadListener(null)
            destroy()
        }
        webView = null
    }

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }
}

internal data class FindWebViewState(
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
)

private fun android.webkit.WebView.navigationState(isLoading: Boolean = progress < 100) = FindWebViewState(
    canGoBack = canGoBack(),
    canGoForward = canGoForward(),
    isLoading = isLoading,
)
