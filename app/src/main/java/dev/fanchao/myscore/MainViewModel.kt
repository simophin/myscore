package dev.fanchao.myscore

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.fanchao.myscore.data.DownloadedScore
import dev.fanchao.myscore.data.LibraryEntry
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.ScoreLibraryRepository
import dev.fanchao.myscore.data.UserSettingsRepository
import dev.fanchao.myscore.data.PageLayoutPreference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class LibraryUiState(
    val initialized: Boolean = false,
    val loading: Boolean = false,
    val scores: List<ScoreDocument> = emptyList(),
    val message: String? = null,
    val downloading: Boolean = false,
    val entries: List<LibraryEntry> = emptyList(),
    val path: List<FolderLocation> = emptyList(),
    val clipboard: FileClipboard? = null,
    val downloadedScore: DownloadedScore? = null,
)

data class FolderLocation(val uri: String, val name: String)

enum class FileTransferMode { Copy, Move }

data class FileClipboard(val entry: LibraryEntry, val mode: FileTransferMode)

data class MainUiState(
    val libraryUri: String? = null,
    val lastScoreUri: String? = null,
    val library: LibraryUiState = LibraryUiState(),
    val paperModeEnabled: Boolean = false,
)

class MainViewModel(
    private val settings: UserSettingsRepository,
    private val library: ScoreLibraryRepository,
) : ViewModel() {

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<MainUiState> = combine(
        settings.libraryUri,
        settings.lastScoreUri,
        settings.paperModeEnabled,
        _libraryState,
    ) { libraryUri, lastScoreUri, paperModeEnabled, libraryState ->
        MainUiState(libraryUri, lastScoreUri, libraryState, paperModeEnabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )
    private var refreshJob: Job? = null

    fun setLibraryFolder(uri: String) {
        viewModelScope.launch {
            settings.setLibraryUri(uri)
            _libraryState.value = LibraryUiState()
            refresh(uri)
        }
    }

    fun refresh(uri: String? = uiState.value.libraryUri) {
        if (uri == null) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(loading = true, message = null)
            delay(250)
            val previous = _libraryState.value
            val currentDirectory = previous.path.lastOrNull()?.uri
            runCatching {
                val listing = library.listDirectory(uri, currentDirectory)
                val scores = library.findScores(uri)
                listing to scores
            }
                .recoverCatching {
                    val listing = library.listDirectory(uri)
                    val scores = library.findScores(uri)
                    listing to scores
                }
                .onSuccess { (listing, scores) ->
                    val retainedPath = previous.path.takeIf { it.lastOrNull()?.uri == listing.directoryUri }
                    _libraryState.value = previous.copy(
                        initialized = true,
                        loading = false,
                        scores = scores,
                        entries = listing.entries,
                        path = retainedPath ?: listOf(FolderLocation(listing.directoryUri, listing.directoryName)),
                        message = null,
                    )
                }
                .onFailure {
                    _libraryState.value = previous.copy(
                        initialized = true,
                        loading = false,
                        message = it.message ?: "Could not read the score folder",
                    )
                }
        }
    }

    fun openDirectory(entry: LibraryEntry) {
        if (!entry.isDirectory) return
        loadDirectory(entry.uri, _libraryState.value.path + FolderLocation(entry.uri, entry.name))
    }

    fun navigateUp() {
        val path = _libraryState.value.path
        if (path.size > 1) loadDirectory(path[path.lastIndex - 1].uri, path.dropLast(1))
    }

    fun navigateToPath(index: Int) {
        val path = _libraryState.value.path
        if (index in path.indices && index != path.lastIndex) {
            loadDirectory(path[index].uri, path.take(index + 1))
        }
    }

    fun stageCopy(entry: LibraryEntry) = stage(entry, FileTransferMode.Copy)

    fun stageMove(entry: LibraryEntry) = stage(entry, FileTransferMode.Move)

    fun clearClipboard() {
        _libraryState.value = _libraryState.value.copy(clipboard = null)
    }

    fun createFolder(name: String) {
        val treeUri = uiState.value.libraryUri ?: return
        val parentUri = _libraryState.value.path.lastOrNull()?.uri ?: return
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(loading = true, message = null)
            library.createDirectory(treeUri, parentUri, name)
                .onSuccess { refresh(treeUri) }
                .onFailure { failure ->
                    _libraryState.value = _libraryState.value.copy(
                        loading = false,
                        message = failure.message ?: "Could not create folder",
                    )
                }
        }
    }

    fun rename(entry: LibraryEntry, name: String) {
        val treeUri = uiState.value.libraryUri ?: return
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(loading = true, message = null)
            library.renameEntry(treeUri, entry.uri, name)
                .onSuccess {
                    if (_libraryState.value.clipboard?.entry?.uri == entry.uri) {
                        _libraryState.value = _libraryState.value.copy(clipboard = null)
                    }
                    refresh(treeUri)
                }
                .onFailure { failure ->
                    _libraryState.value = _libraryState.value.copy(
                        loading = false,
                        message = failure.message ?: "Could not rename ${entry.name}",
                    )
                }
        }
    }

    fun paste() {
        val treeUri = uiState.value.libraryUri ?: return
        val state = _libraryState.value
        val clipboard = state.clipboard ?: return
        val destination = state.path.lastOrNull()?.uri ?: return
        viewModelScope.launch {
            _libraryState.value = state.copy(loading = true, message = null)
            val result = when (clipboard.mode) {
                FileTransferMode.Copy -> library.copyEntry(treeUri, clipboard.entry.uri, destination)
                FileTransferMode.Move -> library.moveEntry(treeUri, clipboard.entry.uri, destination)
            }
            result.onSuccess {
                if (clipboard.mode == FileTransferMode.Move) {
                    _libraryState.value = _libraryState.value.copy(clipboard = null)
                }
                refresh(treeUri)
            }.onFailure { failure ->
                _libraryState.value = _libraryState.value.copy(
                    loading = false,
                    message = failure.message ?: "Could not paste ${clipboard.entry.name}",
                )
            }
        }
    }

    fun delete(entry: LibraryEntry) {
        val treeUri = uiState.value.libraryUri ?: return
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(loading = true, message = null)
            library.deleteEntry(treeUri, entry.uri)
                .onSuccess {
                    if (_libraryState.value.clipboard?.entry?.uri == entry.uri) {
                        _libraryState.value = _libraryState.value.copy(clipboard = null)
                    }
                    refresh(treeUri)
                }
                .onFailure { failure ->
                    _libraryState.value = _libraryState.value.copy(
                        loading = false,
                        message = failure.message ?: "Could not delete ${entry.name}",
                    )
                }
        }
    }

    private fun stage(entry: LibraryEntry, mode: FileTransferMode) {
        _libraryState.value = _libraryState.value.copy(
            clipboard = FileClipboard(entry, mode),
            message = "${entry.name} ready to ${mode.name.lowercase()}",
        )
    }

    private fun loadDirectory(directoryUri: String, path: List<FolderLocation>) {
        val treeUri = uiState.value.libraryUri ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val previous = _libraryState.value
            _libraryState.value = previous.copy(loading = true, message = null)
            runCatching { library.listDirectory(treeUri, directoryUri) }
                .onSuccess { listing ->
                    _libraryState.value = previous.copy(
                        initialized = true,
                        loading = false,
                        entries = listing.entries,
                        path = path,
                        message = null,
                    )
                }
                .onFailure { failure ->
                    _libraryState.value = previous.copy(
                        loading = false,
                        message = failure.message ?: "Could not open this folder",
                    )
                }
        }
    }

    fun importPdf(source: String) {
        val destination = uiState.value.libraryUri ?: return
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(loading = true, message = null)
            library.importPdf(source, destination)
                .onSuccess { refresh(destination) }
                .onFailure {
                    _libraryState.value = _libraryState.value.copy(
                        loading = false,
                        message = it.message ?: "Could not import the PDF",
                    )
                }
        }
    }

    fun downloadPdf(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookies: String?,
    ) {
        val destination = uiState.value.libraryUri
        if (destination == null) {
            _libraryState.value = _libraryState.value.copy(message = "Choose a score folder before downloading")
            return
        }
        if (_libraryState.value.downloading) return
        viewModelScope.launch {
            _libraryState.value = _libraryState.value.copy(
                downloading = true,
                message = "Downloading score…",
                downloadedScore = null,
            )
            library.downloadPdf(url, userAgent, contentDisposition, mimeType, cookies, destination)
                .onSuccess { downloaded ->
                    val scores = runCatching { library.findScores(destination) }.getOrDefault(_libraryState.value.scores)
                    _libraryState.value = _libraryState.value.copy(
                        initialized = true,
                        scores = scores,
                        loading = false,
                        downloading = false,
                        message = "${downloaded.fileName} downloaded",
                        downloadedScore = downloaded,
                    )
                }
                .onFailure { failure ->
                    _libraryState.value = _libraryState.value.copy(
                        downloading = false,
                        message = failure.message ?: "Download failed",
                        downloadedScore = null,
                    )
                }
        }
    }

    fun readerPage(uri: String) = settings.readerPage(uri)

    fun saveReaderPage(uri: String, page: Int) {
        viewModelScope.launch { settings.setReaderPage(uri, page) }
    }

    fun readerLayout(uri: String) = settings.readerLayout(uri)

    fun saveReaderLayout(uri: String, preference: PageLayoutPreference) {
        viewModelScope.launch { settings.setReaderLayout(uri, preference) }
    }

    fun savePaperModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setPaperModeEnabled(enabled) }
    }

    fun recordOpenedScore(uri: String) {
        viewModelScope.launch { settings.setLastScoreUri(uri) }
    }
}

class MainViewModelFactory(
    private val settings: UserSettingsRepository,
    private val library: ScoreLibraryRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(settings, library) as T
    }
}
