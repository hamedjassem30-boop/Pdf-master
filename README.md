# PDF Master Pro (Android — Kotlin & Jetpack Compose)

A complete, production-grade mobile PDF processing suite inspired by iLovePDF, built natively for Android with **Kotlin**, **Jetpack Compose (Material 3)**, **Apache PDFBox-Android**, **Android PdfRenderer / PdfDocument**, **Google ML Kit Text Recognition**, **Room**, and **DataStore**.

## Architecture
- `com.example.model` — Domain models for all 44 tools, 7 categories, page organization states, edit overlays, redaction boxes, metadata, and diff comparisons.
- `com.example.i18n` — Complete bilingual English (`LTR`) and Arabic (`RTL`) localization dictionary with dynamic layout direction switching.
- `com.example.data` — Room database (`processed_records`, `favorite_tools`, `tool_usage`), DataStore preferences (`AppLanguage`, `ThemeMode`, saved signature), and repository with real storage metrics & cache cleanup.
- `com.example.engine` — Modular on-device PDF processing engines:
  - `FileHelper` — SAF import, validation, duplicate detection, ZIP packaging, `FileProvider` sharing.
  - `PdfThumbnailRenderer` — Thread-safe lazy page bitmap renderer.
  - `PdfOrganizeEngine` — Merge, Split, Extract, Delete, Reorder, Rotate, Duplicate, Visual Organizer.
  - `PdfOptimizeEngine` — Multi-level compression (Low / Medium / High + optional rasterize mode) with real size reduction metrics.
  - `PdfConvertToEngine` — JPG, PNG, WEBP, HTML, TXT (with Arabic RTL), DOCX, XLSX/CSV, and PPTX to PDF.
  - `PdfConvertFromEngine` — PDF to JPG/PNG/WEBP (+ ZIP), Text, DOCX, XLSX, and PPTX.
  - `PdfEditEngine` — Visual studio overlays (Text, Image/Logo, Freehand Draw, Highlighter, Rect/Circle, Signature Pad, Date Stamp), Page Numbers, Watermarks, Headers & Footers.
  - `PdfSecurityEngine` — AES-128 Password Protection & Permissions, Authorized Unlock, SHA-256 Digital Verification Seal, and True Irreversible Page Redaction.
  - `PdfAdvancedEngine` — On-device ML Kit OCR, Full-Text Search, Image Extraction, Two-PDF Line Diff Comparison, Outline Bookmarks, XRef/Page Rescue Repair, Metadata Inspector/Editor, and PDF/A-1b XMP Archival Tagging.
- `com.example.ui` — Adaptive Material 3 Compose UI (`NavigationBar` on phones, `NavigationRail` on tablets/foldables) with Deep Navy, White/Slate, and Crimson Red identity, Light & Dark modes, and dedicated workspaces.

## Features Requiring a Server-Side Backend (Transparently Documented)
While all 44 tools run locally on-device for maximum privacy, the following specialized edge cases require an external backend (e.g., LibreOffice headless, Tesseract Arabic traineddata, Ghostscript/veraPDF, or CA HSM):
1. Legacy binary Office formats (`.doc`, `.xls`, `.ppt`) and complex charts/macros requiring full LibreOffice layout rendering.
2. Offline Arabic/CJK OCR (on-device ML Kit uses Latin script; Arabic OCR requires Tesseract Arabic `ara.traineddata` or Cloud Vision).
3. CA-trusted PKCS#7 / LTV cryptographic signing with external hardware tokens / TSA servers.
4. Strict ISO 19005 PDF/A font glyph subsetting & veraPDF compliance verification.

## Build & Test
```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```
