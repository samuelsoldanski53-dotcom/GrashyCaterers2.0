import test from 'node:test';
import assert from 'node:assert/strict';
import { alignElementToPage, calculateSnap, reorderZIndex } from './geometry';
import { DEFAULT_PAGE_SETTINGS } from '../data/grashyInitialProject';
import { LetterheadElement } from '../types/letterhead';

const sampleEl: LetterheadElement = {
  id: 'el-1',
  pageIndex: 0,
  type: 'text',
  role: 'custom',
  name: 'Sample',
  x: 30,
  y: 40,
  width: 50,
  height: 20,
  rotation: 0,
  opacity: 1,
  zIndex: 10,
  locked: false,
  visible: true,
};

test('calculateSnap snaps element edge to left margin', () => {
  const res = calculateSnap(
    { id: 'el-1', x: 16, y: 40, width: 50, height: 20, pageIndex: 0 },
    [],
    DEFAULT_PAGE_SETTINGS
  );
  assert.equal(res.x, 15);
  assert.equal(res.guides.some((g) => g.axis === 'x' && g.kind === 'margin'), true);
});

test('alignElementToPage centers horizontally on A4 width', () => {
  const aligned = alignElementToPage(sampleEl, 'center-x', DEFAULT_PAGE_SETTINGS);
  assert.equal(aligned.x, 80); // (210 - 50) / 2
});

test('reorderZIndex brings element forward and sends backward', () => {
  const b: LetterheadElement = { ...sampleEl, id: 'el-2', zIndex: 20 };
  const c: LetterheadElement = { ...sampleEl, id: 'el-3', zIndex: 30 };
  const list = [sampleEl, b, c];

  const movedForward = reorderZIndex(list, 'el-1', 'forward');
  const z1 = movedForward.find((e) => e.id === 'el-1')!.zIndex;
  const z2 = movedForward.find((e) => e.id === 'el-2')!.zIndex;
  assert.equal(z1 > z2, true);

  const sentBack = reorderZIndex(list, 'el-3', 'back');
  assert.equal(sentBack.find((e) => e.id === 'el-3')!.zIndex, 10);
});
