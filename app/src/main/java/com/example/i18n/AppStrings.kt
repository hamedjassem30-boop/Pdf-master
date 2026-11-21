package com.example.i18n

import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.model.ToolCategory

data class ToolText(val title: String, val description: String)

object AppStrings {

    fun isArabic(lang: AppLanguage): Boolean = lang == AppLanguage.AR

    fun appTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "بي دي إف ماستر برو" else "PDF Master Pro"
    fun navHome(lang: AppLanguage) = if (lang == AppLanguage.AR) "الرئيسية" else "Home"
    fun navTools(lang: AppLanguage) = if (lang == AppLanguage.AR) "الأدوات" else "Tools"
    fun navDashboard(lang: AppLanguage) = if (lang == AppLanguage.AR) "لوحة التحكم" else "Dashboard"
    fun navEngine(lang: AppLanguage) = if (lang == AppLanguage.AR) "المحرك" else "Engine"

    fun heroBadge(lang: AppLanguage) = if (lang == AppLanguage.AR) "معالجة محلية آمنة 100%" else "100% ON-DEVICE PROCESSING"
    fun heroHeadline(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "كل ما تحتاجه للعمل مع ملفات PDF."
    else
        "Everything You Need to Work with PDF Files."

    fun heroSubtitle(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "ادمج، قسّم، اضغط، حوّل، حرّر، احمِ، واستخرج النصوص بالذكاء المحلي مباشرة على جهازك بدون رفع ملفاتك."
    else
        "Merge, split, compress, convert, edit, redact, OCR, and secure your documents locally with zero cloud uploads."

    fun uploadDropTitle(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "اسحب الملفات هنا أو اضغط للاختيار"
    else
        "Drag & drop files here or tap to browse"

    fun uploadDropSubtitle(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "يدعم PDF، الصور (JPG/PNG/WEBP)، Word، Excel، PowerPoint، HTML و TXT"
    else
        "Supports PDF, Images (JPG/PNG/WEBP), DOCX, XLSX, PPTX, HTML & TXT"

    fun selectFilesBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "اختيار الملفات" else "Select Files"
    fun addMoreFilesBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "إضافة ملفات" else "Add Files"
    fun clearAllBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "مسح الكل" else "Clear"

    fun searchPlaceholder(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "ابحث عن أداة (دمج، ضغط، OCR، تحويل...)"
    else
        "Search 44 PDF tools (merge, compress, OCR, redact...)"

    fun quickLaunchTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "اختيار سريع للأداة بعد رفع الملف" else "Quick Tool Match"
    fun frequentlyUsedTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "الأدوات الأكثر استخداماً" else "Frequently Used Tools"
    fun popularToolsTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "أدوات شائعة" else "Popular PDF Tools"
    fun categoriesTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "تصنيفات أدوات PDF" else "PDF Tool Categories"
    fun recentFilesTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "الملفات الأخيرة" else "Recent Files"
    fun favoritesTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "المفضلة" else "Favorite Tools"
    fun allToolsChip(lang: AppLanguage) = if (lang == AppLanguage.AR) "الكل" else "All"

    fun footerTitle(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "PDF Master Pro — خصوصية كاملة على جهازك"
    else
        "PDF Master Pro — Built for Speed & Local Privacy"

    fun footerDesc(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "تتم جميع عمليات PDF محلياً باستخدام PDFBox و Android PdfRenderer و ML Kit بدون تخزين دائم في السحابة."
    else
        "All PDF operations execute locally via PDFBox, Android PdfRenderer & on-device ML Kit. No files leave your device."

    // Dashboard
    fun dashboardTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "لوحة التحكم والإحصائيات" else "Workspace Dashboard"
    fun totalProcessedLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "المستندات المعالجة" else "Documents Processed"
    fun totalSavedLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "المساحة الموفرة" else "Space Saved"
    fun favoritesCountLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "أدوات مفضلة" else "Favorite Tools"
    fun recentActivityTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "النشاط الأخير" else "Recent Activity"
    fun quickActionsTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "إجراءات سريعة" else "Quick Actions"
    fun cleanCacheBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "تنظيف الملفات المؤقتة" else "Clean Temporary Files"
    fun cacheCleanedMsg(lang: AppLanguage) = if (lang == AppLanguage.AR) "تم تنظيف ذاكرة التخزين المؤقت بنجاح" else "Temporary cache cleaned"
    fun noRecentFilesTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "لا توجد ملفات معالجة بعد" else "No processed documents yet"
    fun noRecentFilesHint(lang: AppLanguage) = if (lang == AppLanguage.AR)
        "اختر أي أداة لمعالجة أول مستند PDF وسيتاح هنا فوراً."
    else
        "Pick any tool to process your first document. Real outputs and history appear here."

    // Workspace common
    fun uploadedFilesHeader(lang: AppLanguage) = if (lang == AppLanguage.AR) "الملفات المحددة" else "Selected Files"
    fun processingOptionsHeader(lang: AppLanguage) = if (lang == AppLanguage.AR) "خيارات المعالجة" else "Processing Options"
    fun pagePreviewsHeader(lang: AppLanguage) = if (lang == AppLanguage.AR) "معاينة الصفحات وتنظيمها" else "Page Thumbnails & Organizer"
    fun processNowBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "بدء المعالجة" else "Process File"
    fun processingTitle(lang: AppLanguage) = if (lang == AppLanguage.AR) "جاري المعالجة..." else "Processing Document..."
    fun cancelBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "إلغاء" else "Cancel"
    fun successHeader(lang: AppLanguage) = if (lang == AppLanguage.AR) "تمت المعالجة بنجاح!" else "Completed Successfully!"
    fun errorHeader(lang: AppLanguage) = if (lang == AppLanguage.AR) "تعذرت المعالجة" else "Processing Failed"
    fun saveToDeviceBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "حفظ في الجهاز" else "Save to Device"
    fun shareBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "مشاركة" else "Share"
    fun previewResultBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "معاينة النتيجة" else "Preview Result"
    fun downloadZipBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "تنزيل الكل كـ ZIP" else "Download ZIP"
    fun processAnotherBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "معالجة ملف آخر" else "Process Another File"
    fun copyTextBtn(lang: AppLanguage) = if (lang == AppLanguage.AR) "نسخ النص" else "Copy Text"
    fun copiedMsg(lang: AppLanguage) = if (lang == AppLanguage.AR) "تم نسخ النص" else "Copied to clipboard"
    fun savedSuccessMsg(lang: AppLanguage) = if (lang == AppLanguage.AR) "تم حفظ الملف بنجاح" else "File saved to device"
    fun pagesLabel(lang: AppLanguage, count: Int) = if (lang == AppLanguage.AR) "$count صفحة" else "$count pages"
    fun originalSizeLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "الحجم الأصلي" else "Original Size"
    fun compressedSizeLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "الحجم الجديد" else "New Size"
    fun reductionLabel(lang: AppLanguage) = if (lang == AppLanguage.AR) "نسبة الضغط" else "Reduction"
    fun duplicateWarning(lang: AppLanguage) = if (lang == AppLanguage.AR) "تم تخطي ملف مكرر:" else "Skipped duplicate file:"

    fun categoryName(cat: ToolCategory, lang: AppLanguage): String = when (cat) {
        ToolCategory.ORGANIZE -> if (lang == AppLanguage.AR) "تنظيم PDF" else "Organize PDF"
        ToolCategory.OPTIMIZE -> if (lang == AppLanguage.AR) "تحسين وضغط" else "Optimize PDF"
        ToolCategory.CONVERT_TO_PDF -> if (lang == AppLanguage.AR) "التحويل إلى PDF" else "Convert to PDF"
        ToolCategory.CONVERT_FROM_PDF -> if (lang == AppLanguage.AR) "التحويل من PDF" else "Convert from PDF"
        ToolCategory.EDIT -> if (lang == AppLanguage.AR) "تحرير PDF" else "Edit PDF"
        ToolCategory.SECURITY -> if (lang == AppLanguage.AR) "أمان PDF" else "Security PDF"
        ToolCategory.ADVANCED -> if (lang == AppLanguage.AR) "أدوات متقدمة" else "Advanced Tools"
    }

    fun toolText(tool: PdfToolId, lang: AppLanguage): ToolText = when (tool) {
        PdfToolId.MERGE_PDF -> if (lang == AppLanguage.AR)
            ToolText("دمج PDF", "اجمع عدة ملفات PDF في مستند واحد بالترتيب الذي تريده")
        else ToolText("Merge PDF", "Combine multiple PDF files into one unified document in exact order")

        PdfToolId.SPLIT_PDF -> if (lang == AppLanguage.AR)
            ToolText("تقسيم PDF", "قسّم مستند PDF إلى ملفات متعددة حسب النطاقات أو كل صفحة")
        else ToolText("Split PDF", "Separate one PDF into multiple files by page ranges, intervals, or single pages")

        PdfToolId.EXTRACT_PAGES -> if (lang == AppLanguage.AR)
            ToolText("استخراج صفحات", "احفظ الصفحات المحددة فقط في ملف PDF جديد")
        else ToolText("Extract Pages", "Pick specific pages visually or by range and export them as a new PDF")

        PdfToolId.DELETE_PAGES -> if (lang == AppLanguage.AR)
            ToolText("حذف صفحات", "أزل الصفحات غير المرغوب فيها من المستند بسهولة")
        else ToolText("Delete Pages", "Remove unwanted pages from your PDF with thumbnail previews")

        PdfToolId.REORDER_PAGES -> if (lang == AppLanguage.AR)
            ToolText("إعادة ترتيب الصفحات", "اسحب وبدّل ترتيب صفحات PDF بالطريقة المناسبة")
        else ToolText("Reorder Pages", "Visually rearrange PDF pages in any order before saving")

        PdfToolId.ROTATE_PAGES -> if (lang == AppLanguage.AR)
            ToolText("تدوير الصفحات", "دوّر صفحات محددة أو جميع الصفحات بزاوية 90° أو 180° أو 270°")
        else ToolText("Rotate PDF", "Rotate individual pages or the entire document cleanly")

        PdfToolId.DUPLICATE_PAGES -> if (lang == AppLanguage.AR)
            ToolText("تكرار الصفحات", "أنشئ نسخاً مكررة من صفحات مختارة داخل نفس الملف")
        else ToolText("Duplicate Pages", "Clone selected pages one or more times inside your PDF")

        PdfToolId.VISUAL_ORGANIZER -> if (lang == AppLanguage.AR)
            ToolText("المنظم المرئي", "رتّب، دوّر، كرّر، واحذف الصفحات عبر شبكة مصغرات تفاعلية")
        else ToolText("Visual Organizer", "All-in-one thumbnail studio to reorder, rotate, duplicate & delete pages")

        PdfToolId.COMPRESS_PDF -> if (lang == AppLanguage.AR)
            ToolText("ضغط PDF", "قلّل حجم الملف مع الحفاظ على جودة القراءة وعرض نسبة التوفير")
        else ToolText("Compress PDF", "Reduce PDF file size with Low/Medium/High levels and view savings")

        PdfToolId.JPG_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("JPG إلى PDF", "حوّل صورة أو عدة صور JPG إلى مستند PDF عالي الجودة")
        else ToolText("JPG to PDF", "Convert one or multiple JPG photos into a clean paginated PDF")

        PdfToolId.PNG_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("PNG إلى PDF", "حوّل صور PNG بشفافيتها إلى صفحات PDF مرتبة")
        else ToolText("PNG to PDF", "Convert PNG graphics and screenshots into a PDF document")

        PdfToolId.WEBP_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("WEBP إلى PDF", "حوّل صور WEBP الحديثة إلى ملف PDF قياسي")
        else ToolText("WEBP to PDF", "Convert WEBP images directly into standard PDF pages")

        PdfToolId.HTML_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("HTML إلى PDF", "حوّل صفحات HTML أو الكود المنسق إلى مستند PDF")
        else ToolText("HTML to PDF", "Render HTML files or markup into paginated A4 PDF documents")

        PdfToolId.TXT_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("TXT إلى PDF", "حوّل الملفات النصية (عربي/إنجليزي) إلى PDF منسق")
        else ToolText("TXT to PDF", "Convert plain UTF-8 text files (LTR & Arabic RTL) into crisp PDFs")

        PdfToolId.WORD_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("Word إلى PDF", "حوّل مستندات DOCX إلى ملفات PDF قابلة للمشاركة")
        else ToolText("Word to PDF", "Parse DOCX documents into paginated PDFs locally on device")

        PdfToolId.EXCEL_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("Excel إلى PDF", "حوّل جداول XLSX و CSV إلى صفحات PDF منظمة")
        else ToolText("Excel to PDF", "Convert XLSX spreadsheets and CSV tables into clean PDF tables")

        PdfToolId.PPT_TO_PDF -> if (lang == AppLanguage.AR)
            ToolText("PowerPoint إلى PDF", "حوّل عروض PPTX التقديمية إلى شرائح PDF")
        else ToolText("PowerPoint to PDF", "Convert PPTX presentations into landscape PDF slide decks")

        PdfToolId.PDF_TO_JPG -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى JPG", "صدّر كل صفحة PDF كصورة JPG عالية الدقة أو ملف ZIP")
        else ToolText("PDF to JPG", "Render PDF pages as high-resolution JPG images or ZIP archive")

        PdfToolId.PDF_TO_PNG -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى PNG", "حوّل صفحات PDF إلى صور PNG واضحة بدون فقدان تفاصيل")
        else ToolText("PDF to PNG", "Export PDF pages as crisp lossless PNG images")

        PdfToolId.PDF_TO_WEBP -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى WEBP", "حوّل صفحات PDF إلى صور WEBP خفيفة الحجم")
        else ToolText("PDF to WEBP", "Export PDF pages to efficient WEBP image format")

        PdfToolId.PDF_TO_TEXT -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى نص", "استخرج جميع النصوص من PDF واحفظها كملف TXT")
        else ToolText("PDF to Text", "Extract clean readable text from your PDF into a UTF-8 TXT file")

        PdfToolId.PDF_TO_WORD -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى Word", "حوّل محتوى PDF إلى مستند DOCX قابل للتحرير")
        else ToolText("PDF to Word", "Convert PDF text content into a valid editable DOCX document")

        PdfToolId.PDF_TO_EXCEL -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى Excel", "استخرج الجداول والصفوف إلى ملف XLSX حقيقي")
        else ToolText("PDF to Excel", "Extract structured rows into a valid XLSX Excel workbook")

        PdfToolId.PDF_TO_PPT -> if (lang == AppLanguage.AR)
            ToolText("PDF إلى PowerPoint", "حوّل صفحات PDF إلى شرائح عرض PPTX حقيقية")
        else ToolText("PDF to PowerPoint", "Turn PDF pages into a genuine PPTX slide presentation")

        PdfToolId.EDIT_PDF -> if (lang == AppLanguage.AR)
            ToolText("استوديو تحرير PDF", "أضف نصوصاً، صوراً، رسوماً، تظليلاً، أشكالاً، توقيعاً وتاريخاً مع معاينة فورية")
        else ToolText("Edit PDF Studio", "Add text, logos, freehand ink, highlights, shapes, signatures & dates")

        PdfToolId.PAGE_NUMBERS -> if (lang == AppLanguage.AR)
            ToolText("ترقيم الصفحات", "أدرج أرقام الصفحات في الموضع والتنسيق الذي تفضله")
        else ToolText("Page Numbers", "Insert customizable page numbers across your PDF document")

        PdfToolId.WATERMARK -> if (lang == AppLanguage.AR)
            ToolText("علامة مائية", "أضف نص علامة مائية مع التحكم بالشفافية والزاوية والحجم")
        else ToolText("Watermark", "Stamp custom diagonal or centered text watermarks with opacity")

        PdfToolId.HEADER_FOOTER -> if (lang == AppLanguage.AR)
            ToolText("الرأس والتذييل", "أضف ترويسة وتذييل مخصص على صفحات المستند")
        else ToolText("Header & Footer", "Apply custom top headers and bottom footers across pages")

        PdfToolId.PROTECT_PDF -> if (lang == AppLanguage.AR)
            ToolText("حماية بكلمة مرور", "اقفل ملف PDF بكلمة مرور وتشفير AES قوي")
        else ToolText("Protect PDF", "Lock your PDF with a strong password and AES-128 encryption")

        PdfToolId.UNLOCK_PDF -> if (lang == AppLanguage.AR)
            ToolText("فتح قفل PDF", "أزل حماية كلمة المرور عند إدخال الكلمة الصحيحة")
        else ToolText("Unlock PDF", "Decrypt a password-protected PDF when you have authorization")

        PdfToolId.ENCRYPT_PDF -> if (lang == AppLanguage.AR)
            ToolText("تشفير وصلاحيات PDF", "تحكم بصلاحيات الطباعة والنسخ والتعديل مع تشفير AES")
        else ToolText("Encrypt & Permissions", "Configure granular print/copy/edit restrictions with AES encryption")

        PdfToolId.REMOVE_PASSWORD -> if (lang == AppLanguage.AR)
            ToolText("إزالة كلمة المرور", "احفظ نسخة غير مشفرة من ملف PDF محمي بعد التحقق")
        else ToolText("Remove Password", "Strip password security permanently from an authorized PDF")

        PdfToolId.DIGITAL_SIGNATURE -> if (lang == AppLanguage.AR)
            ToolText("توقيع وختم رقمي", "أضف ختم تحقق رقمي ببصمة SHA-256 وبيانات الموقّع")
        else ToolText("Digital Signature Seal", "Apply a visual verification seal with SHA-256 document digest")

        PdfToolId.REDACT_PDF -> if (lang == AppLanguage.AR)
            ToolText("طمس وحذف دائم", "اطمس المحتوى الحساس نهائياً بتدمير النص الأصلي في المنطقة المحددة")
        else ToolText("True Redact PDF", "Permanently destroy selected regions so hidden text cannot be copied")

        PdfToolId.OCR_PDF -> if (lang == AppLanguage.AR)
            ToolText("OCR استخراج النص من الصور", "تعرّف على النصوص في ملفات PDF الممسوحة ضوئياً محلياً")
        else ToolText("OCR PDF", "Run on-device ML Kit text recognition on scanned PDF pages")

        PdfToolId.SEARCH_PDF -> if (lang == AppLanguage.AR)
            ToolText("البحث داخل PDF", "ابحث عن أي كلمة أو عبارة واعرض أرقام الصفحات والمقتطفات")
        else ToolText("Search Inside PDF", "Find keywords across all pages with match counts and snippets")

        PdfToolId.EXTRACT_IMAGES -> if (lang == AppLanguage.AR)
            ToolText("استخراج الصور", "استخرج كل الصور المدمجة داخل PDF كملفات PNG أو ZIP")
        else ToolText("Extract Images", "Extract embedded raster images from PDF pages as PNGs or ZIP")

        PdfToolId.EXTRACT_TEXT -> if (lang == AppLanguage.AR)
            ToolText("استخراج كل النصوص", "استخرج جميع النصوص وانسخها أو احفظها بترميز UTF-8")
        else ToolText("Extract All Text", "Pull complete plain text across all pages with instant copy")

        PdfToolId.COMPARE_PDF -> if (lang == AppLanguage.AR)
            ToolText("مقارنة ملفي PDF", "قارن بين ملفين واكتشف الفروقات النصية والهيكلية صفحة بصفحة")
        else ToolText("Compare Two PDFs", "Diff two PDF documents page-by-page to spot added or removed lines")

        PdfToolId.ADD_BOOKMARKS -> if (lang == AppLanguage.AR)
            ToolText("إضافة إشارات مرجعية", "أنشئ فهرس إشارات مرجعية ينقل القارئ لأي صفحة")
        else ToolText("Add Bookmarks", "Build clickable PDF outline bookmarks linked to target pages")

        PdfToolId.REPAIR_PDF -> if (lang == AppLanguage.AR)
            ToolText("إصلاح ملف PDF تالف", "أعد بناء بنية PDF التالفة واستنقذ الصفحات القابلة للقراءة")
        else ToolText("Repair PDF", "Rebuild corrupted PDF cross-references or rescue readable pages")

        PdfToolId.METADATA_INSPECT -> if (lang == AppLanguage.AR)
            ToolText("فحص بيانات المستند", "اعرض المؤلف، العنوان، التشفير، الإصدار، وتواريخ الإنشاء")
        else ToolText("Inspect Metadata", "Detect author, producer, PDF version, encryption & page stats")

        PdfToolId.EDIT_METADATA -> if (lang == AppLanguage.AR)
            ToolText("تعديل بيانات المستند", "عدّل العنوان، المؤلف، الموضوع، والكلمات المفتاحية للملف")
        else ToolText("Edit Metadata", "Modify Title, Author, Subject, Keywords & Creator metadata")

        PdfToolId.CREATE_PDFA -> if (lang == AppLanguage.AR)
            ToolText("إنشاء PDF/A للأرشفة", "أضف بيانات XMP للأرشفة طويلة المدى وفق معيار PDF/A-1b")
        else ToolText("Create PDF/A", "Embed PDF/A-1b archival XMP metadata & sRGB output intent")
    }
}
