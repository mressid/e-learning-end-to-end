package com.elearning.learning.infrastructure

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders a certificate to PDF.
 *
 * Deliberately plain: layout and branding are a product decision, and a
 * placeholder that looks finished is worse than one that obviously is not.
 * Standard-14 fonts keep the file small and need no font licensing, at the cost
 * of Latin-1 only - see [sanitise].
 */
@Component
class CertificatePdfRenderer {

    fun render(details: CertificateDetails): ByteArray {
        PDDocument().use { document ->
            // Landscape A4, the usual certificate orientation.
            val page = PDPage(PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width))
            document.addPage(page)

            PDPageContentStream(document, page).use { content ->
                val width = page.mediaBox.width

                centred(content, "CERTIFICATE OF COMPLETION", HEADING, 26f, width, 480f)
                centred(content, "This certifies that", BODY, 14f, width, 420f)
                centred(content, sanitise(details.learnerName), HEADING, 30f, width, 370f)
                centred(content, "has successfully completed", BODY, 14f, width, 320f)
                centred(content, sanitise(details.courseTitle), HEADING, 20f, width, 275f)
                centred(content, "Issued ${DATE.format(details.issuedAt.atZone(ZoneOffset.UTC))}", BODY, 12f, width, 200f)
                centred(content, "Certificate ${details.certificateNumber}", BODY, 11f, width, 160f)
                centred(content, "Verify with code: ${details.verificationCode}", BODY, 9f, width, 130f)
            }

            return ByteArrayOutputStream().use { out ->
                document.save(out)
                out.toByteArray()
            }
        }
    }

    private fun centred(
        content: PDPageContentStream,
        text: String,
        font: PDType1Font,
        size: Float,
        pageWidth: Float,
        y: Float,
    ) {
        val textWidth = font.getStringWidth(text) / 1000 * size
        content.beginText()
        content.setFont(font, size)
        content.newLineAtOffset((pageWidth - textWidth) / 2, y)
        content.showText(text)
        content.endText()
    }

    /**
     * Standard-14 fonts cannot encode characters outside WinAnsi, and PDFBox
     * throws rather than substituting. A name with an unsupported glyph must not
     * fail certificate issuance, so those characters are dropped.
     */
    private fun sanitise(text: String): String =
        text.filter { it.code in 32..255 }.ifBlank { "-" }

    private companion object {
        val HEADING = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val BODY = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    }
}

data class CertificateDetails(
    val learnerName: String,
    val courseTitle: String,
    val certificateNumber: String,
    val verificationCode: String,
    val issuedAt: Instant,
)
