package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates an official, multi-page PDF user manual for Excel & Image Vault
 * and provides sharing / viewing utilities.
 */
object UserManualPdfManager {

    private const val MANUAL_FILE_NAME = "Excel_and_Image_Vault_User_Manual.pdf"

    // A4 dimensions at 72 DPI
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 42
    private const val MARGIN_TOP = 46
    private const val MARGIN_BOTTOM = 46
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2) // 511 pt

    data class ManualSection(
        val title: String,
        val iconEmoji: String,
        val contents: List<String>
    )

    val SECTIONS = listOf(
        ManualSection(
            title = "1. Introduction & Overview",
            iconEmoji = "📘",
            contents = listOf(
                "Excel & Image Vault is a dual-purpose mobile workstation engineered for high-security media storage and spreadsheet-driven catalog searching.",
                "Key Capabilities:",
                "• Excel Catalog Search: Instant lookup and dynamic image filtering based on Excel data sheets.",
                "• Encrypted DAT Vault: AES-256 encrypted image containers (.dat) with fixed-size allocation and dummy-padding for deniability.",
                "• Dedicated Storage: Files reside in Android/media to prevent memory bloat and simplify file management.",
                "• Multi-Layer Security: 6-Digit PIN, Master Password, Android Biometrics, and Anti-Screenshot protection."
            )
        ),
        ManualSection(
            title = "2. Security & Lockscreen Authentication",
            iconEmoji = "🛡️",
            contents = listOf(
                "The application provides robust lockscreen protection to guard confidential photos and catalogs.",
                "• 6-Digit PIN Protection: Set a quick-access 6-digit PIN. Failed attempts are rate-limited to prevent brute-force attacks.",
                "• Master Password: Used as an administrative override for critical operations and PIN changes.",
                "• Biometric Fingerprint: Seamless authentication using your device's native fingerprint or face sensor.",
                "• Auto-Lock Timer: Automatically locks the application when backgrounded (configurable from Immediate to 30 minutes in Settings).",
                "• Anti-Screenshot & Screen Recording Prevention: Enforces Android FLAG_SECURE to block screen captures and task switcher snooping."
            )
        ),
        ManualSection(
            title = "3. Fingerprint Registration & Backup File",
            iconEmoji = "🔐",
            contents = listOf(
                "The app includes an advanced biometric recovery protocol located in Settings -> Fingerprint Registration:",
                "• Registration: Tapping 'Register Fingerprint' triggers the Android BiometricPrompt. Upon verification, the app generates a cryptographically signed backup file: fingerprint_backup.dat.",
                "• Storage Path: The backup file is preserved in Android/media/com.example/security/fingerprint/fingerprint_backup.dat.",
                "• Export / Sharing: Use the 'Export' button in Settings to transfer your fingerprint backup file to external storage or a secure drive.",
                "• Cross-Device Ready: You can transfer this file to a new device or secondary tablet to unlock the app seamlessly."
            )
        ),
        ManualSection(
            title = "4. Emergency Recovery: Unlocking with Backup Fingerprint",
            iconEmoji = "🔑",
            contents = listOf(
                "If you forget your 6-digit PIN or need to access your vault on a newly set up device:",
                "1. On the PIN Lockscreen, tap the 'Use Backup Fingerprint' button below the numeric keypad.",
                "2. The Android system document picker will open. Select your saved fingerprint_backup.dat file.",
                "3. The app cryptographically verifies the signature and security hash.",
                "4. Once verified, the lockscreen immediately unlocks and presents an option to set a brand-new 6-digit PIN."
            )
        ),
        ManualSection(
            title = "5. Encrypted DAT Image Vault",
            iconEmoji = "🗄️",
            contents = listOf(
                "The Image Vault allows you to package sensitive photos into a single encrypted binary container (.dat):",
                "• Creation: Create a container with custom target size (e.g., 50 MB, 100 MB, 500 MB) padded with uniform 0xAA bytes to conceal file counts.",
                "• High-Performance Streaming Engine: AES encryption streams in memory-safe 64KB chunks, effortlessly supporting over 1,000 photos without Out-Of-Memory crashes.",
                "• Image Extraction: Unpack images to a dedicated folder or view photos within the in-app lightbox.",
                "• Auto-Expansion: When importing images exceeding original capacity, the container safely auto-expands with headroom.",
                "• Storage Location: Containers are stored in Android/media/com.example/Vault_Containers."
            )
        ),
        ManualSection(
            title = "6. Excel Catalog & Dynamic Image Matching",
            iconEmoji = "📊",
            contents = listOf(
                "Turn Excel spreadsheets into an interactive, visual search catalog:",
                "• Import: Load .xlsx or .xls files via the File Picker in the Excel Catalog tab.",
                "• Dynamic Column Filter: Select any column (e.g., SKU, Model, Name, Category) and instantly query rows.",
                "• Image Linking: Associate product codes with local gallery images for instant visual identification."
            )
        ),
        ManualSection(
            title = "7. SShow Slideshow Presentation Mode",
            iconEmoji = "🎬",
            contents = listOf(
                "Present catalog photos or vault images in an interactive slideshow:",
                "• Configurable Speed: Adjust transition interval from 1 to 10 seconds in Settings.",
                "• Smooth Transitions: Choose between Fade, Slide, or Zoom animation styles.",
                "• Loop Option: Seamless continuous loop playback for kiosk or catalog presentations."
            )
        ),
        ManualSection(
            title = "8. Storage Locations & Directory Guide",
            iconEmoji = "📁",
            contents = listOf(
                "All app assets are organized in Android/media/com.example for easy backup and PC transfer:",
                "• Security Files: Android/media/.../security/",
                "• Fingerprint Backup: Android/media/.../security/fingerprint/fingerprint_backup.dat",
                "• Vault Containers: Android/media/.../Vault_Containers/",
                "• Extracted Images: Android/media/.../vault_extracted/",
                "• SShow Storage: Android/media/.../SShow_Stored/"
            )
        ),
        ManualSection(
            title = "9. Frequently Asked Questions (FAQ) & Troubleshooting",
            iconEmoji = "❓",
            contents = listOf(
                "Q: What if I forget both my PIN and Master Password?",
                "A: Use the 'Use Backup Fingerprint' button on the lockscreen to unlock using your exported fingerprint_backup.dat file.",
                "",
                "Q: Why are screenshots blocked?",
                "A: Anti-Screenshot is enabled by default for banking-grade security. You can toggle this setting in Settings -> App Security.",
                "",
                "Q: How do I back up my entire vault to my computer?",
                "A: Connect your phone to PC via USB, navigate to Android/media/com.example, and copy the Vault_Containers and security folders."
            )
        )
    )

    /**
     * Generates the multi-page PDF manual and saves it to dedicated media directory.
     * Returns the generated File.
     */
    fun generatePdf(context: Context): File {
        val destDir = AppStorageHelper.getDedicatedMediaDir(context)
        val pdfFile = File(destDir, MANUAL_FILE_NAME)

        val document = PdfDocument()

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(40, 44, 52)
            textSize = 10.5f
        }

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(15, 76, 129) // Deep blue primary
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(15, 76, 129)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(100, 110, 125)
            textSize = 11f
        }

        val linePaint = Paint().apply {
            color = android.graphics.Color.rgb(220, 225, 230)
            strokeWidth = 1f
        }

        val pageNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(140, 145, 155)
            textSize = 9f
            textAlign = Paint.Align.RIGHT
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(140, 145, 155)
            textSize = 9f
            textAlign = Paint.Align.LEFT
        }

        var currentPageNumber = 1
        var currentPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create())
        var currentCanvas = currentPage.canvas
        var currentY = MARGIN_TOP

        fun drawHeaderAndFooter(canvas: Canvas, pageNum: Int) {
            // Header bar
            canvas.drawText("Excel & Image Vault — User Manual & Technical Guide", MARGIN_X.toFloat(), 30f, brandPaint)
            val dateStr = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date())
            canvas.drawText("Confidential & User Guide • $dateStr", (PAGE_WIDTH - MARGIN_X).toFloat(), 30f, pageNumberPaint)
            canvas.drawLine(MARGIN_X.toFloat(), 35f, (PAGE_WIDTH - MARGIN_X).toFloat(), 35f, linePaint)

            // Footer bar
            val footerY = (PAGE_HEIGHT - 25).toFloat()
            canvas.drawLine(MARGIN_X.toFloat(), footerY - 10f, (PAGE_WIDTH - MARGIN_X).toFloat(), footerY - 10f, linePaint)
            canvas.drawText("Excel & Image Vault for Android", MARGIN_X.toFloat(), footerY, brandPaint)
            canvas.drawText("Page $pageNum", (PAGE_WIDTH - MARGIN_X).toFloat(), footerY, pageNumberPaint)
        }

        fun checkPageBreak(neededHeight: Int) {
            if (currentY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM) {
                drawHeaderAndFooter(currentCanvas, currentPageNumber)
                document.finishPage(currentPage)
                currentPageNumber++
                currentPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create())
                currentCanvas = currentPage.canvas
                currentY = MARGIN_TOP + 10
            }
        }

        fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.15f, 2f, false)
            }
        }

        // Draw Document Cover Header
        drawHeaderAndFooter(currentCanvas, currentPageNumber)

        val docTitleLayout = createStaticLayout("Excel & Image Vault", titlePaint, CONTENT_WIDTH)
        currentCanvas.save()
        currentCanvas.translate(MARGIN_X.toFloat(), currentY.toFloat())
        docTitleLayout.draw(currentCanvas)
        currentCanvas.restore()
        currentY += docTitleLayout.height + 4

        val docSubLayout = createStaticLayout(
            "Official Operating Manual, Security Architecture & Recovery Guide\nVersion 3.0 • Hardware-Accelerated Streaming Security",
            subtitlePaint,
            CONTENT_WIDTH
        )
        currentCanvas.save()
        currentCanvas.translate(MARGIN_X.toFloat(), currentY.toFloat())
        docSubLayout.draw(currentCanvas)
        currentCanvas.restore()
        currentY += docSubLayout.height + 14

        currentCanvas.drawLine(MARGIN_X.toFloat(), currentY.toFloat(), (PAGE_WIDTH - MARGIN_X).toFloat(), currentY.toFloat(), linePaint)
        currentY += 16

        // Render all sections
        for (section in SECTIONS) {
            val sectionHeader = "${section.iconEmoji}  ${section.title}"
            val headerLayout = createStaticLayout(sectionHeader, headerPaint, CONTENT_WIDTH)

            checkPageBreak(headerLayout.height + 30)

            // Section background highlight banner
            val bannerPaint = Paint().apply {
                color = android.graphics.Color.rgb(244, 247, 251)
            }
            currentCanvas.drawRoundRect(
                MARGIN_X.toFloat() - 4f,
                currentY.toFloat() - 3f,
                (PAGE_WIDTH - MARGIN_X).toFloat() + 4f,
                (currentY + headerLayout.height + 5).toFloat(),
                6f,
                6f,
                bannerPaint
            )

            currentCanvas.save()
            currentCanvas.translate(MARGIN_X.toFloat(), currentY.toFloat())
            headerLayout.draw(currentCanvas)
            currentCanvas.restore()
            currentY += headerLayout.height + 10

            for (paragraph in section.contents) {
                if (paragraph.isBlank()) {
                    currentY += 6
                    continue
                }

                val bodyLayout = createStaticLayout(paragraph, textPaint, CONTENT_WIDTH)
                checkPageBreak(bodyLayout.height + 8)

                currentCanvas.save()
                currentCanvas.translate(MARGIN_X.toFloat(), currentY.toFloat())
                bodyLayout.draw(currentCanvas)
                currentCanvas.restore()
                currentY += bodyLayout.height + 5
            }

            currentY += 12
        }

        // Finish last page
        drawHeaderAndFooter(currentCanvas, currentPageNumber)
        document.finishPage(currentPage)

        // Write to file
        FileOutputStream(pdfFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return pdfFile
    }

    /**
     * Creates an Intent to share or view the generated PDF manual.
     */
    fun createManualViewIntent(context: Context): Intent? {
        val file = File(AppStorageHelper.getDedicatedMediaDir(context), MANUAL_FILE_NAME)
        val targetFile = if (file.exists() && file.length() > 0L) file else generatePdf(context)

        return try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Creates an Intent to share the PDF manual with other apps (Drive, Gmail, Bluetooth, WhatsApp, etc.).
     */
    fun createManualShareIntent(context: Context): Intent? {
        val file = File(AppStorageHelper.getDedicatedMediaDir(context), MANUAL_FILE_NAME)
        val targetFile = if (file.exists() && file.length() > 0L) file else generatePdf(context)

        return try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Excel & Image Vault — Official User Manual (PDF)")
                putExtra(Intent.EXTRA_TEXT, "Here is the official User Manual PDF for Excel & Image Vault with complete security and recovery instructions.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
