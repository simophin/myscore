package dev.fanchao.myscore.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.URLUtil
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ScoreDocument(
    val uri: String,
    val title: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

data class LibraryEntry(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

data class DirectoryListing(
    val directoryUri: String,
    val directoryName: String,
    val entries: List<LibraryEntry>,
)

interface ScoreLibraryRepository {
    suspend fun findScores(treeUri: String): List<ScoreDocument>
    suspend fun listDirectory(treeUri: String, directoryUri: String? = null): DirectoryListing
    suspend fun createDirectory(treeUri: String, parentDirectoryUri: String, name: String): Result<Unit>
    suspend fun renameEntry(treeUri: String, entryUri: String, name: String): Result<Unit>
    suspend fun deleteEntry(treeUri: String, entryUri: String): Result<Unit>
    suspend fun copyEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String): Result<Unit>
    suspend fun moveEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String): Result<Unit>
    suspend fun importPdf(source: String, treeUri: String): Result<Unit>
    suspend fun downloadPdf(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookies: String?,
        treeUri: String,
    ): Result<String>
}

class AndroidScoreLibraryRepository(private val context: Context) : ScoreLibraryRepository {
    override suspend fun findScores(treeUri: String): List<ScoreDocument> = withContext(Dispatchers.IO) {
        val tree = treeUri.toUri()
        val root = DocumentFile.fromTreeUri(context, tree) ?: return@withContext emptyList()
        buildList { collectPdfFiles(tree, root.uri, this) }
            .sortedBy { it.title.lowercase() }
    }

    override suspend fun listDirectory(
        treeUri: String,
        directoryUri: String?,
    ): DirectoryListing = withContext(Dispatchers.IO) {
        val tree = treeUri.toUri()
        val root = requireRoot(treeUri)
        val directory = directoryUri?.toUri() ?: root.uri
        require(directory == root.uri || belongsToTree(tree, directory)) {
            "Folder is outside the score library"
        }
        val directoryName = queryDirectoryName(directory)
        DirectoryListing(
            directoryUri = directory.toString(),
            directoryName = directoryName,
            entries = queryDirectoryEntries(tree, directory)
                .sortedWith(compareByDescending<LibraryEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        )
    }

    override suspend fun createDirectory(
        treeUri: String,
        parentDirectoryUri: String,
        name: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = requireRoot(treeUri)
            val parent = requireDirectory(root, parentDirectoryUri)
            val validName = validateName(name)
            requireNameAvailable(parent, validName)
            requireNotNull(parent.createDirectory(validName)) { "Could not create $validName" }
            Unit
        }
    }

    override suspend fun renameEntry(
        treeUri: String,
        entryUri: String,
        name: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = requireRoot(treeUri)
            val entryWithParent = requireNotNull(findDescendantWithParent(root, entryUri.toUri())) {
                "Item is outside the score library"
            }
            val entry = entryWithParent.first
            val parent = requireNotNull(entryWithParent.second) { "The score library root cannot be renamed" }
            val validName = validateName(name)
            require(entry.isDirectory || validName.endsWith(".pdf", ignoreCase = true)) {
                "Score names must end in .pdf"
            }
            if (entry.name == validName) return@runCatching
            requireNameAvailable(parent, validName, entry.uri)
            require(entry.renameTo(validName)) {
                "The storage provider could not rename ${entry.name ?: "this item"}"
            }
        }
    }

    override suspend fun deleteEntry(treeUri: String, entryUri: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = requireRoot(treeUri)
                val entry = requireNotNull(findDescendant(root, entryUri.toUri())) {
                    "Item is outside the score library"
                }
                require(entry.uri != root.uri) { "The score library root cannot be deleted" }
                require(entry.delete()) { "The storage provider could not delete ${entry.name ?: "this item"}" }
            }
        }

    override suspend fun copyEntry(
        treeUri: String,
        entryUri: String,
        destinationDirectoryUri: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = requireRoot(treeUri)
            val source = requireNotNull(findDescendant(root, entryUri.toUri())) {
                "Item is outside the score library"
            }
            val destination = requireDirectory(root, destinationDirectoryUri)
            validateTransfer(source, destination)
            if (!tryNativeCopy(source, destination)) copyRecursively(source, destination)
        }
    }

    override suspend fun moveEntry(
        treeUri: String,
        entryUri: String,
        destinationDirectoryUri: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = requireRoot(treeUri)
            val sourceWithParent = requireNotNull(findDescendantWithParent(root, entryUri.toUri())) {
                "Item is outside the score library"
            }
            val source = sourceWithParent.first
            val sourceParent = requireNotNull(sourceWithParent.second) { "The score library root cannot be moved" }
            val destination = requireDirectory(root, destinationDirectoryUri)
            validateTransfer(source, destination)
            if (sourceParent.uri == destination.uri) return@runCatching
            if (!tryNativeMove(source, sourceParent, destination)) {
                val created = copyRecursively(source, destination)
                if (!source.delete()) {
                    created.delete()
                    error("The storage provider copied but could not remove the original")
                }
            }
        }
    }

    override suspend fun importPdf(source: String, treeUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val source = source.toUri()
            val treeUri = treeUri.toUri()
            val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
                "The score folder is no longer available"
            }
            val sourceName = queryDisplayName(context.contentResolver, source) ?: "Imported score.pdf"
            val safeName = if (sourceName.endsWith(".pdf", ignoreCase = true)) sourceName else "$sourceName.pdf"
            val destination = requireNotNull(root.createFile("application/pdf", safeName)) {
                "Could not create the PDF in the score folder"
            }
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Could not read the selected PDF" }
                context.contentResolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output) { "Could not write to the score folder" }
                    input.copyTo(output)
                }
            }
            Unit
        }
    }

    override suspend fun downloadPdf(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookies: String?,
        treeUri: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = treeUri.toUri()
            val requestedUrl = URL(url)
            require(requestedUrl.protocol == "https") { "Only secure downloads are supported" }
            require(requestedUrl.host == "imslp.org" || requestedUrl.host.endsWith(".imslp.org")) {
                "Only downloads started on IMSLP are supported"
            }
            val root = requireNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
                "The score folder is no longer available"
            }
            val connection = (requestedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                userAgent?.let { setRequestProperty("User-Agent", it) }
                cookies?.let { setRequestProperty("Cookie", it) }
            }
            try {
                connection.connect()
                require(connection.responseCode in 200..299) {
                    "Download failed (HTTP ${connection.responseCode})"
                }
                val responseDisposition = connection.getHeaderField("Content-Disposition") ?: contentDisposition
                val responseType = connection.contentType?.substringBefore(';') ?: mimeType ?: "application/pdf"
                val guessed = URLUtil.guessFileName(connection.url.toString(), responseDisposition, responseType)
                val fileName = sanitizePdfName(guessed)
                val destination = requireNotNull(root.createFile("application/pdf", fileName)) {
                    "Could not create the PDF in the score folder"
                }
                try {
                    connection.inputStream.use { input ->
                        context.contentResolver.openOutputStream(destination.uri, "w").use { output ->
                            requireNotNull(output) { "Could not write to the score folder" }
                            input.copyTo(output)
                        }
                    }
                } catch (failure: Throwable) {
                    destination.delete()
                    throw failure
                }
                destination.name ?: fileName
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun collectPdfFiles(tree: Uri, directory: Uri, destination: MutableList<ScoreDocument>) {
        queryDirectoryEntries(tree, directory).forEach { entry ->
            if (entry.isDirectory) {
                collectPdfFiles(tree, entry.uri.toUri(), destination)
            } else {
                destination += ScoreDocument(
                    uri = entry.uri,
                    title = entry.name.removeSuffix(".pdf").removeSuffix(".PDF"),
                    sizeBytes = entry.sizeBytes,
                    modifiedAtMillis = entry.modifiedAtMillis,
                )
            }
        }
    }

    private fun requireRoot(treeUri: String): DocumentFile =
        requireNotNull(DocumentFile.fromTreeUri(context, treeUri.toUri())) {
            "The score folder is no longer available"
        }

    private fun belongsToTree(tree: Uri, target: Uri): Boolean =
        tree.authority == target.authority && runCatching {
            DocumentsContract.getTreeDocumentId(tree) == DocumentsContract.getTreeDocumentId(target)
        }.getOrDefault(false)

    private fun queryDirectoryName(directory: Uri): String {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return context.contentResolver.query(directory, projection, null, null, null)?.use { cursor ->
            require(cursor.moveToFirst()) { "The score folder is no longer available" }
            require(cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) {
                "This item is not a folder"
            }
            cursor.getString(0) ?: "Scores"
        } ?: error("The score folder is no longer available")
    }

    private fun queryDirectoryEntries(tree: Uri, directory: Uri): List<LibraryEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getDocumentId(directory),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(2)
                    val name = cursor.getString(1)
                    val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                    if (isDirectory || mimeType == "application/pdf" || name?.endsWith(".pdf", true) == true) {
                        add(
                            LibraryEntry(
                                uri = DocumentsContract.buildDocumentUriUsingTree(
                                    tree,
                                    cursor.getString(0),
                                ).toString(),
                                name = name ?: if (isDirectory) "Untitled folder" else "Untitled score.pdf",
                                isDirectory = isDirectory,
                                sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                                modifiedAtMillis = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                            ),
                        )
                    }
                }
            }
        } ?: emptyList()
    }

    private fun requireDirectory(root: DocumentFile, uri: String): DocumentFile {
        val directory = requireNotNull(findDescendant(root, uri.toUri())) {
            "Destination is outside the score library"
        }
        require(directory.isDirectory) { "Destination is not a folder" }
        return directory
    }

    private fun findDescendant(root: DocumentFile, target: Uri): DocumentFile? =
        findDescendantWithParent(root, target)?.first

    private fun findDescendantWithParent(
        current: DocumentFile,
        target: Uri,
        parent: DocumentFile? = null,
    ): Pair<DocumentFile, DocumentFile?>? {
        if (current.uri == target) return current to parent
        if (!current.isDirectory) return null
        current.listFiles().forEach { child ->
            findDescendantWithParent(child, target, current)?.let { return it }
        }
        return null
    }

    private fun validateTransfer(source: DocumentFile, destination: DocumentFile) {
        require(source.uri != destination.uri) { "A folder cannot be pasted into itself" }
        if (source.isDirectory) {
            require(findDescendant(source, destination.uri) == null) {
                "A folder cannot be pasted into one of its subfolders"
            }
        }
    }

    private fun tryNativeCopy(source: DocumentFile, destination: DocumentFile): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || source.uri.authority != destination.uri.authority) {
            return false
        }
        return runCatching {
            DocumentsContract.copyDocument(context.contentResolver, source.uri, destination.uri)
        }.getOrNull() != null
    }

    private fun tryNativeMove(
        source: DocumentFile,
        sourceParent: DocumentFile,
        destination: DocumentFile,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || source.uri.authority != destination.uri.authority) {
            return false
        }
        return runCatching {
            DocumentsContract.moveDocument(
                context.contentResolver,
                source.uri,
                sourceParent.uri,
                destination.uri,
            )
        }.getOrNull() != null
    }

    private fun copyRecursively(source: DocumentFile, destination: DocumentFile): DocumentFile {
        val name = uniqueName(destination, source.name ?: if (source.isDirectory) "Folder" else "File")
        val created = if (source.isDirectory) {
            requireNotNull(destination.createDirectory(name)) { "Could not create $name" }
        } else {
            requireNotNull(destination.createFile(source.type ?: "application/octet-stream", name)) {
                "Could not create $name"
            }
        }
        try {
            if (source.isDirectory) {
                source.listFiles().forEach { copyRecursively(it, created) }
            } else {
                context.contentResolver.openInputStream(source.uri).use { input ->
                    requireNotNull(input) { "Could not read ${source.name ?: "the source file"}" }
                    context.contentResolver.openOutputStream(created.uri, "w").use { output ->
                        requireNotNull(output) { "Could not write $name" }
                        input.copyTo(output)
                    }
                }
            }
            return created
        } catch (failure: Throwable) {
            created.delete()
            throw failure
        }
    }

    private fun uniqueName(directory: DocumentFile, requested: String): String {
        val existing = directory.listFiles().mapNotNull { it.name?.lowercase() }.toSet()
        if (requested.lowercase() !in existing) return requested
        val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
        val base = requested.substring(0, dot)
        val extension = requested.substring(dot)
        var index = 2
        while ("$base ($index)$extension".lowercase() in existing) index++
        return "$base ($index)$extension"
    }

    private fun validateName(requested: String): String {
        val name = requested.trim()
        require(name.isNotEmpty()) { "Name cannot be empty" }
        require(name != "." && name != "..") { "Choose a different name" }
        require(name.none { it == '/' || it == '\\' || it.isISOControl() }) {
            "Name cannot contain slashes or control characters"
        }
        return name
    }

    private fun requireNameAvailable(directory: DocumentFile, name: String, exceptUri: Uri? = null) {
        val duplicate = directory.listFiles().any {
            it.uri != exceptUri && it.name.equals(name, ignoreCase = true)
        }
        require(!duplicate) { "An item named $name already exists" }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    private fun sanitizePdfName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Downloaded score.pdf" }
        return if (cleaned.endsWith(".pdf", ignoreCase = true)) cleaned else "$cleaned.pdf"
    }
}
