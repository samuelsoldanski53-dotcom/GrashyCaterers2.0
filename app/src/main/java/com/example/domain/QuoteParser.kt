package com.example.domain

import com.example.model.ParsedQuotation

object QuoteParser {
    const val SUNCULTURE_SAMPLE_QUOTE =
        "Date:13/11/2026Pax: 200Client: Sunculture Kenya Ltd Venue: Muthangari Drive💫 Swahili pilau 💫Ugali💫Chapati💫 Potato wedges 💫Beef stew💫 Supreme grilled chicken 💫Native greens- half💫 stir fried -half💫 Kachumbari 💫 Watermelon 💫Soda mwala plastic @850/= per plate Transport:5,000/=Total Cost:175,000/=Vat:28,000/=Total Cost:203,000/="

    private fun normalizeMenuItem(raw: String): String {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return ""
        return cleaned
            .replace(Regex("\\s*-\\s*half$", RegexOption.IGNORE_CASE), " (Half)")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun parseQuotationText(rawText: String): ParsedQuotation {
        val text = rawText.replace("\r\n", "\n").trim()

        val dateRegex = Regex(
            "Date\\s*:\\s*([0-9]{1,2}[/.-][0-9]{1,2}[/.-][0-9]{2,4}|[^\\n]+?)(?=\\s*(?:Pax|No\\s*of\\s*guests|Client|Venue|💫|\\n|$))",
            RegexOption.IGNORE_CASE
        )
        val paxRegex = Regex(
            "(?:Pax|No\\s*of\\s*guests|Guests)\\s*:\\s*([0-9,]+(?:\\s*(?:guests?|pax))?)",
            RegexOption.IGNORE_CASE
        )
        val clientRegex = Regex(
            "Client\\s*:\\s*(.+?)(?=\\s*(?:Venue|Date|Pax|💫|\\n|$))",
            RegexOption.IGNORE_CASE
        )
        val venueRegex = Regex(
            "Venue\\s*:\\s*(.+?)(?=\\s*(?:Date|Pax|Client|💫|\\n|$))",
            RegexOption.IGNORE_CASE
        )
        val rateRegex = Regex(
            "(@\\s*[0-9,]+(?:\\s*/=)?(?:\\s*per\\s*plate|\\s*pp)?)",
            RegexOption.IGNORE_CASE
        )
        val transportRegex = Regex(
            "Transport\\s*:\\s*([0-9,]+(?:\\s*/=)?(?:\\s*inclusive)?)",
            RegexOption.IGNORE_CASE
        )
        val vatRegex = Regex(
            "Vat\\s*:\\s*([0-9,]+(?:\\s*/=)?)",
            RegexOption.IGNORE_CASE
        )
        val totalRegex = Regex(
            "(?:Total\\s*Cost|Total|Grand\\s*Total)\\s*:\\s*([0-9,]+(?:\\s*/=)?)",
            RegexOption.IGNORE_CASE
        )

        val dateMatch = dateRegex.find(text)
        val paxMatch = paxRegex.find(text)
        val clientMatch = clientRegex.find(text)
        val venueMatch = venueRegex.find(text)
        val rateMatch = rateRegex.find(text)
        val transportMatch = transportRegex.find(text)
        val vatMatch = vatRegex.find(text)

        val totalMatches = totalRegex.findAll(text).map { it.groupValues[1].trim() }.toList()
        var subtotal = ""
        var totalCost = ""
        if (totalMatches.size >= 2) {
            subtotal = totalMatches.first()
            totalCost = totalMatches.last()
        } else if (totalMatches.size == 1) {
            totalCost = totalMatches[0]
        }

        var menuSection = text
        if (venueMatch != null) {
            menuSection = text.substring(venueMatch.range.last + 1)
        }
        val pricingStart = Regex("(@\\s*[0-9,]+|Transport\\s*:|Total\\s*Cost\\s*:|Vat\\s*:)", RegexOption.IGNORE_CASE)
            .find(menuSection)?.range?.first
        if (pricingStart != null && pricingStart >= 0) {
            menuSection = menuSection.substring(0, pricingStart)
        }

        val menuItems = if (menuSection.contains("💫") || menuSection.contains("•") || menuSection.contains("★")) {
            menuSection.split(Regex("[💫•★]+"))
                .map { normalizeMenuItem(it) }
                .filter { it.isNotEmpty() }
        } else {
            menuSection.split(Regex("\\n+"))
                .map { it.replace(Regex("^[-*]\\s*"), "") }
                .map { normalizeMenuItem(it) }
                .filter { it.isNotEmpty() }
        }

        val extraNotes = mutableListOf<String>()
        if (rateMatch != null) {
            extraNotes.add("Rate: KES ${rateMatch.groupValues[1].replace(Regex("^@\\s*"), "")}")
        }

        return ParsedQuotation(
            date = dateMatch?.groupValues?.get(1)?.trim() ?: "",
            pax = paxMatch?.groupValues?.get(1)?.trim() ?: "",
            client = clientMatch?.groupValues?.get(1)?.trim() ?: "",
            venue = venueMatch?.groupValues?.get(1)?.trim() ?: "",
            menuItems = menuItems,
            ratePerPlate = rateMatch?.groupValues?.get(1)?.trim() ?: "",
            transport = transportMatch?.groupValues?.get(1)?.trim() ?: "",
            subtotal = subtotal,
            vat = vatMatch?.groupValues?.get(1)?.trim() ?: "",
            totalCost = totalCost,
            extraNotes = extraNotes
        )
    }

    fun formatMenuOnlyText(parsed: ParsedQuotation, title: String = "MENU SELECTION"): String {
        val sections = mutableListOf(title, "")
        parsed.menuItems.forEachIndexed { idx, item ->
            val isLast = idx == parsed.menuItems.lastIndex
            val suffix = if (isLast && parsed.ratePerPlate.isNotEmpty()) "   (${parsed.ratePerPlate})" else ""
            sections.add("•  $item$suffix")
        }
        return sections.joinToString("\n")
    }
}
