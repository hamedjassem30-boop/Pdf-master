package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.engine.FileHelper
import com.example.engine.HeaderFooterOptions
import com.example.engine.PageNumbersOptions
import com.example.engine.PdfAdvancedEngine
import com.example.engine.PdfConvertFromEngine
import com.example.engine.PdfConvertToEngine
import com.example.engine.PdfEditEngine
import com.example.engine.PdfOptimizeEngine
import com.example.engine.PdfOrganizeEngine
import com.example.engine.PdfSecurityEngine
import com.example.engine.WatermarkOptions
import com.example.model.BookmarkInput
import com.example.model.CompressionLevel
import com.example.model.PageItemState
import com.example.model.PdfMetadataInfo
import com.example.model.PickedFile
import com.example.model.SplitMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfEnginesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    private fun createSamplePdf(name: String, pageTexts: List<String>, withImage: Boolean = false): PickedFile {
        val file = File(context.cacheDir, name)
        PDDocument().use { doc ->
            pageTexts.forEach { text ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(50f, 700f)
                    text.lines().forEachIndexed { lineIdx, line ->
                        if (lineIdx > 0) cs.newLineAtOffset(0f, -20f)
                        cs.showText(line)
                    }
                    cs.endText()
                    if (withImage) {
                        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.RED)
                        val xObj = LosslessFactory.createFromImage(doc, bmp)
                        bmp.recycle()
                        cs.drawImage(xObj, 50f, 300f, 200f, 200f)
                    }
                }
            }
            doc.save(file)
        }
        return PickedFile(
            id = name,
            name = name,
            mimeType = "application/pdf",
            sizeBytes = file.length(),
            localPath = file.absolutePath,
            pageCount = pageTexts.size
        )
    }

    @Test
    fun `merge and split and organize pages preserve order and counts`() = runTest {
        val pdf1 = createSamplePdf("doc1.pdf", listOf("Alpha Page 1", "Alpha Page 2"))
        val pdf2 = createSamplePdf("doc2.pdf", listOf("Beta Page 1"))

        val merged = PdfOrganizeEngine.mergePdfs(context, listOf(pdf1, pdf2)) { _, _ -> }
        assertEquals(3, merged.primaryFile.pageCount)
        assertTrue(File(merged.primaryFile.path).exists())

        val mergedPicked = PickedFile("m", merged.primaryFile.name, "application/pdf", merged.primaryFile.sizeBytes, merged.primaryFile.path, 3)
        val split = PdfOrganizeEngine.splitPdf(context, mergedPicked, SplitMode.ALL_PAGES, "", 1) { _, _ -> }
        assertEquals(1, split.primaryFile.pageCount)
        assertEquals(2, split.additionalFiles.size)
        assertTrue(split.zipArchive != null && File(split.zipArchive.path).exists())

        // Reorder (reverse), rotate page 1 by 90, duplicate page 0
        val ops = listOf(
            PageItemState("p2", 2, rotationDegrees = 0),
            PageItemState("p1", 1, rotationDegrees = 90),
            PageItemState("p0a", 0, rotationDegrees = 0),
            PageItemState("p0b", 0, rotationDegrees = 0)
        )
        val organized = PdfOrganizeEngine.applyPageOrganization(context, mergedPicked, ops, "org") { _, _ -> }
        assertEquals(4, organized.primaryFile.pageCount)
        PDDocument.load(File(organized.primaryFile.path)).use { outDoc ->
            assertEquals(90, outDoc.getPage(1).rotation)
        }
    }

    @Test
    fun `compress optimizes embedded images and reports sizes`() = runTest {
        val imgPdf = createSamplePdf("heavy.pdf", listOf("Image Page 1", "Image Page 2"), withImage = true)
        val res = PdfOptimizeEngine.compressPdf(context, imgPdf, CompressionLevel.HIGH, false) { _, _ -> }
        assertEquals(2, res.primaryFile.pageCount)
        assertTrue(res.primaryFile.sizeBytes > 0L)
        assertTrue(res.primaryFile.sizeBytes <= imgPdf.sizeBytes)
    }

    @Test
    fun `convert to and from pdf produces valid outputs`() = runTest {
        // Image to PDF
        val imgFile = File(context.cacheDir, "sample.png")
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val pickedImg = PickedFile("img", "sample.png", "image/png", imgFile.length(), imgFile.absolutePath)
        val imgPdfRes = PdfConvertToEngine.imagesToPdf(context, listOf(pickedImg), true, 20f) { _, _ -> }
        assertEquals(1, imgPdfRes.primaryFile.pageCount)

        // TXT (English + Arabic) -> PDF
        val txtFile = File(context.cacheDir, "bilingual.txt")
        txtFile.writeText("Hello PDF Master Pro\nمرحباً بكم في محرر ملفات PDF", Charsets.UTF_8)
        val pickedTxt = PickedFile("txt", "bilingual.txt", "text/plain", txtFile.length(), txtFile.absolutePath)
        val txtPdfRes = PdfConvertToEngine.textToPdf(context, pickedTxt, 14f) { _, _ -> }
        assertTrue(txtPdfRes.primaryFile.pageCount >= 1)

        // PDF -> Text, Word DOCX, Excel XLSX
        val docPdf = createSamplePdf("convert_src.pdf", listOf("Invoice 2026   Amount 500", "Total   500 USD"))
        val toText = PdfConvertFromEngine.pdfToText(context, docPdf) { _, _ -> }
        assertTrue(toText.extractedText!!.contains("Invoice 2026"))

        val toDocx = PdfConvertFromEngine.pdfToWordDocx(context, docPdf) { _, _ -> }
        ZipFile(toDocx.primaryFile.path).use { zip ->
            assertTrue(zip.getEntry("word/document.xml") != null)
        }

        val toXlsx = PdfConvertFromEngine.pdfToExcelXlsx(context, docPdf) { _, _ -> }
        ZipFile(toXlsx.primaryFile.path).use { zip ->
            assertTrue(zip.getEntry("xl/worksheets/sheet1.xml") != null)
        }
    }

    @Test
    fun `edit protect unlock search compare bookmarks metadata and pdfa work end to end`() = runTest {
        val pdfA = createSamplePdf("a.pdf", listOf("Contract Clause One\nSigned Value 100"))
        val pdfB = createSamplePdf("b.pdf", listOf("Contract Clause One\nSigned Value 250"))

        // Edit (Watermark + Page Numbers + Header/Footer)
        val edited = PdfEditEngine.applyEdits(
            context = context,
            file = pdfA,
            overlays = emptyList(),
            pageNumbers = PageNumbersOptions(enabled = true),
            watermark = WatermarkOptions(enabled = true, text = "DRAFT"),
            headerFooter = HeaderFooterOptions(enabled = true, headerText = "Top Header"),
            onProgress = { _, _ -> }
        )
        assertEquals(1, edited.primaryFile.pageCount)

        // Protect & Unlock
        val protectedRes = PdfSecurityEngine.protectPdf(context, pdfA, "secret123", "owner123", true, false, false) { _, _ -> }
        try {
            PDDocument.load(File(protectedRes.primaryFile.path)).close()
            fail("Expected InvalidPasswordException")
        } catch (_: InvalidPasswordException) {
            // Expected
        }

        val lockedPicked = PickedFile("lk", protectedRes.primaryFile.name, "application/pdf", protectedRes.primaryFile.sizeBytes, protectedRes.primaryFile.path)
        val unlockedRes = PdfSecurityEngine.unlockPdf(context, lockedPicked, "secret123") { _, _ -> }
        PDDocument.load(File(unlockedRes.primaryFile.path)).use { unlocked ->
            assertFalse(unlocked.isEncrypted)
            assertEquals(1, unlocked.numberOfPages)
        }

        // Digital signature seal
        val signedRes = PdfSecurityEngine.applyDigitalSignatureSeal(context, pdfA, "Jane Doe", "Approval", "London", null) { _, _ -> }
        assertTrue(File(signedRes.primaryFile.path).length() > pdfA.sizeBytes)

        // Search
        val searchRes = PdfAdvancedEngine.searchInsidePdf(context, pdfA, "Clause") { _, _ -> }
        assertEquals(1, searchRes.searchMatches.size)

        // Compare
        val compareRes = PdfAdvancedEngine.compareTwoPdfs(context, pdfA, pdfB) { _, _ -> }
        assertTrue(compareRes.pageComparisons.first().hasChanges)

        // Bookmarks & Metadata & PDF/A
        val bmRes = PdfAdvancedEngine.addBookmarks(context, pdfA, listOf(BookmarkInput("1", "Intro", 1))) { _, _ -> }
        PDDocument.load(File(bmRes.primaryFile.path)).use { d ->
            assertTrue(d.documentCatalog.documentOutline != null)
        }

        val metaRes = PdfAdvancedEngine.editMetadata(
            context, pdfA, PdfMetadataInfo(title = "Master Title", author = "QA Engineer")
        ) { _, _ -> }
        val inspected = PdfAdvancedEngine.inspectMetadata(
            PickedFile("m", metaRes.primaryFile.name, "application/pdf", metaRes.primaryFile.sizeBytes, metaRes.primaryFile.path)
        )
        assertEquals("Master Title", inspected.title)
        assertEquals("QA Engineer", inspected.author)

        val pdfaRes = PdfAdvancedEngine.createPdfA(context, pdfA, "Archive Doc", "QA") { _, _ -> }
        PDDocument.load(File(pdfaRes.primaryFile.path)).use { d ->
            assertTrue(d.documentCatalog.metadata != null)
        }
    }

    @Test
    fun `extract images and handle corrupted pdf gracefully`() = runTest {
        val imgPdf = createSamplePdf("with_img.pdf", listOf("Page with image"), withImage = true)
        val extracted = PdfAdvancedEngine.extractImages(context, imgPdf) { _, _ -> }
        assertTrue(extracted.primaryFile.name.endsWith(".png"))
        assertTrue(File(extracted.primaryFile.path).exists())

        // Corrupted PDF handling
        val badFile = File(context.cacheDir, "corrupt.pdf")
        badFile.writeText("NOT_A_PDF_CORRUPT_BYTES")
        val pickedBad = PickedFile("bad", "corrupt.pdf", "application/pdf", badFile.length(), badFile.absolutePath)
        var failedGracefully = false
        try {
            PdfAdvancedEngine.repairPdf(context, pickedBad) { _, _ -> }
        } catch (_: Exception) {
            failedGracefully = true
        }
        assertTrue(failedGracefully)

        // Parse page selection utility
        val parsed = FileHelper.parsePageSelection("1-2, 4", 5)
        assertEquals(listOf(0, 1, 3), parsed)
        val text = PDDocument.load(File(imgPdf.localPath)).use { PDFTextStripper().getText(it) }
        assertTrue(text.contains("Page with image"))
    }
}
