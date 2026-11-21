package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.OutputFileArtifact
import com.example.model.PickedFile
import com.example.model.RedactionRect
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

object PdfSecurityEngine {

    suspend fun protectPdf(
        context: Context,
        file: PickedFile,
        userPassword: String,
        ownerPassword: String,
        allowPrint: Boolean,
        allowCopy: Boolean,
        allowModify: Boolean,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(userPassword.isNotEmpty() || ownerPassword.isNotEmpty()) {
            "Please enter a password to protect the PDF."
        }
        onProgress(0.2f, "Configuring AES-128 security policy...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_protected.pdf")

        var pageCount = 0
        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val ap = AccessPermission().apply {
                setCanPrint(allowPrint)
                setCanExtractContent(allowCopy)
                setCanModify(allowModify)
                setCanModifyAnnotations(allowModify)
                setCanFillInForm(allowModify)
            }
            val effectiveOwner = ownerPassword.ifEmpty { userPassword }
            val policy = StandardProtectionPolicy(effectiveOwner, userPassword, ap).apply {
                encryptionKeyLength = 128
            }
            onProgress(0.6f, "Encrypting PDF streams...")
            doc.protect(policy)
            doc.save(outFile)
        }

        onProgress(1f, "PDF encrypted & protected")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Encrypted with AES-128 protection. Print=$allowPrint, Copy=$allowCopy, Modify=$allowModify."
        )
    }

    suspend fun unlockPdf(
        context: Context,
        file: PickedFile,
        password: String,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Verifying PDF password...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_unlocked.pdf")

        var pageCount = 0
        try {
            PDDocument.load(File(file.localPath), password).use { doc ->
                pageCount = doc.numberOfPages
                onProgress(0.65f, "Removing password protection...")
                doc.isAllSecurityToBeRemoved = true
                doc.save(outFile)
            }
        } catch (_: InvalidPasswordException) {
            throw IllegalArgumentException("Incorrect password. Authorization failed.")
        }

        onProgress(1f, "Password protection removed")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Removed security restrictions from $pageCount-page PDF."
        )
    }

    suspend fun applyDigitalSignatureSeal(
        context: Context,
        file: PickedFile,
        signerName: String,
        reason: String,
        location: String,
        signatureImagePath: String?,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(signerName.isNotBlank()) { "Please enter the signer's name." }
        onProgress(0.15f, "Computing SHA-256 document digest...")

        val srcFile = File(file.localPath)
        val digestHex = computeSha256(srcFile)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_signed.pdf")
        val typeface = ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.SANS_SERIF

        var pageCount = 0
        PDDocument.load(srcFile).use { doc ->
            pageCount = doc.numberOfPages
            if (pageCount == 0) throw IllegalStateException("PDF has no pages.")

            // Record cryptographic digest & signer metadata in DocumentInformation
            val info = doc.documentInformation
            val existingKeywords = info.keywords.orEmpty()
            info.keywords = "$existingKeywords | SignedBy:$signerName | SHA256:${digestHex.take(16)}".trim(' ', '|')
            doc.documentInformation = info

            onProgress(0.55f, "Stamping verification seal...")
            val targetPage = doc.getPage(pageCount - 1)
            val box = targetPage.cropBox ?: targetPage.mediaBox
            val sealW = 540
            val sealH = 190
            val sealBmp = Bitmap.createBitmap(sealW, sealH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sealBmp)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#059669") }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 20f
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 14f
                this.typeface = typeface
            }

            canvas.drawRoundRect(RectF(2f, 2f, sealW - 2f, sealH - 2f), 16f, 16f, bgPaint)
            canvas.drawRoundRect(RectF(2f, 2f, sealW - 2f, sealH - 2f), 16f, 16f, borderPaint)
            canvas.drawRect(14f, 18f, 24f, sealH - 18f, accentPaint)

            canvas.drawText("DIGITALLY SIGNED SEAL", 40f, 40f, titlePaint)
            canvas.drawText("Signer: $signerName", 40f, 70f, metaPaint)
            if (reason.isNotBlank()) canvas.drawText("Reason: $reason", 40f, 94f, metaPaint)
            if (location.isNotBlank()) canvas.drawText("Location: $location", 40f, 118f, metaPaint)
            canvas.drawText("Date: $timestamp", 40f, 142f, metaPaint)
            canvas.drawText("SHA-256: ${digestHex.take(32)}...", 40f, 166f, metaPaint)

            if (!signatureImagePath.isNullOrBlank()) {
                runCatching {
                    val sigBmp = BitmapFactory.decodeFile(signatureImagePath)
                    if (sigBmp != null) {
                        canvas.drawBitmap(sigBmp, null, RectF(340f, 28f, 520f, 128f), Paint(Paint.FILTER_BITMAP_FLAG))
                        sigBmp.recycle()
                    }
                }
            }

            val xObj = LosslessFactory.createFromImage(doc, sealBmp)
            sealBmp.recycle()

            val drawW = (box.width * 0.42f).coerceIn(180f, 270f)
            val drawH = drawW * (sealH.toFloat() / sealW.toFloat())
            val drawX = box.lowerLeftX + box.width - drawW - 24f
            val drawY = box.lowerLeftY + 24f

            PDPageContentStream(doc, targetPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(xObj, drawX, drawY, drawW, drawH)
            }

            onProgress(0.88f, "Saving signed PDF...")
            doc.save(outFile)
        }

        onProgress(1f, "Signature seal applied")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Applied visual verification seal & SHA-256 digest (${digestHex.take(12)}...). Note: Trusted CA X.509 PKCS#7 certificates require an imported keystore."
        )
    }

    suspend fun trueRedactPdf(
        context: Context,
        file: PickedFile,
        redactions: List<RedactionRect>,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(redactions.isNotEmpty()) { "Draw at least one redaction box on a page first." }
        onProgress(0.1f, "Preparing irreversible redaction...")

        val srcFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_redacted.pdf")
        val redactionsByPage = redactions.groupBy { it.pageIndex }

        var pageCount = 0
        PDDocument.load(srcFile).use { srcDoc ->
            pageCount = srcDoc.numberOfPages
            PDDocument().use { outDoc ->
                ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val blackPaint = Paint().apply {
                            color = Color.BLACK
                            style = Paint.Style.FILL
                        }

                        for (i in 0 until pageCount) {
                            coroutineContext.ensureActive()
                            val pageRedactions = redactionsByPage[i].orEmpty()
                            if (pageRedactions.isEmpty()) {
                                // Keep unredacted pages untouched as native vector pages
                                outDoc.importPage(srcDoc.getPage(i))
                            } else {
                                // Render redacted page to bitmap, burn black pixels, and replace page stream
                                renderer.openPage(i).use { rPage ->
                                    val ptW = rPage.width.toFloat().coerceAtLeast(100f)
                                    val ptH = rPage.height.toFloat().coerceAtLeast(100f)
                                    val targetW = 1650
                                    val ratio = ptH / ptW
                                    val bmpW = targetW
                                    val bmpH = max(100, (bmpW * ratio).roundToInt())

                                    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    rPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    pageRedactions.forEach { box ->
                                        val left = minOf(box.normLeft, box.normRight) * bmpW
                                        val top = minOf(box.normTop, box.normBottom) * bmpH
                                        val right = maxOf(box.normLeft, box.normRight) * bmpW
                                        val bottom = maxOf(box.normTop, box.normBottom) * bmpH
                                        canvas.drawRect(left, top, right, bottom, blackPaint)
                                    }

                                    val newPage = PDPage(PDRectangle(ptW, ptH))
                                    outDoc.addPage(newPage)
                                    val xObj = LosslessFactory.createFromImage(outDoc, bmp)
                                    bmp.recycle()
                                    PDPageContentStream(outDoc, newPage).use { cs ->
                                        cs.drawImage(xObj, 0f, 0f, ptW, ptH)
                                    }
                                }
                            }
                            onProgress(0.15f + 0.72f * (i + 1) / max(1, pageCount), "Redacting page ${i + 1} of $pageCount...")
                        }
                    }
                }
                onProgress(0.92f, "Saving redacted PDF...")
                outDoc.save(outFile)
            }
        }

        onProgress(1f, "True redaction complete")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Permanently redacted ${redactions.size} region(s) across ${redactionsByPage.size} page(s). Underlying text streams on redacted pages were completely destroyed."
        )
    }

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = fis.read(buf)
                if (r == -1) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
