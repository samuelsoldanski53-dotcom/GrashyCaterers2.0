package com.example.domain

import com.example.model.ElementRole
import com.example.model.LetterheadElement
import com.example.model.LetterheadFormData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

object FormSync {
    val DEFAULT_FORM_DATA = LetterheadFormData(
        date = "13/11/2026",
        clientName = "Sunculture Kenya Ltd",
        pax = "200",
        venue = "Muthangari Drive",
        eventDetails = listOf(
            "MENU SELECTION",
            "",
            "•  Swahili pilau",
            "•  Ugali",
            "•  Chapati",
            "•  Potato wedges",
            "•  Beef stew",
            "•  Supreme grilled chicken",
            "•  Native greens (Half)",
            "•  Stir fried (Half)",
            "•  Kachumbari",
            "•  Watermelon",
            "•  Soda mwala plastic (@850/= per plate)"
        ).joinToString("\n"),
        transportCost = "5,000/=",
        subtotalCost = "175,000/=",
        vatAmount = "28,000/=",
        totalAmount = "KES 203,000/=",
        depositAmount = "KES 101,500/=",
        balance = "KES 101,500/=",
        paymentMethod = "Bank Transfer / M-Pesa Buy Goods",
        bankDetails = listOf(
            "Bank Name: NCBA",
            "Branch: Junction",
            "Account Name: Grashy Finest Caterers",
            "Account No: 1006509947"
        ).joinToString("\n"),
        mpesaDetails = listOf(
            "M-Pesa: Buy Goods Till Number",
            "Till no: 972914"
        ).joinToString("\n"),
        paymentInstructions = "Thank you for your enquiry, we are delighted to be at your service.\n50% deposit confirms booking.",
        companyName = "Grashy Caterers",
        contactPerson = "Events & Catering Desk",
        phoneNumber = "0727 4XX XXX",
        email = "info@grashycaterers.co.ke",
        physicalAddress = "Karen – Mbagathi Ridge, Nairobi",
        postalAddress = "P.O. Box 24500-00502, Karen"
    )

    private fun parseNumericAmount(raw: String): Double? {
        val cleaned = raw.replace(Regex("[^0-9.]"), "")
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun formatAmountLike(amount: Double, vararg samples: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        val formatted = if (amount % 1.0 == 0.0) {
            nf.format(amount.roundToLong())
        } else {
            nf.format(amount)
        }
        val combined = samples.joinToString(" ")
        val hasKes = Regex("kes", RegexOption.IGNORE_CASE).containsMatchIn(combined)
        val hasSlashEq = combined.contains("/=")
        return "${if (hasKes) "KES " else ""}$formatted${if (hasSlashEq) "/=" else ""}"
    }

    fun computeBalanceFromAmounts(totalRaw: String, depositRaw: String): String? {
        val total = parseNumericAmount(totalRaw) ?: return null
        val deposit = parseNumericAmount(depositRaw) ?: return null
        val diff = max(0.0, total - deposit)
        return formatAmountLike(diff, totalRaw)
    }

    fun computeTotalFromBreakdown(
        subtotalRaw: String,
        vatRaw: String,
        totalTemplate: String = ""
    ): String? {
        val subtotal = parseNumericAmount(subtotalRaw) ?: return null
        val vat = parseNumericAmount(vatRaw) ?: 0.0
        return formatAmountLike(subtotal + vat, totalTemplate, subtotalRaw, vatRaw)
    }

    fun buildSenderContactText(form: LetterheadFormData): String {
        val lines = mutableListOf<String>()
        if (form.contactPerson.trim().isNotEmpty()) {
            lines.add("Attn: ${form.contactPerson.trim()}")
        }
        val telEmail = listOf(
            if (form.phoneNumber.trim().isNotEmpty()) "Tel: ${form.phoneNumber.trim()}" else "",
            form.email.trim()
        ).filter { it.isNotEmpty() }.joinToString("  |  ")
        if (telEmail.isNotEmpty()) lines.add(telEmail)
        if (form.physicalAddress.trim().isNotEmpty()) lines.add(form.physicalAddress.trim())
        if (form.postalAddress.trim().isNotEmpty()) lines.add(form.postalAddress.trim())
        return lines.joinToString("\n")
    }

    fun buildPaymentSummaryText(form: LetterheadFormData): String {
        val lines = mutableListOf("PAYMENT SUMMARY")
        if (form.transportCost.trim().isNotEmpty()) {
            lines.add("Transport: ${form.transportCost.trim()}")
        }
        if (form.subtotalCost.trim().isNotEmpty()) {
            lines.add("Total Cost (Excl. VAT): ${form.subtotalCost.trim()}")
        }
        if (form.vatAmount.trim().isNotEmpty()) {
            lines.add("VAT: ${form.vatAmount.trim()}")
        }
        lines.add("Total Cost (Incl. VAT): ${form.totalAmount.trim().ifEmpty { "—" }}")
        val depositLine = mutableListOf<String>()
        if (form.depositAmount.trim().isNotEmpty()) depositLine.add("Deposit: ${form.depositAmount.trim()}")
        if (form.balance.trim().isNotEmpty()) depositLine.add("Balance Due: ${form.balance.trim()}")
        if (depositLine.isNotEmpty()) {
            lines.add(depositLine.joinToString("   |   "))
        }
        if (form.paymentMethod.trim().isNotEmpty()) {
            lines.add("Payment Method: ${form.paymentMethod.trim()}")
        }
        return lines.joinToString("\n")
    }

    fun syncFormToElements(
        elements: List<LetterheadElement>,
        form: LetterheadFormData
    ): List<LetterheadElement> {
        val roleContentMap: Map<ElementRole, String> = mapOf(
            ElementRole.COMPANY_TITLE to form.companyName,
            ElementRole.CONTACT to buildSenderContactText(form),
            ElementRole.EVENT_DATE to "Date: ${form.date.ifEmpty { "—" }}",
            ElementRole.EVENT_PAX to "PAX: ${if (form.pax.isNotEmpty()) "${form.pax} Guests" else "—"}",
            ElementRole.CLIENT_NAME to "Client: ${form.clientName.ifEmpty { "—" }}",
            ElementRole.EVENT_VENUE to "Venue: ${form.venue.ifEmpty { "—" }}",
            ElementRole.EVENT_META to "Date: ${form.date.ifEmpty { "—" }}   |   PAX: ${form.pax.ifEmpty { "—" }}\nClient: ${form.clientName.ifEmpty { "—" }}\nVenue: ${form.venue.ifEmpty { "—" }}",
            ElementRole.BODY to form.eventDetails,
            ElementRole.COST_SUMMARY to buildPaymentSummaryText(form),
            ElementRole.CLOSING to form.paymentInstructions,
            ElementRole.PAYMENT_BANK to form.bankDetails,
            ElementRole.PAYMENT_MPESA to form.mpesaDetails
        )

        return elements.map { el ->
            if (el.pageIndex != 0) el
            else {
                val nextContent = roleContentMap[el.role]
                if (nextContent != null) el.copy(content = nextContent) else el
            }
        }
    }

    fun syncElementEditToForm(
        form: LetterheadFormData,
        role: ElementRole,
        content: String
    ): LetterheadFormData {
        return when (role) {
            ElementRole.COMPANY_TITLE -> form.copy(companyName = content)
            ElementRole.CLIENT_NAME -> form.copy(clientName = content.replace(Regex("^Client\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim())
            ElementRole.EVENT_DATE -> form.copy(date = content.replace(Regex("^Date\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim())
            ElementRole.EVENT_PAX -> form.copy(
                pax = content.replace(Regex("^PAX\\s*:\\s*", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s*Guests?$", RegexOption.IGNORE_CASE), "")
                    .trim()
            )
            ElementRole.EVENT_VENUE -> form.copy(venue = content.replace(Regex("^Venue\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim())
            ElementRole.BODY -> form.copy(eventDetails = content)
            ElementRole.CLOSING -> form.copy(paymentInstructions = content)
            ElementRole.PAYMENT_BANK -> form.copy(bankDetails = content)
            ElementRole.PAYMENT_MPESA -> form.copy(mpesaDetails = content)
            else -> form
        }
    }

    fun createResetClientFormData(current: LetterheadFormData): LetterheadFormData {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(Date())
        return current.copy(
            date = today,
            clientName = "",
            pax = "",
            venue = "",
            eventDetails = "MENU SELECTION\n\n•  ",
            transportCost = "",
            subtotalCost = "",
            vatAmount = "",
            totalAmount = "",
            depositAmount = "",
            balance = "",
            paymentInstructions = "Thank you for your enquiry, we are delighted to be at your service."
        )
    }
}
