import test from 'node:test';
import assert from 'node:assert/strict';
import {
  formatCostSummaryText,
  formatEventMetaText,
  formatMenuOnlyText,
  formatQuotationBodyText,
  parseQuotationText,
  SUNCULTURE_SAMPLE_QUOTE,
} from './quoteParser';

test('parseQuotationText extracts Sunculture Kenya Ltd quotation cleanly', () => {
  const parsed = parseQuotationText(SUNCULTURE_SAMPLE_QUOTE);

  assert.equal(parsed.date, '13/11/2026');
  assert.equal(parsed.pax, '200');
  assert.equal(parsed.client, 'Sunculture Kenya Ltd');
  assert.equal(parsed.venue, 'Muthangari Drive');
  assert.equal(parsed.menuItems.length, 11);
  assert.equal(parsed.menuItems[0], 'Swahili pilau');
  assert.equal(parsed.menuItems[6], 'Native greens (Half)');
  assert.equal(parsed.ratePerPlate, '@850/= per plate');
  assert.equal(parsed.transport, '5,000/=');
  assert.equal(parsed.subtotal, '175,000/=');
  assert.equal(parsed.vat, '28,000/=');
  assert.equal(parsed.totalCost, '203,000/=');
});

test('formatEventMetaText, formatMenuOnlyText, and formatCostSummaryText produce split sections', () => {
  const parsed = parseQuotationText(SUNCULTURE_SAMPLE_QUOTE);
  const meta = formatEventMetaText(parsed);
  const menu = formatMenuOnlyText(parsed);
  const cost = formatCostSummaryText(parsed);
  const combined = formatQuotationBodyText(parsed);

  assert.equal(meta.includes('Client: Sunculture Kenya Ltd'), true);
  assert.equal(meta.includes('Pax: 200'), true);
  assert.equal(menu.includes('•  Swahili pilau'), true);
  assert.equal(menu.includes('@850/= per plate'), true);
  assert.equal(cost.includes('Transport: 5,000/='), true);
  assert.equal(cost.includes('Vat: 28,000/='), true);
  assert.equal(cost.includes('203,000/='), true);
  assert.equal(combined.includes('KES 203,000/='), true);
});
