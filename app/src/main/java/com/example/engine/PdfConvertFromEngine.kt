package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.model.OutputFileArtifact
import com.example.model.PickedFile
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

enum class ImageExportFormat(val ext: String, val mime: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

object PdfConvertFromEngine {

    suspend fun pdfToImages(
        context: Context,
        file: PickedFile,
        format: ImageExportFormat,
        targetWidthPx: Int = 1400,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val srcFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val artifacts = mutableListOf<OutputFileArtifact>()

        ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                if (total == 0) throw IllegalStateException("PDF contains 0 pages.")

                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                        val w = max(200, targetWidthPx)
                        val h = max(200, (w * ratio).roundToInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val outFile = FileHelper.createProcessedFile(context, "${baseName}_page_${i + 1}.${format.ext}")
                        FileOutputStream(outFile).use { fos ->
                            val compressFormat = when (format) {
                                ImageExportFormat.JPG -> Bitmap.CompressFormat.JPEG
                                ImageExportFormat.PNG -> Bitmap.CompressFormat.PNG
                                ImageExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Bitmap.CompressFormat.WEBP_LOSSY
                                } else {
                                    @Suppress("DEPRECATION")
                                    Bitmap.CompressFormat.WEBP
                                }
                            }
                            bmp.compress(compressFormat, 90, fos)
                        }
                        bmp.recycle()
                        artifacts.add(
                            OutputFileArtifact(
                                name = outFile.name,
                                path = outFile.absolutePath,
                                mimeType = format.mime,
                                sizeBytes = outFile.length(),
                                pageCount = 1
                            )
                        )
                    }
                    onProgress(0.15f + 0.7f * (i + 1) / total, "Exporting page ${i + 1} of $total...")
                }
            }
        }

        val zipArchive = if (artifacts.size > 1) {
            onProgress(0.92f, "Creating ZIP archive...")
            FileHelper.createZipArchive(context, artifacts, "${baseName}_${format.ext}_pages.zip")
        } else null

        onProgress(1f, "Exported ${artifacts.size} image(s)")
        WorkspaceSuccessResult(
            primaryFile = artifacts.first(),
            additionalFiles = artifacts.drop(1),
            zipArchive = zipArchive,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Exported ${artifacts.size} ${format.ext.uppercase()} image(s)."
        )
    }

    suspend fun pdfToText(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Extracting text streams...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_text.txt")

        var pageCount = 0
        val extracted = StringBuilder()

        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val stripper = PDFTextStripper()
            for (p in 1..pageCount) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                val text = stripper.getText(doc).trim()
                extracted.append("=== Page $p ===\n")
                extracted.append(text).append("\n\n")
                onProgress(0.2f + 0.7f * p / max(1, pageCount), "Extracting page $p of $pageCount...")
            }
        }

        val fullText = extracted.toString().trim()
        outFile.writeText(fullText, Charsets.UTF_8)
        onProgress(1f, "Text extraction complete")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "text/plain",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            extractedText = fullText,
            summaryNote = "Extracted ${fullText.length} characters from $pageCount page(s)."
        )
    }

    suspend fun pdfToWordDocx(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Reading PDF text for DOCX...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}.docx")

        val pagesText = mutableListOf<String>()
        PDDocument.load(File(file.localPath)).use { doc ->
            val stripper = PDFTextStripper()
            for (p in 1..doc.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                pagesText.add(stripper.getText(doc))
                onProgress(0.15f + 0.65f * p / max(1, doc.numberOfPages), "Extracting page $p...")
            }
        }

        onProgress(0.85f, "Building OOXML DOCX package...")
        writeDocxPackage(outFile, pagesText)
        onProgress(1f, "DOCX generated")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = outFile.length(),
            pageCount = pagesText.size
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Generated valid DOCX document with ${pagesText.size} page section(s)."
        )
    }

    suspend fun pdfToExcelXlsx(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Parsing tabular rows from PDF...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}.xlsx")

        val allRows = mutableListOf<List<String>>()
        var pageCount = 0
        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val stripper = PDFTextStripper()
            for (p in 1..pageCount) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                val text = stripper.getText(doc)
                text.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        // Split on multiple spaces or tabs to detect columns cleanly
                        val cols = trimmed.split(Regex("\\s{2,}|\\t")).map { it.trim() }.filter { it.isNotEmpty() }
                        allRows.add(if (cols.isNotEmpty()) cols else listOf(trimmed))
                    }
                }
                onProgress(0.15f + 0.65f * p / max(1, pageCount), "Scanning page $p...")
            }
        }

        if (allRows.isEmpty()) {
            allRows.add(listOf("No selectable text rows detected in PDF"))
        }

        onProgress(0.85f, "Building OOXML XLSX workbook...")
        writeXlsxPackage(outFile, allRows)
        onProgress(1f, "XLSX generated")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Exported ${allRows.size} structured row(s) into a valid XLSX workbook."
        )
    }

    suspend fun pdfToPptx(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val srcFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}.pptx")

        val slideImages = mutableListOf<ByteArray>()
        ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                if (total == 0) throw IllegalStateException("PDF contains 0 pages.")
                for (i in 0 until total) {
                    coroutineContext.ensureActive()
                    renderer.openPage(i).use { page ->
                        val w = 1280
                        val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 0.75f
                        val h = max(200, (w * ratio).roundToInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)
                        bmp.recycle()
                        slideImages.add(baos.toByteArray())
                    }
                    onProgress(0.15f + 0.65f * (i + 1) / total, "Rendering slide ${i + 1} of $total...")
                }
            }
        }

        onProgress(0.85f, "Packaging OOXML PPTX presentation...")
        writePptxPackage(outFile, slideImages)
        onProgress(1f, "PPTX generated")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            sizeBytes = outFile.length(),
            pageCount = slideImages.size
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Created ${slideImages.size}-slide PPTX presentation from PDF pages."
        )
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    val code = ch.code
                    if (code == 0x9 || code == 0xA || code == 0xD || code >= 0x20) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun writeDocxPackage(outFile: File, pagesText: List<String>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putEntryText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            )
            zos.putEntryText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )

            val body = StringBuilder()
            pagesText.forEachIndexed { idx, pageText ->
                val lines = pageText.lines()
                for (line in lines) {
                    val clean = escapeXml(line)
                    body.append("<w:p><w:r><w:t xml:space=\"preserve\">$clean</w:t></w:r></w:p>")
                }
                if (idx < pagesText.lastIndex) {
                    body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }

            zos.putEntryText(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $body
    <w:sectPr/>
  </w:body>
</w:document>"""
            )
        }
    }

    private fun writeXlsxPackage(outFile: File, rows: List<List<String>>) {
        val sharedStrings = mutableListOf<String>()
        val stringToIndex = mutableMapOf<String, Int>()
        fun intern(value: String): Int {
            return stringToIndex.getOrPut(value) {
                sharedStrings.add(value)
                sharedStrings.lastIndex
            }
        }

        val sheetRowsXml = StringBuilder()
        rows.forEachIndexed { rIdx, row ->
            val rNum = rIdx + 1
            sheetRowsXml.append("<row r=\"$rNum\">")
            row.forEachIndexed { cIdx, cellVal ->
                val colRef = columnLetter(cIdx) + rNum
                val sIdx = intern(cellVal)
                sheetRowsXml.append("<c r=\"$colRef\" t=\"s\"><v>$sIdx</v></c>")
            }
            sheetRowsXml.append("</row>")
        }

        val ssXml = StringBuilder()
        sharedStrings.forEach { s ->
            ssXml.append("<si><t xml:space=\"preserve\">${escapeXml(s)}</t></si>")
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putEntryText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""
            )
            zos.putEntryText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            zos.putEntryText(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="PDF Data" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""
            )
            zos.putEntryText(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""
            )
            zos.putEntryText(
                "xl/sharedStrings.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">
  $ssXml
</sst>"""
            )
            zos.putEntryText(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$sheetRowsXml</sheetData>
</worksheet>"""
            )
        }
    }

    private fun writePptxPackage(outFile: File, slideImages: List<ByteArray>) {
        val slideCx = 9144000L // 10 inches in EMU
        val slideCy = 6858000L // 7.5 inches in EMU

        val ctSlides = StringBuilder()
        val presSlideIds = StringBuilder()
        val presRels = StringBuilder()

        slideImages.forEachIndexed { idx, _ ->
            val n = idx + 1
            ctSlides.append("<Override PartName=\"/ppt/slides/slide$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>\n")
            presSlideIds.append("<p:sldId id=\"${256 + idx}\" r:id=\"rId$n\"/>")
            presRels.append("<Relationship Id=\"rId$n\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide$n.xml\"/>\n")
        }

        val masterRid = "rId${slideImages.size + 1}"
        presRels.append("<Relationship Id=\"$masterRid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            zos.putEntryText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  $ctSlides
</Types>"""
            )
            zos.putEntryText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/presentation" Target="ppt/presentation.xml"/>
</Relationships>"""
            )
            zos.putEntryText(
                "ppt/presentation.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="$masterRid"/></p:sldMasterIdLst>
  <p:sldIdLst>$presSlideIds</p:sldIdLst>
  <p:sldSz cx="$slideCx" cy="$slideCy" type="screen4x3"/>
  <p:notesSz cx="$slideCy" cy="$slideCx"/>
</p:presentation>"""
            )
            zos.putEntryText(
                "ppt/_rels/presentation.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $presRels
</Relationships>"""
            )
            zos.putEntryText(
                "ppt/slideMasters/slideMaster1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""
            )
            zos.putEntryText(
                "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
            )
            zos.putEntryText(
                "ppt/slideLayouts/slideLayout1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
</p:sldLayout>"""
            )
            zos.putEntryText(
                "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""
            )

            slideImages.forEachIndexed { idx, imgBytes ->
                val n = idx + 1
                zos.putNextEntry(ZipEntry("ppt/media/image$n.png"))
                zos.write(imgBytes)
                zos.closeEntry()

                zos.putEntryText(
                    "ppt/slides/_rels/slide$n.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$n.png"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
                )
                zos.putEntryText(
                    "ppt/slides/slide$n.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree>
    <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>
    <p:pic>
      <p:nvPicPr><p:cNvPr id="2" name="Slide $n"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
      <p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
      <p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$slideCx" cy="$slideCy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr>
    </p:pic>
  </p:spTree></p:cSld>
</p:sld>"""
                )
            }
        }
    }

    private fun ZipOutputStream.putEntryText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun columnLetter(index: Int): String {
        var idx = index
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A'.code + (idx % 26)).toChar())
            idx = idx / 26 - 1
        }
        return sb.toString()
    }
}
