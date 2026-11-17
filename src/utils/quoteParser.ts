import { ParsedQuotation } from '../types/letterhead';

export const SUNCULTURE_SAMPLE_QUOTE = `Date:13/11/2026Pax: 200Client: Sunculture Kenya Ltd Venue: Muthangari Drive💫 Swahili pilau 💫Ugali💫Chapati💫 Potato wedges 💫Beef stew💫 Supreme grilled chicken 💫Native greens- half💫 stir fried -half💫 Kachumbari 💫 Watermelon 💫Soda mwala plastic @850/= per plate Transport:5,000/=Total Cost:175,000/=Vat:28,000/=Total Cost:203,000/=`;

function normalizeMenuItem(raw: string): string {
  const cleaned = raw.replace(/\s+/g, ' ').trim();
  if (!cleaned) return '';
  // Fix common formatting quirks like "stir fried -half" or "Native greens- half"
  return cleaned
    .replace(/\s*-\s*half$/i, ' (Half)')
    .replace(/^./, (c) => c.toUpperCase());
}

export function parseQuotationText(rawText: string): ParsedQuotation {
  const text = rawText.replace(/\r\n/g, '\n').trim();

  // Extract metadata fields even when concatenated without spaces (e.g. Date:13/11/2026Pax: 200Client: ...)
  const dateMatch = text.match(/Date\s*:\s*([0-9]{1,2}[\/.-][0-9]{1,2}[\/.-][0-9]{2,4}|[^\n]+?)(?=\s*(?:Pax|No\s*of\s*guests|Client|Venue|💫|\n|$))/i);
  const paxMatch = text.match(/(?:Pax|No\s*of\s*guests|Guests)\s*:\s*([0-9,]+(?:\s*(?:guests?|pax))?)/i);
  const clientMatch = text.match(/Client\s*:\s*(.+?)(?=\s*(?:Venue|Date|Pax|💫|\n|$))/i);
  const venueMatch = text.match(/Venue\s*:\s*(.+?)(?=\s*(?:Date|Pax|Client|💫|\n|$))/i);

  const rateMatch = text.match(/(@\s*[0-9,]+(?:\s*\/=)?(?:\s*per\s*plate|\s*pp)?)/i);
  const transportMatch = text.match(/Transport\s*:\s*([0-9,]+(?:\s*\/=)?(?:\s*inclusive)?)/i);
  const vatMatch = text.match(/Vat\s*:\s*([0-9,]+(?:\s*\/=)?)/i);

  // Capture all Total Cost occurrences (often subtotal first, then grand total after VAT)
  const totalMatches = Array.from(text.matchAll(/(?:Total\s*Cost|Total|Grand\s*Total)\s*:\s*([0-9,]+(?:\s*\/=)?)/gi)).map(
    (m) => m[1].trim()
  );

  let subtotal = '';
  let totalCost = '';
  if (totalMatches.length >= 2) {
    subtotal = totalMatches[0];
    totalCost = totalMatches[totalMatches.length - 1];
  } else if (totalMatches.length === 1) {
    totalCost = totalMatches[0];
  }

  // Isolate the menu section (between metadata and pricing summary)
  let menuSection = text;
  if (venueMatch && venueMatch.index !== undefined) {
    menuSection = text.slice(venueMatch.index + venueMatch[0].length);
  }
  const pricingStart = menuSection.search(/(@\s*[0-9,]+|Transport\s*:|Total\s*Cost\s*:|Vat\s*:)/i);
  if (pricingStart !== -1) {
    menuSection = menuSection.slice(0, pricingStart);
  }

  let menuItems: string[] = [];
  if (menuSection.includes('💫') || menuSection.includes('•') || menuSection.includes('★')) {
    menuItems = menuSection
      .split(/[💫•★]+/)
      .map(normalizeMenuItem)
      .filter(Boolean);
  } else {
    menuItems = menuSection
      .split(/\n+/)
      .map((line) => line.replace(/^[-*]\s*/, ''))
      .map(normalizeMenuItem)
      .filter(Boolean);
  }

  const extraNotes: string[] = [];
  if (rateMatch) {
    extraNotes.push(`Rate: KES ${rateMatch[1].replace(/^@\s*/, '')}`);
  }

  return {
    date: dateMatch ? dateMatch[1].trim() : '',
    pax: paxMatch ? paxMatch[1].trim() : '',
    client: clientMatch ? clientMatch[1].trim() : '',
    venue: venueMatch ? venueMatch[1].trim() : '',
    menuItems,
    ratePerPlate: rateMatch ? rateMatch[1].trim() : '',
    transport: transportMatch ? transportMatch[1].trim() : '',
    subtotal,
    vat: vatMatch ? vatMatch[1].trim() : '',
    totalCost,
    extraNotes,
  };
}

export function formatEventMetaText(parsed: ParsedQuotation): string {
  const lines = [
    `Date: ${parsed.date || '—'}    |    Pax: ${parsed.pax || '—'}`,
    `Client: ${parsed.client || '—'}`,
    `Venue: ${parsed.venue || '—'}`,
  ];
  return lines.join('\n');
}

export function formatMenuOnlyText(parsed: ParsedQuotation, title = 'MENU SELECTION'): string {
  const sections: string[] = [title, ''];

  if (parsed.menuItems.length > 0) {
    parsed.menuItems.forEach((item, idx) => {
      const isLast = idx === parsed.menuItems.length - 1;
      const suffix = isLast && parsed.ratePerPlate ? `   (${parsed.ratePerPlate})` : '';
      sections.push(`•  ${item}${suffix}`);
    });
  }

  return sections.join('\n');
}

export function formatCostSummaryText(parsed: ParsedQuotation, title = 'COST SUMMARY'): string {
  const costLines: string[] = [title];
  if (parsed.ratePerPlate) {
    costLines.push(`Per Plate Rate: ${parsed.ratePerPlate.replace(/^@\s*/, '')}`);
  }
  if (parsed.transport) {
    costLines.push(`Transport: ${parsed.transport}`);
  }
  if (parsed.subtotal) {
    costLines.push(`Total Cost: ${parsed.subtotal}`);
  }
  if (parsed.vat) {
    costLines.push(`Vat: ${parsed.vat}`);
  }
  if (parsed.totalCost) {
    costLines.push(`Total Cost (Incl. VAT): ${parsed.totalCost}`);
  }
  return costLines.join('\n');
}

export function formatQuotationBodyText(parsed: ParsedQuotation, title = 'CATERING MENU & QUOTATION'): string {
  const sections: string[] = [title, ''];

  if (parsed.menuItems.length > 0) {
    sections.push('Menu Selection:');
    for (const item of parsed.menuItems) {
      sections.push(`•  ${item}`);
    }
    sections.push('');
  }

  const costLines: string[] = [];
  if (parsed.ratePerPlate) {
    costLines.push(`Per Plate Rate:        ${parsed.ratePerPlate.replace(/^@\s*/, 'KES ')}`);
  }
  if (parsed.transport) {
    costLines.push(`Transport:               KES ${parsed.transport}`);
  }
  if (parsed.subtotal) {
    costLines.push(`Subtotal:                  KES ${parsed.subtotal}`);
  }
  if (parsed.vat) {
    costLines.push(`VAT (16%):               KES ${parsed.vat}`);
  }
  if (parsed.totalCost) {
    costLines.push(`Total Cost:              KES ${parsed.totalCost}`);
  }

  if (costLines.length > 0) {
    sections.push('Cost Summary:');
    sections.push(...costLines);
  }

  return sections.join('\n');
}
