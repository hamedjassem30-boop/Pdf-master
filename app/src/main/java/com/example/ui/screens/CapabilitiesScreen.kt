package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.ui.theme.CatConvertToColor
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun CapabilitiesScreen(
    lang: AppLanguage,
    modifier: Modifier = Modifier
) {
    val isAr = lang == AppLanguage.AR
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isAr) "بنية المحرك والقدرات التقنية" else "Engine Architecture & Capabilities",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (isAr)
                        "يعمل PDF Master Pro محلياً بالكامل على جهازك لحماية خصوصيتك. يوضح الجدول أدناه المحركات النشطة وما يتطلب خادماً خلفياً عند الحاجة."
                    else
                        "PDF Master Pro executes all document processing locally on-device for zero-upload privacy. Below is the transparent breakdown of active local engines and server-recommended edge cases.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            CapabilitySectionCard(
                title = if (isAr) "1. محركات PDF المحلية النشطة (100% على الجهاز)" else "1. Active On-Device Engines (100% Local)",
                icon = Icons.Rounded.CheckCircle,
                accent = SuccessGreen,
                bullets = listOf(
                    "Organize PDF (Merge, Split, Extract, Delete, Reorder, Rotate, Duplicate, Visual Studio) — Powered by Apache PDFBox-Android & Android PdfRenderer.",
                    "Optimize PDF — Re-compresses embedded PDImageXObject bitmaps (Low/Medium/High) & offers optional page rasterization with real size reporting.",
                    "Convert to PDF — JPG/PNG/WEBP, UTF-8 TXT (with Arabic RTL support), HTML, and local OOXML (.docx, .xlsx/.csv, .pptx) parsers rendered via Android PdfDocument & PDFBox.",
                    "Convert from PDF — High-DPI JPG/PNG/WEBP (+ ZIP), UTF-8 Text extraction, and genuine OOXML (.docx, .xlsx, .pptx) package generators.",
                    "Edit PDF Studio — Interactive WYSIWYG canvas for Text, Logos/Images, Freehand Ink, Highlighter, Shapes, Touch Signature Pad, Date Stamps, Page Numbers, Watermarks & Headers/Footers.",
                    "Security PDF — AES-128 Password Protection & Permission Locking, Authorized Unlock, SHA-256 Digital Verification Seal, and True Irreversible Page Redaction.",
                    "Advanced Tools — On-device ML Kit OCR (Latin), Full-Text Search, Embedded Image Extraction, Line-by-Line Two-PDF Diff Compare, Outline Bookmarks, XRef/Page Rescue Repair, Metadata Inspector/Editor, and PDF/A-1b XMP Archival Tagging."
                ),
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
            )
        }

        item {
            CapabilitySectionCard(
                title = if (isAr) "2. حالات تتطلب معالجة خادم (Server-Side)" else "2. Features Benefiting from a Backend Server",
                icon = Icons.Rounded.CloudQueue,
                accent = WarningAmber,
                bullets = listOf(
                    "Legacy Binary Office Formats (.doc, .xls, .ppt) & Complex Macro/Chart Layouts — Require server-side LibreOffice headless conversion for pixel-exact fidelity.",
                    "Arabic / Multi-Script Handwritten OCR — On-device ML Kit handles Latin offline; Arabic/CJK OCR requires a server-side Tesseract Arabic traineddata or Cloud Vision pipeline.",
                    "CA-Trusted X.509 PKCS#7 Cryptographic Signatures — Full LTV/PAdES certificate chain signing requires a user-supplied PKCS#12 hardware token or TSA timestamp server.",
                    "Strict ISO 19005 PDF/A Font Subsetting & VeraPDF Validation — Embedding full ICC profiles and subsetting every legacy font glyph requires Ghostscript/veraPDF on a backend."
                ),
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
            )
        }

        item {
            CapabilitySectionCard(
                title = if (isAr) "3. سياسة الخصوصية والملفات" else "3. Privacy & Storage Architecture",
                icon = Icons.Rounded.Security,
                accent = CatConvertToColor,
                bullets = listOf(
                    "Zero broad storage permissions requested (uses Android Storage Access Framework & Photo Picker).",
                    "Original user files are never overwritten or modified in place.",
                    "Processed outputs are stored locally in private app storage with instant 1-tap cache cleanup in Dashboard."
                ),
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CapabilitySectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    bullets: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            bullets.forEach { b ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• $b",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
