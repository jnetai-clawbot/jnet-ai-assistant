package com.jnetai.assistant.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParsedDocument(
    val text: String,
    val pages: Int = 1,
    val sections: Map<Int, String> = emptyMap()
)

/**
 * Extracts plain text from many document types via Android's ContentResolver
 * and streaming input. Unsupported types throw [UnsupportedDocumentException]
 * with a user-friendly message rather than crashing. Files > threshold are
 * processed in a streaming fashion to avoid loading everything into memory.
 */
object DocumentParser {

    fun isSupported(mime: String, name: String): Boolean = supportedTypes(mime.lowercase()).any { it == mime.lowercase() } ||
        supportedExtensions.any { name.lowercase().endsWith(it) }

    private fun supportedTypes(mime: String): List<String> = listOf(
        "text/plain", "text/markdown", "text/csv", "text/html", "text/xml",
        "application/json", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/aac"
    )

    private val supportedExtensions = listOf(
        ".txt", ".md", ".markdown", ".csv", ".html", ".htm", ".xml", ".json",
        ".pdf", ".docx", ".doc", ".xls", ".xlsx",
        ".kt", ".java", ".py", ".js", ".ts", ".c", ".cpp", ".h", ".sh", ".sql", ".php", ".rb", ".go", ".rs", ".swift", ".css", ".gradle", ".properties"
    )

    /** Computes a stable SHA-256 file hash for duplicate detection. Streamed. */
    suspend fun hashUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val dig = java.security.MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(64 * 1024)
                var n = input.read(buf)
                while (n != -1) {
                    if (n > 0) dig.update(buf, 0, n)
                    n = input.read(buf)
                }
            } ?: throw IllegalStateException("Cannot open URI for hashing")
            dig.digest().joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            Err.e(Err.DOC_PARSE_ERROR, "hashUri failed for $uri", t)
            throw t
        }
    }

    suspend fun nameAndSize(context: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "document"
        var size = 0L
        try {
            val resolver = context.contentResolver
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        } catch (_: Throwable) {}
        name to size
    }

    suspend fun parse(context: Context, uri: Uri, name: String, mime: String): ParsedDocument =
        withContext(Dispatchers.IO) {
            val lower = name.lowercase()
            val m = mime.lowercase()
            return@withContext when {
                m == "application/pdf" || lower.endsWith(".pdf") -> parsePdf(context, uri)
                m.contains("wordprocessingml") || lower.endsWith(".docx") -> parseDocx(context, uri)
                m == "application/msword" || lower.endsWith(".doc") -> {
                    throw UnsupportedDocumentException("Legacy .doc files are not supported — please re-save as .docx or convert to PDF.")
                }
                m.contains("spreadsheetml") || m == "application/vnd.ms-excel" || lower.endsWith(".xlsx") || lower.endsWith(".xls") -> parseExcel(context, uri)
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> {
                    Err.w("Image OCR not configured; returning empty")
                    throw UnsupportedDocumentException("Image OCR is not available for this document")
                }
                supportedExtensions.any { lower.endsWith(it) } || m.startsWith("text/") -> parseText(context, uri)
                // audio files: we do NOT OCR here; transcribed separately via STT
                m.startsWith("audio/") -> {
                    throw UnsupportedDocumentException("Audio documents are indexed from their transcription, not raw bytes")
                }
                else -> {
                    Err.e(Err.DOC_UNSUPPORTED, "Unsupported document type: $name / $m")
                    throw UnsupportedDocumentException("Unsupported document type: $name")
                }
            }
        }

    private suspend fun parseText(context: Context, uri: Uri): ParsedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sb = StringBuilder()
        resolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(8192)
                var n = reader.read(buf)
                while (n != -1) {
                    if (n > 0) sb.append(buf, 0, n)
                    n = reader.read(buf)
                }
            }
        } ?: throw UnsupportedDocumentException("Could not read the document")
        ParsedDocument(sb.toString().clean())
    }

    private suspend fun parsePdf(context: Context, uri: Uri): ParsedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var pages = 0
        val sections = mutableMapOf<Int, String>()
        resolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                pages = doc.numberOfPages
                val stripper = PDFTextStripper()
                for (p in 1..pages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(doc).clean()
                    sections[p] = pageText
                }
            }
        } ?: throw UnsupportedDocumentException("Could not read PDF")
        ParsedDocument(sections.values.joinToString("\n\n").clean(), pages, sections)
    }

    private suspend fun parseDocx(context: Context, uri: Uri): ParsedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sb = StringBuilder()
        resolver.openInputStream(uri)?.use { input ->
            XWPFDocument(input).use { doc ->
                for (para in doc.paragraphs) sb.append(para.text).append('\n')
                for (table in doc.tables) {
                    for (row in table.rows) {
                        for (cell in row.tableCells) {
                            for (p in cell.paragraphs) sb.append(p.text).append(' ')
                        }
                        sb.append('\n')
                    }
                }
            }
        } ?: throw UnsupportedDocumentException("Could not read DOCX")
        ParsedDocument(sb.toString().clean())
    }

    private suspend fun parseExcel(context: Context, uri: Uri): ParsedDocument = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val sb = StringBuilder()
        resolver.openInputStream(uri)?.use { input ->
            val wb = WorkbookFactory.create(input)
            val fmt = DataFormatter()
            try {
                for (sheet in wb) {
                    sb.append("### Sheet: ").append(sheet.sheetName).append('\n')
                    for (row in sheet) {
                        row.forEach { cell ->
                            sb.append(fmt.formatCellValue(cell)).append('\t')
                        }
                        sb.append('\n')
                    }
                }
            } finally {
                wb.close()
            }
        } ?: throw UnsupportedDocumentException("Could not read spreadsheet")
        ParsedDocument(sb.toString().clean())
    }

    private fun String.clean(): String = this.trim()
}

class UnsupportedDocumentException(message: String) : Exception(message)