package com.example.domain

import com.example.model.AuditCategory
import com.example.model.AuditSeverity
import com.example.model.DesignSuggestion
import com.example.model.ElementRole
import com.example.model.ElementType
import com.example.model.LetterheadElement
import com.example.model.LetterheadProject
import com.example.model.ShapeStyleConfig
import com.example.model.TextAlignOption
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object DesignAnalyzer {
    data class LayoutHealthSummary(
        val highCount: Int,
        val mediumCount: Int,
        val lowCount: Int,
        val statusText: String
    )

    fun analyzeLetterheadDesign(project: LetterheadProject): List<DesignSuggestion> {
        val suggestions = mutableListOf<DesignSuggestion>()
        val pageSettings = project.pageSettings
        val margins = pageSettings.margins
        val widthMm = pageSettings.widthMm
        val heightMm = pageSettings.heightMm
        val contentWidth = widthMm - margins.left - margins.right
        val pageZero = project.elements.filter { it.pageIndex == 0 && it.visible }

        // 1. Check Company Title completeness & hierarchy
        val titleEl = pageZero.find { it.role == ElementRole.COMPANY_TITLE }
        val logoEl = pageZero.find { it.role == ElementRole.LOGO }
        if (titleEl != null) {
            val text = (titleEl.content ?: "").trim()
            val isShortBrand = text.equals("grashy", ignoreCase = true)
            val fontSize = titleEl.textStyle?.fontSize ?: 12f
            val isNearRightEdge = titleEl.x + titleEl.width > widthMm - margins.right + 1f
            if (isShortBrand || isNearRightEdge || fontSize < 18f) {
                suggestions.add(
                    DesignSuggestion(
                        id = "audit-company-title",
                        category = AuditCategory.LOGO_HEADER,
                        severity = AuditSeverity.HIGH,
                        title = if (isShortBrand) {
                            "Company title \"Grashy\" is truncated and crowded against the right edge"
                        } else {
                            "Company title sits outside safe right margin or lacks visual hierarchy"
                        },
                        explanation = "Expanding to \"Grashy Caterers\", aligning flush with the 15mm right margin, and balancing scale with the logo creates an authoritative brand lockup.",
                        affectedElementIds = listOf(titleEl.id),
                        fixLabel = "Align & Elevate Title",
                        canAutoFix = true,
                        applyFix = { p ->
                            p.copy(
                                elements = p.elements.map { el ->
                                    if (el.id == titleEl.id) {
                                        el.copy(
                                            x = margins.left + 52f,
                                            y = 13f,
                                            width = contentWidth - 52f,
                                            height = 11f,
                                            content = if ((el.content ?: "").trim().equals("grashy", ignoreCase = true)) "Grashy Caterers" else el.content,
                                            textStyle = el.textStyle?.copy(
                                                fontSize = 22f,
                                                fontWeight = 700,
                                                textAlign = TextAlignOption.RIGHT
                                            )
                                        )
                                    } else el
                                }
                            )
                        }
                    )
                )
            }
        }

        // 2. Check Contact Information clipping
        val contactEl = pageZero.find { it.role == ElementRole.CONTACT }
        if (contactEl != null) {
            val txt = (contactEl.content ?: "").trim()
            val looksTruncated = Regex("Tel:\\s*0\\d{3}\\s*\\d?$", RegexOption.IGNORE_CASE).containsMatchIn(txt)
            val clipsMargin = contactEl.x + contactEl.width > widthMm - margins.right + 1f
            if (looksTruncated || clipsMargin) {
                suggestions.add(
                    DesignSuggestion(
                        id = "audit-contact-clipped",
                        category = AuditCategory.CONTACT_FOOTER,
                        severity = AuditSeverity.HIGH,
                        title = if (looksTruncated) {
                            "Telephone number (\"Tel: 0727 4\") appears cut off at the right margin"
                        } else {
                            "Contact block extends past safe printable right margin"
                        },
                        explanation = "Widen the contact container inside the 15mm right margin and add a structured phone/email template so numbers never clip.",
                        affectedElementIds = listOf(contactEl.id),
                        fixLabel = "Fix Contact Box Bounds",
                        canAutoFix = true,
                        applyFix = { p ->
                            p.copy(
                                elements = p.elements.map { el ->
                                    if (el.id == contactEl.id) {
                                        el.copy(
                                            x = margins.left + 60f,
                                            y = 25f,
                                            width = contentWidth - 60f,
                                            height = 14f,
                                            content = if ((el.content ?: "").trim() == "Tel: 0727 4") {
                                                "Tel: 0727 4XX XXX  |  Karen, Nairobi\nEmail: info@grashycaterers.co.ke"
                                            } else el.content,
                                            textStyle = el.textStyle?.copy(
                                                fontSize = 9.5f,
                                                fontWeight = 500,
                                                lineHeight = 1.45f,
                                                color = "#334155",
                                                textAlign = TextAlignOption.RIGHT
                                            )
                                        )
                                    } else el
                                }
                            )
                        }
                    )
                )
            }
        }

        // 3. Check Header Separator (dotted ellipsis string vs clean vector line)
        val sepEl = pageZero.find { it.role == ElementRole.SEPARATOR }
        if (sepEl != null && sepEl.type == ElementType.TEXT && Regex("\\.{6,}|…{3,}").containsMatchIn(sepEl.content ?: "")) {
            suggestions.add(
                DesignSuggestion(
                    id = "audit-dotted-separator",
                    category = AuditCategory.LOGO_HEADER,
                    severity = AuditSeverity.HIGH,
                    title = "Header divider uses typed period characters instead of a crisp rule",
                    explanation = "Typed dots stop awkwardly short of the right margin and render unevenly across exports. Replace with a full-width 1.5pt brand-orange hairline rule.",
                    affectedElementIds = listOf(sepEl.id),
                    fixLabel = "Convert to Crisp Rule",
                    canAutoFix = true,
                    applyFix = { p ->
                        p.copy(
                            elements = p.elements.map { el ->
                                if (el.id == sepEl.id) {
                                    el.copy(
                                        type = ElementType.LINE,
                                        name = "Header Accent Rule",
                                        x = margins.left,
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
                                } else el
                            }
                        )
                    }
                )
            )
        }

        // 4. Check Logo alignment & proportion
        if (logoEl != null && (logoEl.x > margins.left + 2f || logoEl.width > 46f)) {
            suggestions.add(
                DesignSuggestion(
                    id = "audit-logo-margin",
                    category = AuditCategory.MARGINS_ALIGNMENT,
                    severity = AuditSeverity.MEDIUM,
                    title = "Logo sits at x=${logoEl.x.roundToInt()}mm instead of aligning with left document margin",
                    explanation = "Aligning the Grashy Caterers emblem flush with the 15mm left margin creates an immediate vertical grid line down the page.",
                    affectedElementIds = listOf(logoEl.id),
                    fixLabel = "Snap Logo to Margin",
                    canAutoFix = true,
                    applyFix = { p ->
                        p.copy(
                            elements = p.elements.map { el ->
                                if (el.id == logoEl.id) el.copy(x = margins.left, y = 12f, width = 42f, height = 28.5f)
                                else el
                            }
                        )
                    }
                )
            )
        }

        // 5. Check Typography & Text Spacing quirks
        val metaEl = pageZero.find { it.role == ElementRole.EVENT_META }
        val bodyEl = pageZero.find { it.role == ElementRole.BODY }
        val textIssueIds = mutableListOf<String>()
        if (metaEl != null && Regex(":[A-Za-z0-9]").containsMatchIn(metaEl.content ?: "")) {
            textIssueIds.add(metaEl.id)
        }
        if (bodyEl != null && Regex("Tea,Chocolate|💫|\\n{3,}").containsMatchIn(bodyEl.content ?: "")) {
            textIssueIds.add(bodyEl.id)
        }
        if (textIssueIds.isNotEmpty()) {
            suggestions.add(
                DesignSuggestion(
                    id = "audit-typography-cleanup",
                    category = AuditCategory.TYPOGRAPHY,
                    severity = AuditSeverity.MEDIUM,
                    title = "Inconsistent spacing around colons, commas, and bullet symbols",
                    explanation = "Detected tight punctuation, raw emoji bullets (💫), and excess blank lines. Standardizing spacing and bullets improves readability.",
                    affectedElementIds = textIssueIds,
                    fixLabel = "Clean Punctuation & Bullets",
                    canAutoFix = true,
                    applyFix = { p ->
                        p.copy(
                            elements = p.elements.map { el ->
                                when (el.id) {
                                    metaEl?.id -> el.copy(
                                        x = margins.left,
                                        width = contentWidth,
                                        content = (el.content ?: "")
                                            .replace(Regex(":[ \\t]*"), ": ")
                                            .replace(Regex("[ \\t]+\\n"), "\n")
                                            .replace(Regex("^No of guests", RegexOption.IGNORE_CASE), "No. of Guests"),
                                        textStyle = el.textStyle?.copy(lineHeight = 1.55f, color = "#9A3412")
                                    )
                                    bodyEl?.id -> el.copy(
                                        x = margins.left,
                                        width = contentWidth,
                                        content = (el.content ?: "")
                                            .replace(Regex("💫\\s*"), "•  ")
                                            .replace("Tea,Chocolate", "Tea & Hot Chocolate")
                                            .replace("Mandazi/Bread", "Mandazi / Fresh Bread")
                                            .replace(Regex("\\n{3,}"), "\n\n"),
                                        textStyle = el.textStyle?.copy(lineHeight = 1.55f, color = "#1E293B")
                                    )
                                    else -> el
                                }
                            }
                        )
                    }
                )
            )
        }

        // 6. Check Payment Footer composition
        val bankEl = pageZero.find { it.role == ElementRole.PAYMENT_BANK }
        val mpesaEl = pageZero.find { it.role == ElementRole.PAYMENT_MPESA }
        val payDivEl = pageZero.find { it.role == ElementRole.PAYMENT_DIVIDER }
        if (bankEl != null && mpesaEl != null && abs(bankEl.x - mpesaEl.x) < 10f) {
            suggestions.add(
                DesignSuggestion(
                    id = "audit-payment-columns",
                    category = AuditCategory.CONTACT_FOOTER,
                    severity = AuditSeverity.HIGH,
                    title = "Payment instructions are stacked unevenly with a hard line cutting across",
                    explanation = "Placing the NCBA bank block and M-Pesa Till block side-by-side in a balanced 2-column payment footer recovers ~25mm of breathing room.",
                    affectedElementIds = listOfNotNull(bankEl.id, mpesaEl.id, payDivEl?.id),
                    fixLabel = "Arrange 2-Column Footer",
                    canAutoFix = true,
                    applyFix = { p ->
                        val w = p.pageSettings.widthMm - p.pageSettings.margins.left - p.pageSettings.margins.right
                        val lx = p.pageSettings.margins.left
                        p.copy(
                            elements = p.elements.map { el ->
                                when (el.id) {
                                    bankEl.id -> el.copy(
                                        x = lx,
                                        y = 226f,
                                        width = (w - 8f) * 0.56f,
                                        height = 28f,
                                        content = (el.content ?: "").replace(Regex("Account no:", RegexOption.IGNORE_CASE), "Account No: "),
                                        textStyle = el.textStyle?.copy(
                                            fontSize = 9.5f, lineHeight = 1.5f,
                                            paddingTop = 2.5f, paddingRight = 3f, paddingBottom = 2.5f, paddingLeft = 3f
                                        ),
                                        shapeStyle = ShapeStyleConfig(
                                            fill = "#F8FAFC", stroke = "#E2E8F0", strokeWidth = 0.75f, borderRadius = 2f, lineStyle = "solid"
                                        )
                                    )
                                    mpesaEl.id -> el.copy(
                                        x = lx + (w - 8f) * 0.56f + 8f,
                                        y = 226f,
                                        width = (w - 8f) * 0.44f,
                                        height = 28f,
                                        content = (el.content ?: "").replace(Regex("^\\s*OR\\s*\\n?", RegexOption.IGNORE_CASE), "").replace(Regex("Mpesa\\s*:\\s*", RegexOption.IGNORE_CASE), "M-Pesa: "),
                                        textStyle = el.textStyle?.copy(
                                            fontSize = 9.5f, lineHeight = 1.5f,
                                            paddingTop = 2.5f, paddingRight = 3f, paddingBottom = 2.5f, paddingLeft = 3f
                                        ),
                                        shapeStyle = ShapeStyleConfig(
                                            fill = "#F8FAFC", stroke = "#E2E8F0", strokeWidth = 0.75f, borderRadius = 2f, lineStyle = "solid"
                                        )
                                    )
                                    payDivEl?.id -> el.copy(
                                        x = lx,
                                        y = 222f,
                                        width = w,
                                        shapeStyle = ShapeStyleConfig(
                                            fill = "transparent", stroke = "#CBD5E1", strokeWidth = 0.75f, borderRadius = 0f, lineStyle = "solid"
                                        )
                                    )
                                    else -> el
                                }
                            }
                        )
                    }
                )
            )
        }

        // 7. Check Footer Wave edge-to-edge bleed
        val waveEl = pageZero.find { it.role == ElementRole.FOOTER_WAVE }
        if (waveEl != null && (waveEl.x > 1f || waveEl.width < widthMm - 2f)) {
            suggestions.add(
                DesignSuggestion(
                    id = "audit-footer-wave-bleed",
                    category = AuditCategory.SPACING_BALANCE,
                    severity = AuditSeverity.MEDIUM,
                    title = "Bottom brand wave stops short of the A4 left and right paper edges",
                    explanation = "Full-bleed architectural waves look intentional when anchored edge-to-edge (0mm to 210mm) at the foot of the page.",
                    affectedElementIds = listOf(waveEl.id),
                    fixLabel = "Stretch Wave Full-Bleed",
                    canAutoFix = true,
                    applyFix = { p ->
                        p.copy(
                            elements = p.elements.map { el ->
                                if (el.id == waveEl.id) {
                                    el.copy(x = 0f, y = Geometry.roundMm(heightMm - 20f), width = widthMm, height = 20f)
                                } else el
                            }
                        )
                    }
                )
            )
        }

        // 8. Check element overlaps
        val contentEls = pageZero.filter {
            it.type != ElementType.LINE && it.type != ElementType.SHAPE && it.role != ElementRole.FOOTER_WAVE
        }
        outer@ for (i in contentEls.indices) {
            for (j in i + 1 until contentEls.size) {
                val a = contentEls[i]
                val b = contentEls[j]
                if (Geometry.rectsOverlap(a, b, -1f)) {
                    suggestions.add(
                        DesignSuggestion(
                            id = "audit-overlap-${a.id}-${b.id}",
                            category = AuditCategory.MARGINS_ALIGNMENT,
                            severity = AuditSeverity.HIGH,
                            title = "\"${a.name}\" overlaps \"${b.name}\"",
                            explanation = "Overlapping text or graphic frames can hide information when printed or exported to PDF/DOCX. Separate vertical bounds cleanly.",
                            affectedElementIds = listOf(a.id, b.id),
                            fixLabel = "Separate Elements",
                            canAutoFix = true,
                            applyFix = { p ->
                                val first = if (a.y <= b.y) a else b
                                val second = if (a.y <= b.y) b else a
                                p.copy(
                                    elements = p.elements.map { el ->
                                        if (el.id == second.id) el.copy(y = Geometry.roundMm(first.y + first.height + 4f)) else el
                                    }
                                )
                            }
                        )
                    )
                    break@outer
                }
            }
        }

        // 9. Check whitespace balance between body and footer
        if (bodyEl != null) {
            val closingEl = pageZero.find { it.role == ElementRole.CLOSING }
            if (closingEl != null) {
                val gap = closingEl.y - (bodyEl.y + bodyEl.height)
                if (gap > 55f) {
                    suggestions.add(
                        DesignSuggestion(
                            id = "audit-whitespace-gap",
                            category = AuditCategory.SPACING_BALANCE,
                            severity = AuditSeverity.LOW,
                            title = "Large ${gap.roundToInt()}mm empty void between quotation body and footer",
                            explanation = "Expanding the body frame height prevents short quotes from leaving an awkward mid-page void.",
                            affectedElementIds = listOf(bodyEl.id),
                            fixLabel = "Balance Body Container",
                            canAutoFix = true,
                            applyFix = { p ->
                                p.copy(
                                    elements = p.elements.map { el ->
                                        if (el.id == bodyEl.id) {
                                            el.copy(y = 82f, height = max(el.height, closingEl.y - 82f - 12f))
                                        } else el
                                    }
                                )
                            }
                        )
                    )
                }
            }
        }

        return suggestions
    }

    fun computeLayoutHealthSummary(suggestions: List<DesignSuggestion>): LayoutHealthSummary {
        val highCount = suggestions.count { it.severity == AuditSeverity.HIGH }
        val mediumCount = suggestions.count { it.severity == AuditSeverity.MEDIUM }
        val lowCount = suggestions.count { it.severity == AuditSeverity.LOW }
        val statusText = when {
            highCount > 0 -> "$highCount high-priority layout issue${if (highCount > 1) "s" else ""} detected"
            mediumCount > 0 -> "$mediumCount refinement${if (mediumCount > 1) "s" else ""} recommended"
            else -> "Layout is balanced and print-ready"
        }
        return LayoutHealthSummary(highCount, mediumCount, lowCount, statusText)
    }

    fun getElementBoundsSummary(el: LetterheadElement): String {
        return "X ${String.format("%.1f", el.x)}mm · Y ${String.format("%.1f", el.y)}mm · ${String.format("%.1f", el.width)}×${String.format("%.1f", el.height)}mm"
    }
}
