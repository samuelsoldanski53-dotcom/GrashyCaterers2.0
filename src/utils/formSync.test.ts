import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPaymentSummaryText,
  computeBalanceFromAmounts,
  computeTotalFromBreakdown,
  createResetClientFormData,
  DEFAULT_FORM_DATA,
  syncElementEditToForm,
  syncFormToElements,
} from './formSync';
import { LetterheadElement } from '../types/letterhead';

test('computeBalanceFromAmounts calculates KES balance cleanly', () => {
  assert.equal(computeBalanceFromAmounts('KES 203,000/=', 'KES 100,000/='), 'KES 103,000/=');
  assert.equal(computeBalanceFromAmounts('175000', '75000'), '100,000');
});

test('computeTotalFromBreakdown sums subtotal and VAT cleanly', () => {
  assert.equal(computeTotalFromBreakdown('175,000/=', '28,000/=', 'KES 203,000/='), 'KES 203,000/=');
  assert.equal(computeTotalFromBreakdown('100,000/=', '16,000/='), '116,000/=');
});

test('buildPaymentSummaryText includes Transport, Total Cost, VAT, Total Cost (Incl. VAT), Deposit & Balance', () => {
  const summary = buildPaymentSummaryText(DEFAULT_FORM_DATA);
  assert.match(summary, /Transport:\s*5,000\/=/);
  assert.match(summary, /Total Cost \(Excl\. VAT\):\s*175,000\/=/);
  assert.match(summary, /VAT:\s*28,000\/=/);
  assert.match(summary, /Total Cost \(Incl\. VAT\):\s*KES 203,000\/=/);
});

test('syncFormToElements updates text content while preserving visual layout coordinates', () => {
  const clientEl: LetterheadElement = {
    id: 'grashy-event-client',
    pageIndex: 0,
    type: 'text',
    role: 'client-name',
    name: 'Client Name',
    x: 42,
    y: 59,
    width: 140,
    height: 7,
    rotation: 0,
    opacity: 1,
    zIndex: 43,
    locked: false,
    visible: true,
    content: 'Client: Sunculture Kenya Ltd',
  };

  const updatedForm = { ...DEFAULT_FORM_DATA, clientName: 'Acorn Holdings Ltd' };
  const synced = syncFormToElements([clientEl], updatedForm);

  assert.equal(synced[0].content, 'Client: Acorn Holdings Ltd');
  assert.equal(synced[0].x, 42);
  assert.equal(synced[0].y, 59);
  assert.equal(synced[0].width, 140);
});

test('syncElementEditToForm keeps form state aligned with direct canvas edits', () => {
  const next = syncElementEditToForm(DEFAULT_FORM_DATA, 'client-name', 'Client: Safaricom PLC');
  assert.equal(next.clientName, 'Safaricom PLC');
});

test('createResetClientFormData clears client and cost fields while keeping sender and bank info', () => {
  const reset = createResetClientFormData(DEFAULT_FORM_DATA);
  assert.equal(reset.clientName, '');
  assert.equal(reset.transportCost, '');
  assert.equal(reset.subtotalCost, '');
  assert.equal(reset.vatAmount, '');
  assert.equal(reset.totalAmount, '');
  assert.equal(reset.companyName, DEFAULT_FORM_DATA.companyName);
  assert.equal(reset.bankDetails, DEFAULT_FORM_DATA.bankDetails);
});
