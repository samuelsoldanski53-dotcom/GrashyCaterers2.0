import test from 'node:test';
import assert from 'node:assert/strict';
import {
  applyCompleteRedesign,
  createOriginalGrashyProject,
  createRedesignedGrashyProject,
} from '../data/grashyInitialProject';
import { analyzeLetterheadDesign } from './designAnalyzer';

test('analyzeLetterheadDesign detects real defects in the original Grashy PDF', () => {
  const original = createOriginalGrashyProject();
  const suggestions = analyzeLetterheadDesign(original);

  assert.equal(suggestions.length >= 6, true);
  const ids = suggestions.map((s) => s.id);
  assert.equal(ids.includes('audit-company-title'), true);
  assert.equal(ids.includes('audit-contact-clipped'), true);
  assert.equal(ids.includes('audit-dotted-separator'), true);
  assert.equal(ids.includes('audit-payment-columns'), true);
});

test('createRedesignedGrashyProject has 0 layout issues and all 4 document sections', () => {
  const redesigned = createRedesignedGrashyProject();
  const audit = analyzeLetterheadDesign(redesigned);
  assert.equal(audit.length, 0);

  const roles = redesigned.elements.map((e) => e.role);
  assert.equal(roles.includes('contact'), true);
  assert.equal(roles.includes('event-date'), true);
  assert.equal(roles.includes('event-pax'), true);
  assert.equal(roles.includes('client-name'), true);
  assert.equal(roles.includes('event-venue'), true);
  assert.equal(roles.includes('body'), true);
  assert.equal(roles.includes('cost-summary'), true);
  assert.equal(roles.includes('closing'), true);
  assert.equal(roles.includes('payment-bank'), true);
  assert.equal(roles.includes('payment-mpesa'), true);
});

test('applyCompleteRedesign resolves all high-severity design issues and preserves event lines', () => {
  const original = createOriginalGrashyProject();
  const redesigned = applyCompleteRedesign(original);
  const postAudit = analyzeLetterheadDesign(redesigned);

  const highIssues = postAudit.filter((s) => s.severity === 'high');
  assert.equal(highIssues.length, 0);
  assert.equal(redesigned.layoutMode, 'refined');

  const meta = redesigned.elements.find((e) => e.role === 'event-meta');
  assert.ok(meta?.content);
  assert.equal(meta!.content!.split('\n').length, 4);
});
