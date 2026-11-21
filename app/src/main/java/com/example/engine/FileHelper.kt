package com.example.engine

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.OutputFileArtifact
import com.example.model.PickedFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileHelper {

    private const val MAX_FILE_SIZE_BYTES = 250L * 1024L * 1024L // 250 MB guard

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isEmpty()) "document_${System.currentTimeMillis()}" else cleaned
    }

    fun createProcessedFile(context: Context, desiredName: String): File {
        val dir = File(context.filesDir, "processed").apply { mkdirs() }
        val clean = sanitizeFileName(desiredName)
        var candidate = File(dir, clean)
        if (candidate.exists()) {
            val dot = clean.lastIndexOf('.')
            val base = if (dot > 0) clean.substring(0, dot) else clean
            val ext = if (dot > 0) clean.substring(dot) else ""
            candidate = File(dir, "${base}_${System.currentTimeMillis() % 10000}$ext")
        }
        return candidate
    }

    fun createTempFile(context: Context, prefix: String, ext: String): File {
        val dir = File(context.cacheDir, "temp_out").apply { mkdirs() }
        return File(dir, "${prefix}_${UUID.randomUUID().toString().take(8)}.$ext")
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        requirePdf: Boolean = false
    ): Result<PickedFile> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val (displayName, reportedSize) = queryMetadata(resolver, uri)
            val mimeType = resolver.getType(uri) ?: guessMimeFromName(displayName)

            if (reportedSize > MAX_FILE_SIZE_BYTES) {
                throw IllegalArgumentException("File exceeds 250 MB maximum local limit (${formatBytes(reportedSize)}).")
            }

            val inputDir = File(context.cacheDir, "inputs").apply { mkdirs() }
            val safeName = sanitizeFileName(displayName)
            val destFile = File(inputDir, "${UUID.randomUUID().toString().take(8)}_$safeName")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_FILE_SIZE_BYTES) {
                            destFile.delete()
                            throw IllegalArgumentException("File exceeds 250 MB limit.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalStateException("Unable to open selected file stream.")

            if (destFile.length() == 0L) {
                destFile.delete()
                throw IllegalArgumentException("Selected file is empty (0 bytes).")
            }

            val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
                safeName.lowercase(Locale.US).endsWith(".pdf")

            if (requirePdf && !isPdf) {
                destFile.delete()
                throw IllegalArgumentException("Invalid file type ($safeName). Please select a PDF file.")
            }

            var pageCount = 0
            var isProtected = false

            if (isPdf) {
                val inspection = inspectPdf(destFile)
                pageCount = inspection.first
                isProtected = inspection.second
            }

            PickedFile(
                id = UUID.randomUUID().toString(),
                name = safeName,
                mimeType = mimeType,
                sizeBytes = destFile.length(),
                localPath = destFile.absolutePath,
                pageCount = pageCount,
                isPasswordProtected = isProtected
            )
        }
    }

    private fun inspectPdf(file: File): Pair<Int, Boolean> {
        return try {
            PDDocument.load(file).use { doc ->
                doc.numberOfPages to doc.isEncrypted
            }
        } catch (_: InvalidPasswordException) {
            0 to true
        } catch (e: Exception) {
            // Let repair or unlock tools attempt to load damaged files, but verify PDF header for general tools
            val header = ByteArray(5)
            FileInputStream(file).use { it.read(header) }
            val headerStr = String(header)
            if (!headerStr.contains("%PDF")) {
                throw IllegalArgumentException("File does not appear to be a valid PDF document.")
            }
            0 to false
        }
    }

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        name = cursor.getString(nameIdx)
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "csv" -> "text/csv"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    suspend fun createZipArchive(
        context: Context,
        artifacts: List<OutputFileArtifact>,
        zipFileName: String
    ): OutputFileArtifact = withContext(Dispatchers.IO) {
        val zipFile = createProcessedFile(context, zipFileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            val usedNames = mutableSetOf<String>()
            artifacts.forEach { artifact ->
                val src = File(artifact.path)
                if (src.exists()) {
                    var entryName = artifact.name
                    var idx = 1
                    while (!usedNames.add(entryName)) {
                        val dot = artifact.name.lastIndexOf('.')
                        entryName = if (dot > 0) {
                            "${artifact.name.substring(0, dot)}_$idx${artifact.name.substring(dot)}"
                        } else "${artifact.name}_$idx"
                        idx++
                    }
                    zos.putNextEntry(ZipEntry(entryName))
                    BufferedInputStream(FileInputStream(src)).use { bis ->
                        bis.copyTo(zos, 8192)
                    }
                    zos.closeEntry()
                }
            }
        }
        OutputFileArtifact(
            name = zipFile.name,
            path = zipFile.absolutePath,
            mimeType = "application/zip",
            sizeBytes = zipFile.length(),
            pageCount = artifacts.size
        )
    }

    suspend fun saveArtifactToUri(
        context: Context,
        sourcePath: String,
        destUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(sourcePath)
            if (!src.exists()) throw IllegalStateException("Output file not found.")
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(src).use { input ->
                    input.copyTo(out, 16 * 1024)
                }
            } ?: throw IllegalStateException("Unable to write to destination.")
            Unit
        }
    }

    fun buildShareIntent(context: Context, filePath: String, mimeType: String): Intent {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun parsePageSelection(input: String, maxPages: Int): List<Int> {
        if (maxPages <= 0) return emptyList()
        if (input.isBlank()) return (0 until maxPages).toList()
        val result = mutableListOf<Int>()
        val parts = input.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            if (part.contains('-')) {
                val bounds = part.split('-').mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = bounds[0].coerceIn(1, maxPages)
                    val end = bounds[1].coerceIn(1, maxPages)
                    if (start <= end) {
                        for (p in start..end) result.add(p - 1)
                    } else {
                        for (p in start downTo end) result.add(p - 1)
                    }
                }
            } else {
                val single = part.toIntOrNull()
                if (single != null && single in 1..maxPages) {
                    result.add(single - 1)
                }
            }
        }
        return result
    }

    fun parseSplitGroups(input: String, maxPages: Int): List<List<Int>> {
        if (maxPages <= 0) return emptyList()
        val groups = mutableListOf<List<Int>>()
        val chunks = input.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        for (chunk in chunks) {
            val pages = parsePageSelection(chunk, maxPages)
            if (pages.isNotEmpty()) groups.add(pages)
        }
        return groups
    }
}
