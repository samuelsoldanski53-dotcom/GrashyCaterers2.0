package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ElementType {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("line") LINE,
    @SerialName("shape") SHAPE
}

@Serializable
enum class ElementRole {
    @SerialName("logo") LOGO,
    @SerialName("company-title") COMPANY_TITLE,
    @SerialName("company-subtitle") COMPANY_SUBTITLE,
    @SerialName("contact") CONTACT,
    @SerialName("separator") SEPARATOR,
    @SerialName("event-meta") EVENT_META,
    @SerialName("event-date") EVENT_DATE,
    @SerialName("event-pax") EVENT_PAX,
    @SerialName("client-name") CLIENT_NAME,
    @SerialName("event-venue") EVENT_VENUE,
    @SerialName("body") BODY,
    @SerialName("cost-summary") COST_SUMMARY,
    @SerialName("closing") CLOSING,
    @SerialName("payment-bank") PAYMENT_BANK,
    @SerialName("payment-mpesa") PAYMENT_MPESA,
    @SerialName("payment-divider") PAYMENT_DIVIDER,
    @SerialName("footer-wave") FOOTER_WAVE,
    @SerialName("custom") CUSTOM
}

enum class EditorMode {
    FORM,
    VISUAL
}

@Serializable
data class LetterheadFormData(
    // A. Document Details
    val date: String = "",
    val clientName: String = "",
    val pax: String = "",
    val venue: String = "",
    val eventDetails: String = "",
    // B. Payment Information
    val transportCost: String = "",
    val subtotalCost: String = "",
    val vatAmount: String = "",
    val totalAmount: String = "",
    val depositAmount: String = "",
    val balance: String = "",
    val paymentMethod: String = "",
    val bankDetails: String = "",
    val mpesaDetails: String = "",
    val paymentInstructions: String = "",
    // C. Sender Details
    val companyName: String = "",
    val contactPerson: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val physicalAddress: String = "",
    val postalAddress: String = ""
)

@Serializable
enum class TextAlignOption {
    @SerialName("left") LEFT,
    @SerialName("center") CENTER,
    @SerialName("right") RIGHT,
    @SerialName("justify") JUSTIFY
}

@Serializable
data class TextStyleConfig(
    val fontFamily: String = "DM Sans",
    val fontSize: Float = 10f, // points (pt)
    val fontWeight: Int = 400, // 400 | 500 | 600 | 700
    val fontStyle: String = "normal", // "normal" | "italic"
    val letterSpacing: Float = 0f, // em
    val lineHeight: Float = 1.4f, // unitless multiplier
    val paragraphSpacing: Float = 1.5f, // pt
    val color: String = "#1E293B",
    val textAlign: TextAlignOption = TextAlignOption.LEFT,
    val paddingTop: Float = 0f, // mm
    val paddingRight: Float = 0f, // mm
    val paddingBottom: Float = 0f, // mm
    val paddingLeft: Float = 0f // mm
)

@Serializable
data class ShapeStyleConfig(
    val fill: String = "transparent",
    val stroke: String = "#C45911",
    val strokeWidth: Float = 1f, // pt
    val borderRadius: Float = 0f, // mm
    val lineStyle: String = "solid" // "solid" | "dashed" | "dotted"
)

@Serializable
data class LetterheadElement(
    val id: String,
    val pageIndex: Int = 0,
    val type: ElementType,
    val role: ElementRole = ElementRole.CUSTOM,
    val name: String,
    val x: Float, // mm from left of A4 page
    val y: Float, // mm from top of A4 page
    val width: Float, // mm
    val height: Float, // mm
    val rotation: Float = 0f, // degrees
    val opacity: Float = 1f, // 0..1
    val zIndex: Int = 10,
    val locked: Boolean = false,
    val visible: Boolean = true,
    val content: String? = null,
    val textStyle: TextStyleConfig? = null,
    val shapeStyle: ShapeStyleConfig? = null,
    val imageSrc: String? = null,
    val objectFit: String? = "contain" // "contain" | "cover" | "fill"
)

@Serializable
data class PageMargins(
    val top: Float = 14f,
    val right: Float = 15f,
    val bottom: Float = 22f,
    val left: Float = 15f
)

@Serializable
data class PageSettings(
    val widthMm: Float = 210f,
    val heightMm: Float = 297f,
    val margins: PageMargins = PageMargins(),
    val showMargins: Boolean = true,
    val showGrid: Boolean = false,
    val showRulers: Boolean = true,
    val snapToGrid: Boolean = false,
    val snapToGuides: Boolean = true,
    val gridSizeMm: Float = 5f
)

@Serializable
data class LetterheadProject(
    val id: String,
    val name: String,
    val version: Int = 1,
    val updatedAt: String = "",
    val pageCount: Int = 1,
    val layoutMode: String = "refined", // "original" | "refined" | "custom"
    val formData: LetterheadFormData = LetterheadFormData(),
    val pageSettings: PageSettings = PageSettings(),
    val elements: List<LetterheadElement> = emptyList()
)

enum class AuditCategory(val label: String) {
    LOGO_HEADER("Logo & Header"),
    TYPOGRAPHY("Typography"),
    SPACING_BALANCE("Spacing & Balance"),
    CONTACT_FOOTER("Contact & Footer"),
    MARGINS_ALIGNMENT("Margins & Alignment")
}

enum class AuditSeverity {
    HIGH,
    MEDIUM,
    LOW
}

data class DesignSuggestion(
    val id: String,
    val category: AuditCategory,
    val severity: AuditSeverity,
    val title: String,
    val explanation: String,
    val affectedElementIds: List<String>,
    val fixLabel: String,
    val canAutoFix: Boolean,
    val applyFix: ((LetterheadProject) -> LetterheadProject)? = null
)

data class SnapGuide(
    val axis: String, // "x" | "y"
    val positionMm: Float,
    val kind: String // "margin" | "page-center" | "element"
)

data class ParsedQuotation(
    val date: String = "",
    val pax: String = "",
    val client: String = "",
    val venue: String = "",
    val menuItems: List<String> = emptyList(),
    val ratePerPlate: String = "",
    val transport: String = "",
    val subtotal: String = "",
    val vat: String = "",
    val totalCost: String = "",
    val extraNotes: List<String> = emptyList()
)

data class FontPairing(
    val id: String,
    val name: String,
    val description: String,
    val displayFont: String,
    val bodyFont: String
)
