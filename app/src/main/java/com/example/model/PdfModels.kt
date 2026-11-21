package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NoEncryption
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CatAdvancedColor
import com.example.ui.theme.CatConvertFromColor
import com.example.ui.theme.CatConvertToColor
import com.example.ui.theme.CatEditColor
import com.example.ui.theme.CatOptimizeColor
import com.example.ui.theme.CatOrganizeColor
import com.example.ui.theme.CatSecurityColor

enum class AppLanguage { EN, AR }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ToolCategory(val color: Color) {
    ORGANIZE(CatOrganizeColor),
    OPTIMIZE(CatOptimizeColor),
    CONVERT_TO_PDF(CatConvertToColor),
    CONVERT_FROM_PDF(CatConvertFromColor),
    EDIT(CatEditColor),
    SECURITY(CatSecurityColor),
    ADVANCED(CatAdvancedColor)
}

enum class PdfToolId(
    val category: ToolCategory,
    val icon: ImageVector,
    val allowMultipleFiles: Boolean,
    val acceptedMimeTypes: List<String>,
    val isPageBased: Boolean = false
) {
    // A. ORGANIZE PDF
    MERGE_PDF(ToolCategory.ORGANIZE, Icons.AutoMirrored.Rounded.MergeType, true, listOf("application/pdf")),
    SPLIT_PDF(ToolCategory.ORGANIZE, Icons.Rounded.CallSplit, false, listOf("application/pdf"), isPageBased = true),
    EXTRACT_PAGES(ToolCategory.ORGANIZE, Icons.Rounded.FilterNone, false, listOf("application/pdf"), isPageBased = true),
    DELETE_PAGES(ToolCategory.ORGANIZE, Icons.Rounded.DeleteSweep, false, listOf("application/pdf"), isPageBased = true),
    REORDER_PAGES(ToolCategory.ORGANIZE, Icons.Rounded.SwapVert, false, listOf("application/pdf"), isPageBased = true),
    ROTATE_PAGES(ToolCategory.ORGANIZE, Icons.Rounded.RotateRight, false, listOf("application/pdf"), isPageBased = true),
    DUPLICATE_PAGES(ToolCategory.ORGANIZE, Icons.Rounded.ContentCopy, false, listOf("application/pdf"), isPageBased = true),
    VISUAL_ORGANIZER(ToolCategory.ORGANIZE, Icons.Rounded.GridView, false, listOf("application/pdf"), isPageBased = true),

    // B. OPTIMIZE PDF
    COMPRESS_PDF(ToolCategory.OPTIMIZE, Icons.Rounded.Compress, false, listOf("application/pdf")),

    // C. CONVERT TO PDF
    JPG_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Image, true, listOf("image/jpeg", "image/jpg")),
    PNG_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Collections, true, listOf("image/png")),
    WEBP_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Image, true, listOf("image/webp")),
    HTML_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Code, false, listOf("text/html", "application/xhtml+xml", "text/plain")),
    TXT_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.TextFields, false, listOf("text/plain")),
    WORD_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Description, false, listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword")),
    EXCEL_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.TableChart, false, listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values")),
    PPT_TO_PDF(ToolCategory.CONVERT_TO_PDF, Icons.Rounded.Slideshow, false, listOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")),

    // D. CONVERT FROM PDF
    PDF_TO_JPG(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.Image, false, listOf("application/pdf")),
    PDF_TO_PNG(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.Collections, false, listOf("application/pdf")),
    PDF_TO_WEBP(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.Image, false, listOf("application/pdf")),
    PDF_TO_TEXT(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.TextFields, false, listOf("application/pdf")),
    PDF_TO_WORD(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.Description, false, listOf("application/pdf")),
    PDF_TO_EXCEL(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.TableChart, false, listOf("application/pdf")),
    PDF_TO_PPT(ToolCategory.CONVERT_FROM_PDF, Icons.Rounded.Slideshow, false, listOf("application/pdf")),

    // E. EDIT PDF
    EDIT_PDF(ToolCategory.EDIT, Icons.Rounded.Draw, false, listOf("application/pdf"), isPageBased = true),
    PAGE_NUMBERS(ToolCategory.EDIT, Icons.Rounded.FormatListNumbered, false, listOf("application/pdf")),
    WATERMARK(ToolCategory.EDIT, Icons.Rounded.WaterDrop, false, listOf("application/pdf")),
    HEADER_FOOTER(ToolCategory.EDIT, Icons.Rounded.ViewAgenda, false, listOf("application/pdf")),

    // F. SECURITY PDF
    PROTECT_PDF(ToolCategory.SECURITY, Icons.Rounded.Lock, false, listOf("application/pdf")),
    UNLOCK_PDF(ToolCategory.SECURITY, Icons.Rounded.LockOpen, false, listOf("application/pdf")),
    ENCRYPT_PDF(ToolCategory.SECURITY, Icons.Rounded.EnhancedEncryption, false, listOf("application/pdf")),
    REMOVE_PASSWORD(ToolCategory.SECURITY, Icons.Rounded.NoEncryption, false, listOf("application/pdf")),
    DIGITAL_SIGNATURE(ToolCategory.SECURITY, Icons.Rounded.VerifiedUser, false, listOf("application/pdf")),
    REDACT_PDF(ToolCategory.SECURITY, Icons.Rounded.HideSource, false, listOf("application/pdf"), isPageBased = true),

    // G. ADVANCED PDF TOOLS
    OCR_PDF(ToolCategory.ADVANCED, Icons.Rounded.Scanner, false, listOf("application/pdf")),
    SEARCH_PDF(ToolCategory.ADVANCED, Icons.AutoMirrored.Rounded.ManageSearch, false, listOf("application/pdf")),
    EXTRACT_IMAGES(ToolCategory.ADVANCED, Icons.Rounded.Collections, false, listOf("application/pdf")),
    EXTRACT_TEXT(ToolCategory.ADVANCED, Icons.AutoMirrored.Rounded.ListAlt, false, listOf("application/pdf")),
    COMPARE_PDF(ToolCategory.ADVANCED, Icons.AutoMirrored.Rounded.CompareArrows, true, listOf("application/pdf")),
    ADD_BOOKMARKS(ToolCategory.ADVANCED, Icons.Rounded.BookmarkAdd, false, listOf("application/pdf")),
    REPAIR_PDF(ToolCategory.ADVANCED, Icons.Rounded.Build, false, listOf("application/pdf")),
    METADATA_INSPECT(ToolCategory.ADVANCED, Icons.Rounded.Info, false, listOf("application/pdf")),
    EDIT_METADATA(ToolCategory.ADVANCED, Icons.Rounded.EditNote, false, listOf("application/pdf")),
    CREATE_PDFA(ToolCategory.ADVANCED, Icons.Rounded.Verified, false, listOf("application/pdf"));

    companion object {
        fun fromId(id: String): PdfToolId? = entries.firstOrNull { it.name == id }
    }
}

data class PickedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val pageCount: Int = 0,
    val isPasswordProtected: Boolean = false
)

data class PageItemState(
    val id: String,
    val originalPageIndex: Int, // 0-based
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = true,
    val copyNumber: Int = 1
)

enum class CompressionLevel { LOW, MEDIUM, HIGH }

enum class SplitMode { ALL_PAGES, CUSTOM_RANGES, FIXED_INTERVAL }

enum class PageNumberPosition {
    BOTTOM_CENTER, BOTTOM_RIGHT, BOTTOM_LEFT,
    TOP_CENTER, TOP_RIGHT, TOP_LEFT
}

enum class EditorToolMode {
    TEXT, IMAGE, DRAW, HIGHLIGHT, SHAPE_RECT, SHAPE_CIRCLE, SIGNATURE, DATE
}

sealed class EditOverlay(open val id: String, open val pageIndex: Int) {
    data class TextOverlay(
        override val id: String,
        override val pageIndex: Int,
        val text: String,
        val normX: Float,
        val normY: Float,
        val fontSizeSp: Float,
        val colorArgb: Int
    ) : EditOverlay(id, pageIndex)

    data class ImageOverlay(
        override val id: String,
        override val pageIndex: Int,
        val imagePath: String,
        val normX: Float,
        val normY: Float,
        val normWidth: Float,
        val normHeight: Float
    ) : EditOverlay(id, pageIndex)

    data class PathOverlay(
        override val id: String,
        override val pageIndex: Int,
        val points: List<Pair<Float, Float>>, // normalized 0..1
        val colorArgb: Int,
        val strokeWidthNorm: Float,
        val isHighlighter: Boolean
    ) : EditOverlay(id, pageIndex)

    data class ShapeOverlay(
        override val id: String,
        override val pageIndex: Int,
        val isCircle: Boolean,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float,
        val colorArgb: Int,
        val strokeWidthNorm: Float
    ) : EditOverlay(id, pageIndex)
}

data class RedactionRect(
    val id: String,
    val pageIndex: Int,
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float
)

data class BookmarkInput(
    val id: String,
    val title: String,
    val pageNumber1Based: Int
)

data class PdfMetadataInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val creationDate: String = "",
    val modificationDate: String = "",
    val pageCount: Int = 0,
    val pdfVersion: String = "",
    val isEncrypted: Boolean = false,
    val fileSizeBytes: Long = 0L
)

data class SearchMatch(
    val pageNumber1Based: Int,
    val matchCount: Int,
    val snippet: String
)

enum class DiffLineType { UNCHANGED, ADDED, REMOVED }

data class DiffLine(
    val type: DiffLineType,
    val text: String
)

data class PageComparison(
    val pageNumber1Based: Int,
    val diffLines: List<DiffLine>,
    val hasChanges: Boolean
)

data class OutputFileArtifact(
    val name: String,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val pageCount: Int = 0
)

data class WorkspaceSuccessResult(
    val primaryFile: OutputFileArtifact,
    val additionalFiles: List<OutputFileArtifact> = emptyList(),
    val zipArchive: OutputFileArtifact? = null,
    val originalSizeBytes: Long = 0L,
    val extractedText: String? = null,
    val metadataInfo: PdfMetadataInfo? = null,
    val searchMatches: List<SearchMatch> = emptyList(),
    val pageComparisons: List<PageComparison> = emptyList(),
    val summaryNote: String = ""
)

data class ProcessingProgress(
    val fraction: Float, // 0f..1f
    val statusText: String
)
