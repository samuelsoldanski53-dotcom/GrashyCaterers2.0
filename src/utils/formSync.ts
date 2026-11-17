import {
  ElementRole,
  LetterheadElement,
  LetterheadFormData,
} from '../types/letterhead';

export const DEFAULT_FORM_DATA: LetterheadFormData = {
  // A. Document Details
  date: '13/11/2026',
  clientName: 'Sunculture Kenya Ltd',
  pax: '200',
  venue: 'Muthangari Drive',
  eventDetails: [
    'MENU SELECTION',
    '',
    '•  Swahili pilau',
    '•  Ugali',
    '•  Chapati',
    '•  Potato wedges',
    '•  Beef stew',
    '•  Supreme grilled chicken',
    '•  Native greens (Half)',
    '•  Stir fried (Half)',
    '•  Kachumbari',
    '•  Watermelon',
    '•  Soda mwala plastic (@850/= per plate)',
  ].join('\n'),

  // B. Payment Information
  transportCost: '5,000/=',
  subtotalCost: '175,000/=',
  vatAmount: '28,000/=',
  totalAmount: 'KES 203,000/=',
  depositAmount: 'KES 101,500/=',
  balance: 'KES 101,500/=',
  paymentMethod: 'Bank Transfer / M-Pesa Buy Goods',
  bankDetails: [
    'Bank Name: NCBA',
    'Branch: Junction',
    'Account Name: Grashy Finest Caterers',
    'Account No: 1006509947',
  ].join('\n'),
  mpesaDetails: ['M-Pesa: Buy Goods Till Number', 'Till no: 972914'].join('\n'),
  paymentInstructions:
    'Thank you for your enquiry, we are delighted to be at your service.\n50% deposit confirms booking.',

  // C. Sender Details
  companyName: 'Grashy Caterers',
  contactPerson: 'Events & Catering Desk',
  phoneNumber: '0727 4XX XXX',
  email: 'info@grashycaterers.co.ke',
  physicalAddress: 'Karen – Mbagathi Ridge, Nairobi',
  postalAddress: 'P.O. Box 24500-00502, Karen',
};

function parseNumericAmount(raw: string): number | null {
  const cleaned = raw.replace(/[^0-9.]/g, '');
  if (!cleaned) return null;
  const num = Number(cleaned);
  return Number.isFinite(num) ? num : null;
}

function formatAmountLike(
  amount: number,
  ...samples: string[]
): string {
  const formatted = amount.toLocaleString('en-US');
  const combined = samples.join(' ');
  const hasKes = /kes/i.test(combined);
  const hasSlashEq = /\/=/.test(combined);
  return `${hasKes ? 'KES ' : ''}${formatted}${hasSlashEq ? '/=' : ''}`;
}

export function computeBalanceFromAmounts(totalRaw: string, depositRaw: string): string | null {
  const total = parseNumericAmount(totalRaw);
  const deposit = parseNumericAmount(depositRaw);
  if (total === null || deposit === null) return null;
  const diff = Math.max(0, total - deposit);
  return formatAmountLike(diff, totalRaw);
}

export function computeTotalFromBreakdown(
  subtotalRaw: string,
  vatRaw: string,
  totalTemplate = ''
): string | null {
  const subtotal = parseNumericAmount(subtotalRaw);
  if (subtotal === null) return null;
  const vat = parseNumericAmount(vatRaw) ?? 0;
  return formatAmountLike(subtotal + vat, totalTemplate, subtotalRaw, vatRaw);
}

export function buildSenderContactText(form: LetterheadFormData): string {
  const lines: string[] = [];
  if (form.contactPerson.trim()) lines.push(`Attn: ${form.contactPerson.trim()}`);
  const telEmail = [
    form.phoneNumber.trim() ? `Tel: ${form.phoneNumber.trim()}` : '',
    form.email.trim(),
  ]
    .filter(Boolean)
    .join('  |  ');
  if (telEmail) lines.push(telEmail);
  if (form.physicalAddress.trim()) lines.push(form.physicalAddress.trim());
  if (form.postalAddress.trim()) lines.push(form.postalAddress.trim());
  return lines.join('\n');
}

export function buildPaymentSummaryText(form: LetterheadFormData): string {
  const lines = ['PAYMENT SUMMARY'];
  if (form.transportCost.trim()) {
    lines.push(`Transport: ${form.transportCost.trim()}`);
  }
  if (form.subtotalCost.trim()) {
    lines.push(`Total Cost (Excl. VAT): ${form.subtotalCost.trim()}`);
  }
  if (form.vatAmount.trim()) {
    lines.push(`VAT: ${form.vatAmount.trim()}`);
  }
  lines.push(`Total Cost (Incl. VAT): ${form.totalAmount.trim() || '—'}`);
  const depositLine: string[] = [];
  if (form.depositAmount.trim()) depositLine.push(`Deposit: ${form.depositAmount.trim()}`);
  if (form.balance.trim()) depositLine.push(`Balance Due: ${form.balance.trim()}`);
  if (depositLine.length > 0) {
    lines.push(depositLine.join('   |   '));
  }
  if (form.paymentMethod.trim()) {
    lines.push(`Payment Method: ${form.paymentMethod.trim()}`);
  }
  return lines.join('\n');
}

export function syncFormToElements(
  elements: LetterheadElement[],
  form: LetterheadFormData
): LetterheadElement[] {
  const roleContentMap: Partial<Record<ElementRole, string>> = {
    'company-title': form.companyName,
    contact: buildSenderContactText(form),
    'event-date': `Date: ${form.date || '—'}`,
    'event-pax': `PAX: ${form.pax ? `${form.pax} Guests` : '—'}`,
    'client-name': `Client: ${form.clientName || '—'}`,
    'event-venue': `Venue: ${form.venue || '—'}`,
    'event-meta': `Date: ${form.date || '—'}   |   PAX: ${form.pax || '—'}\nClient: ${
      form.clientName || '—'
    }\nVenue: ${form.venue || '—'}`,
    body: form.eventDetails,
    'cost-summary': buildPaymentSummaryText(form),
    closing: form.paymentInstructions,
    'payment-bank': form.bankDetails,
    'payment-mpesa': form.mpesaDetails,
  };

  // Preserve every element's visual geometry, style, rotation, and zIndex!
  return elements.map((el) => {
    if (el.pageIndex !== 0) return el;
    const nextContent = roleContentMap[el.role];
    if (nextContent === undefined) return el;
    return {
      ...el,
      content: nextContent,
    };
  });
}

export function syncElementEditToForm(
  form: LetterheadFormData,
  role: ElementRole,
  content: string
): LetterheadFormData {
  switch (role) {
    case 'company-title':
      return { ...form, companyName: content };
    case 'client-name':
      return { ...form, clientName: content.replace(/^Client\s*:\s*/i, '').trim() };
    case 'event-date':
      return { ...form, date: content.replace(/^Date\s*:\s*/i, '').trim() };
    case 'event-pax':
      return {
        ...form,
        pax: content
          .replace(/^PAX\s*:\s*/i, '')
          .replace(/\s*Guests?$/i, '')
          .trim(),
      };
    case 'event-venue':
      return { ...form, venue: content.replace(/^Venue\s*:\s*/i, '').trim() };
    case 'body':
      return { ...form, eventDetails: content };
    case 'closing':
      return { ...form, paymentInstructions: content };
    case 'payment-bank':
      return { ...form, bankDetails: content };
    case 'payment-mpesa':
      return { ...form, mpesaDetails: content };
    default:
      return form;
  }
}

export function createResetClientFormData(current: LetterheadFormData): LetterheadFormData {
  return {
    ...current,
    date: new Date().toLocaleDateString('en-GB'),
    clientName: '',
    pax: '',
    venue: '',
    eventDetails: 'MENU SELECTION\n\n•  ',
    transportCost: '',
    subtotalCost: '',
    vatAmount: '',
    totalAmount: '',
    depositAmount: '',
    balance: '',
    paymentInstructions:
      'Thank you for your enquiry, we are delighted to be at your service.',
  };
}
