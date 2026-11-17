package com.example.domain

import com.example.model.ElementRole
import com.example.model.ElementType
import com.example.model.FontPairing
import com.example.model.LetterheadElement
import com.example.model.LetterheadProject
import com.example.model.PageMargins
import com.example.model.PageSettings
import com.example.model.ShapeStyleConfig
import com.example.model.TextAlignOption
import com.example.model.TextStyleConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GrashyInitialProject {
    const val ASSET_LOGO = "asset://grashy_logo"
    const val ASSET_FOOTER_WAVE = "asset://grashy_footer_wave"

    fun nowIso(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    val FONT_PAIRINGS = listOf(
        FontPairing(
            id = "executive-sans",
            name = "Executive Modern",
            description = "Plus Jakarta Sans display paired with DM Sans body",
            displayFont = "Plus Jakarta Sans",
            bodyFont = "DM Sans"
        ),
        FontPairing(
            id = "editorial-luxury",
            name = "Hospitality Classic",
            description = "Playfair Display headings paired with Source Serif 4 prose",
            displayFont = "Playfair Display",
            bodyFont = "Source Serif 4"
        ),
        FontPairing(
            id = "clean-corporate",
            name = "Corporate Crisp",
            description = "Outfit geometric headings with DM Sans details",
            displayFont = "Outfit",
            bodyFont = "DM Sans"
        ),
        FontPairing(
            id = "warm-atelier",
            name = "Editorial Heritage",
            description = "Montserrat structured headings with Lora body text",
            displayFont = "Montserrat",
            bodyFont = "Lora"
        )
    )

    val DEFAULT_PAGE_SETTINGS = PageSettings(
        widthMm = Geometry.A4_WIDTH_MM,
        heightMm = Geometry.A4_HEIGHT_MM,
        margins = PageMargins(top = 14f, right = 15f, bottom = 22f, left = 15f),
        showMargins = true,
        showGrid = false,
        showRulers = true,
        snapToGrid = false,
        snapToGuides = true,
        gridSizeMm = 5f
    )

    fun createOriginalGrashyProject(): LetterheadProject {
        val elements = listOf(
            LetterheadElement(
                id = "grashy-logo",
                pageIndex = 0,
                type = ElementType.IMAGE,
                role = ElementRole.LOGO,
                name = "Company Logo",
                x = 24f,
                y = 13f,
                width = 48f,
                height = 32.5f,
                zIndex = 10,
                imageSrc = ASSET_LOGO,
                objectFit = "contain"
            ),
            LetterheadElement(
                id = "grashy-title",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.COMPANY_TITLE,
                name = "Company Title",
                x = 162f,
                y = 14f,
                width = 40f,
                height = 11f,
                zIndex = 20,
                content = "Grashy",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 20f,
                    fontWeight = 500,
                    lineHeight = 1.2f,
                    paragraphSpacing = 0f,
                    color = "#C45911",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-contact",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.CONTACT,
                name = "Telephone Contact",
                x = 164f,
                y = 28f,
                width = 39f,
                height = 7f,
                zIndex = 25,
                content = "Tel: 0727 4",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9f,
                    fontWeight = 400,
                    lineHeight = 1.3f,
                    paragraphSpacing = 0f,
                    color = "#111827",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-header-dots",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.SEPARATOR,
                name = "Header Dotted Line",
                x = 19f,
                y = 48f,
                width = 165f,
                height = 6f,
                zIndex = 30,
                content = ".".repeat(130),
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10f,
                    fontWeight = 400,
                    letterSpacing = 0.04f,
                    lineHeight = 1f,
                    paragraphSpacing = 0f,
                    color = "#111827",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-event-meta",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.EVENT_META,
                name = "Event Details",
                x = 20f,
                y = 56f,
                width = 90f,
                height = 22f,
                zIndex = 40,
                content = "No of guests:35\nClient:\nVenue: Karen - Mbagathi Ridge\nDate:",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10.5f,
                    fontWeight = 400,
                    lineHeight = 1.4f,
                    paragraphSpacing = 2f,
                    color = "#D66565",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-body-quote",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.BODY,
                name = "Quotation Body",
                x = 20f,
                y = 80f,
                width = 140f,
                height = 58f,
                zIndex = 50,
                content = "BREAKFAST\n\n💫Mandazi/Bread\n💫Tea,Chocolate\n\n\n\n10,000 transport inclusive\nPer day",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 11.5f,
                    fontWeight = 400,
                    lineHeight = 1.4f,
                    paragraphSpacing = 3f,
                    color = "#111827",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-closing",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.CLOSING,
                name = "Closing Salutation",
                x = 23f,
                y = 206f,
                width = 140f,
                height = 8f,
                zIndex = 60,
                content = "Thank you for your enquiry, we are delighted to be at your service.",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10.5f,
                    fontWeight = 600,
                    fontStyle = "italic",
                    lineHeight = 1.35f,
                    paragraphSpacing = 0f,
                    color = "#111827",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-payment-bank",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.PAYMENT_BANK,
                name = "Bank Payment Details",
                x = 23f,
                y = 214f,
                width = 105f,
                height = 22f,
                zIndex = 70,
                content = "Bank Name: NCBA\nBranch: Junction\nAccount Name: Grashy Finest Caterers\nAccount no:1006509947",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10f,
                    fontWeight = 400,
                    lineHeight = 1.4f,
                    paragraphSpacing = 1f,
                    color = "#111827",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-payment-line",
                pageIndex = 0,
                type = ElementType.LINE,
                role = ElementRole.PAYMENT_DIVIDER,
                name = "Payment Divider Line",
                x = 16f,
                y = 234f,
                width = 170f,
                height = 2f,
                zIndex = 75,
                shapeStyle = ShapeStyleConfig(
                    fill = "transparent",
                    stroke = "#111827",
                    strokeWidth = 1.2f,
                    borderRadius = 0f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-payment-mpesa",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.PAYMENT_MPESA,
                name = "M-Pesa Till Details",
                x = 23f,
                y = 236f,
                width = 90f,
                height = 18f,
                zIndex = 80,
                content = "        OR\nMpesa : Buy Goods Till Number\nTill no: 972914",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10f,
                    fontWeight = 400,
                    lineHeight = 1.4f,
                    paragraphSpacing = 1f,
                    color = "#111827",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-footer-wave",
                pageIndex = 0,
                type = ElementType.IMAGE,
                role = ElementRole.FOOTER_WAVE,
                name = "Footer Brand Wave",
                x = 8f,
                y = 277f,
                width = 194f,
                height = 20f,
                zIndex = 5,
                imageSrc = ASSET_FOOTER_WAVE,
                objectFit = "fill"
            )
        )

        return LetterheadProject(
            id = "grashy-caterers-letterhead",
            name = "Grashy Caterers — Original Upload",
            version = 1,
            updatedAt = nowIso(),
            pageCount = 1,
            layoutMode = "original",
            formData = FormSync.DEFAULT_FORM_DATA.copy(
                companyName = "Grashy",
                phoneNumber = "0727 4",
                pax = "35",
                clientName = "",
                venue = "Karen - Mbagathi Ridge",
                date = "",
                eventDetails = "BREAKFAST\n\n💫Mandazi/Bread\n💫Tea,Chocolate\n\n10,000 transport inclusive\nPer day",
                transportCost = "Inclusive",
                subtotalCost = "",
                vatAmount = "",
                totalAmount = "10,000/= Per day",
                depositAmount = "",
                balance = ""
            ),
            pageSettings = DEFAULT_PAGE_SETTINGS,
            elements = elements
        )
    }

    fun createRedesignedGrashyProject(): LetterheadProject {
        val margins = DEFAULT_PAGE_SETTINGS.margins
        val widthMm = DEFAULT_PAGE_SETTINGS.widthMm
        val heightMm = DEFAULT_PAGE_SETTINGS.heightMm
        val contentWidth = widthMm - margins.left - margins.right
        val leftX = margins.left
        val form = FormSync.DEFAULT_FORM_DATA

        val elements = listOf(
            LetterheadElement(
                id = "grashy-logo",
                pageIndex = 0,
                type = ElementType.IMAGE,
                role = ElementRole.LOGO,
                name = "Company Logo",
                x = leftX,
                y = 11f,
                width = 42f,
                height = 28.5f,
                zIndex = 10,
                imageSrc = ASSET_LOGO,
                objectFit = "contain"
            ),
            LetterheadElement(
                id = "grashy-title",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.COMPANY_TITLE,
                name = "Company Name",
                x = leftX + 55f,
                y = 10f,
                width = contentWidth - 55f,
                height = 9f,
                zIndex = 20,
                content = form.companyName,
                textStyle = TextStyleConfig(
                    fontFamily = "Plus Jakarta Sans",
                    fontSize = 21f,
                    fontWeight = 700,
                    letterSpacing = -0.01f,
                    lineHeight = 1.15f,
                    paragraphSpacing = 0f,
                    color = "#C45911",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-subtitle",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.COMPANY_SUBTITLE,
                name = "Company Tagline",
                x = leftX + 55f,
                y = 19.5f,
                width = contentWidth - 55f,
                height = 4.5f,
                zIndex = 22,
                content = "FINEST EVENT & CORPORATE CATERING",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 7.5f,
                    fontWeight = 600,
                    letterSpacing = 0.14f,
                    lineHeight = 1.2f,
                    paragraphSpacing = 0f,
                    color = "#64748B",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-contact",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.CONTACT,
                name = "Sender Details",
                x = leftX + 55f,
                y = 24.5f,
                width = contentWidth - 55f,
                height = 17.5f,
                zIndex = 25,
                content = FormSync.buildSenderContactText(form),
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 8.5f,
                    fontWeight = 500,
                    letterSpacing = 0.01f,
                    lineHeight = 1.38f,
                    paragraphSpacing = 0f,
                    color = "#334155",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-header-dots",
                pageIndex = 0,
                type = ElementType.LINE,
                role = ElementRole.SEPARATOR,
                name = "Header Accent Rule",
                x = leftX,
                y = 44f,
                width = contentWidth,
                height = 2f,
                zIndex = 30,
                shapeStyle = ShapeStyleConfig(
                    fill = "transparent",
                    stroke = "#C45911",
                    strokeWidth = 1.5f,
                    borderRadius = 0f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-event-card",
                pageIndex = 0,
                type = ElementType.SHAPE,
                role = ElementRole.CUSTOM,
                name = "Event Details Card",
                x = leftX,
                y = 48f,
                width = contentWidth,
                height = 22f,
                zIndex = 35,
                shapeStyle = ShapeStyleConfig(
                    fill = "#FFF7ED",
                    stroke = "#FDBA74",
                    strokeWidth = 0.6f,
                    borderRadius = 2f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-event-date",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.EVENT_DATE,
                name = "Event Date",
                x = leftX + 3f,
                y = 50f,
                width = 82f,
                height = 5.5f,
                zIndex = 41,
                content = "Date: ${form.date}",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 600,
                    lineHeight = 1.3f,
                    paragraphSpacing = 0f,
                    color = "#9A3412",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-event-pax",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.EVENT_PAX,
                name = "PAX Count",
                x = leftX + 100f,
                y = 50f,
                width = contentWidth - 103f,
                height = 5.5f,
                zIndex = 42,
                content = "PAX: ${form.pax} Guests",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 600,
                    lineHeight = 1.3f,
                    paragraphSpacing = 0f,
                    color = "#9A3412",
                    textAlign = TextAlignOption.RIGHT
                )
            ),
            LetterheadElement(
                id = "grashy-event-client",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.CLIENT_NAME,
                name = "Client Name",
                x = leftX + 3f,
                y = 56.5f,
                width = contentWidth - 6f,
                height = 6f,
                zIndex = 43,
                content = "Client: ${form.clientName}",
                textStyle = TextStyleConfig(
                    fontFamily = "Plus Jakarta Sans",
                    fontSize = 10.5f,
                    fontWeight = 700,
                    lineHeight = 1.3f,
                    paragraphSpacing = 0f,
                    color = "#7C2D12",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-event-venue",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.EVENT_VENUE,
                name = "Event Venue",
                x = leftX + 3f,
                y = 63f,
                width = contentWidth - 6f,
                height = 5.5f,
                zIndex = 44,
                content = "Venue: ${form.venue}",
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 500,
                    lineHeight = 1.3f,
                    paragraphSpacing = 0f,
                    color = "#9A3412",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-body-quote",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.BODY,
                name = "Event & Menu Details",
                x = leftX,
                y = 74f,
                width = contentWidth,
                height = 79f,
                zIndex = 50,
                content = form.eventDetails,
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 10f,
                    fontWeight = 400,
                    lineHeight = 1.4f,
                    paragraphSpacing = 1.5f,
                    color = "#1E293B",
                    textAlign = TextAlignOption.LEFT,
                    paddingTop = 1.5f,
                    paddingRight = 2f,
                    paddingBottom = 1.5f,
                    paddingLeft = 2f
                )
            ),
            LetterheadElement(
                id = "grashy-cost-summary",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.COST_SUMMARY,
                name = "Payment Summary",
                x = leftX,
                y = 156f,
                width = contentWidth,
                height = 41f,
                zIndex = 55,
                content = FormSync.buildPaymentSummaryText(form),
                textStyle = TextStyleConfig(
                    fontFamily = "Plus Jakarta Sans",
                    fontSize = 9f,
                    fontWeight = 600,
                    letterSpacing = 0.01f,
                    lineHeight = 1.4f,
                    paragraphSpacing = 1f,
                    color = "#0F172A",
                    textAlign = TextAlignOption.LEFT,
                    paddingTop = 2.5f,
                    paddingRight = 3f,
                    paddingBottom = 2.5f,
                    paddingLeft = 3f
                ),
                shapeStyle = ShapeStyleConfig(
                    fill = "#F8FAFC",
                    stroke = "#CBD5E1",
                    strokeWidth = 0.75f,
                    borderRadius = 2f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-closing",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.CLOSING,
                name = "Payment Instructions & Thank You",
                x = leftX,
                y = 200f,
                width = contentWidth,
                height = 11f,
                zIndex = 60,
                content = form.paymentInstructions,
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 500,
                    fontStyle = "italic",
                    lineHeight = 1.42f,
                    paragraphSpacing = 1f,
                    color = "#334155",
                    textAlign = TextAlignOption.LEFT
                )
            ),
            LetterheadElement(
                id = "grashy-payment-line",
                pageIndex = 0,
                type = ElementType.LINE,
                role = ElementRole.PAYMENT_DIVIDER,
                name = "Footer Divider Line",
                x = leftX,
                y = 213f,
                width = contentWidth,
                height = 2f,
                zIndex = 65,
                shapeStyle = ShapeStyleConfig(
                    fill = "transparent",
                    stroke = "#CBD5E1",
                    strokeWidth = 0.75f,
                    borderRadius = 0f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-payment-bank",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.PAYMENT_BANK,
                name = "Bank Details",
                x = leftX,
                y = 217f,
                width = (contentWidth - 8f) * 0.56f,
                height = 30f,
                zIndex = 70,
                content = form.bankDetails,
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 400,
                    lineHeight = 1.48f,
                    paragraphSpacing = 1f,
                    color = "#1E293B",
                    textAlign = TextAlignOption.LEFT,
                    paddingTop = 2.5f,
                    paddingRight = 3f,
                    paddingBottom = 2.5f,
                    paddingLeft = 3f
                ),
                shapeStyle = ShapeStyleConfig(
                    fill = "#F8FAFC",
                    stroke = "#E2E8F0",
                    strokeWidth = 0.75f,
                    borderRadius = 2f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-payment-mpesa",
                pageIndex = 0,
                type = ElementType.TEXT,
                role = ElementRole.PAYMENT_MPESA,
                name = "M-Pesa Details",
                x = leftX + (contentWidth - 8f) * 0.56f + 8f,
                y = 217f,
                width = (contentWidth - 8f) * 0.44f,
                height = 30f,
                zIndex = 80,
                content = form.mpesaDetails,
                textStyle = TextStyleConfig(
                    fontFamily = "DM Sans",
                    fontSize = 9.5f,
                    fontWeight = 400,
                    lineHeight = 1.48f,
                    paragraphSpacing = 1f,
                    color = "#1E293B",
                    textAlign = TextAlignOption.LEFT,
                    paddingTop = 2.5f,
                    paddingRight = 3f,
                    paddingBottom = 2.5f,
                    paddingLeft = 3f
                ),
                shapeStyle = ShapeStyleConfig(
                    fill = "#F8FAFC",
                    stroke = "#E2E8F0",
                    strokeWidth = 0.75f,
                    borderRadius = 2f,
                    lineStyle = "solid"
                )
            ),
            LetterheadElement(
                id = "grashy-footer-wave",
                pageIndex = 0,
                type = ElementType.IMAGE,
                role = ElementRole.FOOTER_WAVE,
                name = "Footer Brand Wave",
                x = 0f,
                y = heightMm - 20f,
                width = widthMm,
                height = 20f,
                zIndex = 5,
                imageSrc = ASSET_FOOTER_WAVE,
                objectFit = "fill"
            )
        )

        return LetterheadProject(
            id = "grashy-caterers-letterhead-v3",
            name = "Grashy Caterers — Official Letterhead",
            version = 3,
            updatedAt = nowIso(),
            pageCount = 1,
            layoutMode = "refined",
            formData = form,
            pageSettings = DEFAULT_PAGE_SETTINGS,
            elements = elements
        )
    }

    fun applyCompleteRedesign(
        project: LetterheadProject,
        pairing: FontPairing = FONT_PAIRINGS[0]
    ): LetterheadProject {
        val margins = project.pageSettings.margins
        val widthMm = project.pageSettings.widthMm
        val heightMm = project.pageSettings.heightMm
        val contentWidth = widthMm - margins.left - margins.right
        val leftX = margins.left

        val updatedElements = project.elements.map { el ->
            when (el.role) {
                ElementRole.LOGO -> el.copy(
                    x = leftX, y = 11f, width = 42f, height = 28.5f, rotation = 0f
                )
                ElementRole.COMPANY_TITLE -> {
                    val raw = (el.content ?: "").trim()
                    val expanded = if (raw.equals("grashy", ignoreCase = true)) "Grashy Caterers" else raw.ifEmpty { "Grashy Caterers" }
                    el.copy(
                        x = leftX + 52f,
                        y = 10.5f,
                        width = contentWidth - 52f,
                        height = 9f,
                        content = expanded,
                        textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                            fontFamily = pairing.displayFont,
                            fontSize = 21f,
                            fontWeight = 700,
                            letterSpacing = -0.01f,
                            color = "#C45911",
                            textAlign = TextAlignOption.RIGHT
                        )
                    )
                }
                ElementRole.CONTACT -> {
                    val cur = (el.content ?: "").trim()
                    val cleaned = if (cur == "Tel: 0727 4") FormSync.buildSenderContactText(project.formData) else cur
                    el.copy(
                        x = leftX + 55f,
                        y = 25f,
                        width = contentWidth - 55f,
                        height = 17f,
                        content = cleaned,
                        textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                            fontFamily = pairing.bodyFont,
                            fontSize = 8.5f,
                            fontWeight = 500,
                            lineHeight = 1.38f,
                            color = "#334155",
                            textAlign = TextAlignOption.RIGHT
                        )
                    )
                }
                ElementRole.SEPARATOR -> el.copy(
                    type = ElementType.LINE,
                    name = "Header Accent Rule",
                    x = leftX,
                    y = 44f,
                    width = contentWidth,
                    height = 2f,
                    content = null,
                    textStyle = null,
                    shapeStyle = ShapeStyleConfig(
                        fill = "transparent",
                        stroke = "#C45911",
                        strokeWidth = 1.5f,
                        borderRadius = 0f,
                        lineStyle = "solid"
                    )
                )
                ElementRole.EVENT_META -> {
                    val cleanedMeta = (el.content ?: "")
                        .replace(Regex(":[ \\t]*"), ": ")
                        .replace(Regex("[ \\t]+\\n"), "\n")
                        .replace(Regex("^No of guests", RegexOption.IGNORE_CASE), "No. of Guests")
                    el.copy(
                        x = leftX,
                        y = 48f,
                        width = contentWidth,
                        height = 22f,
                        content = cleanedMeta,
                        textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                            fontFamily = pairing.bodyFont,
                            fontSize = 10f,
                            fontWeight = 500,
                            lineHeight = 1.45f,
                            paragraphSpacing = 2f,
                            color = "#9A3412",
                            textAlign = TextAlignOption.LEFT,
                            paddingTop = 2f,
                            paddingRight = 3f,
                            paddingBottom = 2f,
                            paddingLeft = 3f
                        ),
                        shapeStyle = ShapeStyleConfig(
                            fill = "#FFF7ED",
                            stroke = "#FDBA74",
                            strokeWidth = 0.6f,
                            borderRadius = 2f,
                            lineStyle = "solid"
                        )
                    )
                }
                ElementRole.BODY -> {
                    val cleanedBody = (el.content ?: "")
                        .replace(Regex("💫\\s*"), "•  ")
                        .replace("Tea,Chocolate", "Tea & Hot Chocolate")
                        .replace("Mandazi/Bread", "Mandazi / Fresh Bread")
                        .replace(Regex("\\n{3,}"), "\n\n")
                    el.copy(
                        x = leftX,
                        y = 74f,
                        width = contentWidth,
                        height = 84f,
                        content = cleanedBody,
                        textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                            fontFamily = pairing.bodyFont,
                            fontSize = 10f,
                            fontWeight = 400,
                            lineHeight = 1.42f,
                            paragraphSpacing = 2f,
                            color = "#1E293B",
                            textAlign = TextAlignOption.LEFT,
                            paddingTop = 1.5f,
                            paddingRight = 2f,
                            paddingBottom = 1.5f,
                            paddingLeft = 2f
                        )
                    )
                }
                ElementRole.CLOSING -> el.copy(
                    x = leftX,
                    y = 196f,
                    width = contentWidth,
                    height = 15f,
                    textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                        fontFamily = pairing.displayFont,
                        fontSize = 9.5f,
                        fontWeight = 500,
                        fontStyle = "italic",
                        color = "#334155",
                        textAlign = TextAlignOption.LEFT
                    )
                )
                ElementRole.PAYMENT_BANK -> el.copy(
                    x = leftX,
                    y = 217f,
                    width = (contentWidth - 8f) * 0.56f,
                    height = 30f,
                    content = (el.content ?: "").replace(Regex("Account no:", RegexOption.IGNORE_CASE), "Account No: "),
                    textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                        fontFamily = pairing.bodyFont,
                        fontSize = 9.5f,
                        fontWeight = 400,
                        lineHeight = 1.48f,
                        color = "#1E293B",
                        textAlign = TextAlignOption.LEFT,
                        paddingTop = 2.5f,
                        paddingRight = 3f,
                        paddingBottom = 2.5f,
                        paddingLeft = 3f
                    ),
                    shapeStyle = ShapeStyleConfig(
                        fill = "#F8FAFC",
                        stroke = "#E2E8F0",
                        strokeWidth = 0.75f,
                        borderRadius = 2f,
                        lineStyle = "solid"
                    )
                )
                ElementRole.PAYMENT_DIVIDER -> el.copy(
                    x = leftX,
                    y = 213f,
                    width = contentWidth,
                    height = 2f,
                    shapeStyle = ShapeStyleConfig(
                        fill = "transparent",
                        stroke = "#CBD5E1",
                        strokeWidth = 0.75f,
                        borderRadius = 0f,
                        lineStyle = "solid"
                    )
                )
                ElementRole.PAYMENT_MPESA -> {
                    val cleanedMpesa = (el.content ?: "")
                        .replace(Regex("^\\s*OR\\s*\\n?", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("Mpesa\\s*:\\s*", RegexOption.IGNORE_CASE), "M-Pesa: ")
                    el.copy(
                        x = leftX + (contentWidth - 8f) * 0.56f + 8f,
                        y = 217f,
                        width = (contentWidth - 8f) * 0.44f,
                        height = 30f,
                        content = cleanedMpesa,
                        textStyle = (el.textStyle ?: TextStyleConfig()).copy(
                            fontFamily = pairing.bodyFont,
                            fontSize = 9.5f,
                            fontWeight = 400,
                            lineHeight = 1.48f,
                            color = "#1E293B",
                            textAlign = TextAlignOption.LEFT,
                            paddingTop = 2.5f,
                            paddingRight = 3f,
                            paddingBottom = 2.5f,
                            paddingLeft = 3f
                        ),
                        shapeStyle = ShapeStyleConfig(
                            fill = "#F8FAFC",
                            stroke = "#E2E8F0",
                            strokeWidth = 0.75f,
                            borderRadius = 2f,
                            lineStyle = "solid"
                        )
                    )
                }
                ElementRole.FOOTER_WAVE -> el.copy(
                    x = 0f,
                    y = heightMm - 20f,
                    width = widthMm,
                    height = 20f
                )
                else -> el
            }
        }.toMutableList()

        if (updatedElements.none { it.role == ElementRole.COMPANY_SUBTITLE }) {
            updatedElements.add(
                LetterheadElement(
                    id = "grashy-subtitle",
                    pageIndex = 0,
                    type = ElementType.TEXT,
                    role = ElementRole.COMPANY_SUBTITLE,
                    name = "Company Tagline",
                    x = leftX + 60f,
                    y = 19.5f,
                    width = contentWidth - 60f,
                    height = 4.5f,
                    zIndex = 22,
                    content = "FINEST EVENT & CORPORATE CATERING",
                    textStyle = TextStyleConfig(
                        fontFamily = pairing.bodyFont,
                        fontSize = 7.5f,
                        fontWeight = 600,
                        letterSpacing = 0.14f,
                        lineHeight = 1.2f,
                        color = "#64748B",
                        textAlign = TextAlignOption.RIGHT
                    )
                )
            )
        }

        val currentForm = project.formData
        val nextForm = currentForm.copy(
            companyName = if (currentForm.companyName.trim().equals("grashy", ignoreCase = true)) {
                "Grashy Caterers"
            } else {
                currentForm.companyName
            },
            phoneNumber = if (currentForm.phoneNumber.trim() == "0727 4") {
                FormSync.DEFAULT_FORM_DATA.phoneNumber
            } else {
                currentForm.phoneNumber
            }
        )

        return project.copy(
            layoutMode = "refined",
            updatedAt = nowIso(),
            formData = nextForm,
            elements = updatedElements
        )
    }
}
