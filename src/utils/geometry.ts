import { LetterheadElement, PageSettings, SnapGuide } from '../types/letterhead';

export const A4_WIDTH_MM = 210;
export const A4_HEIGHT_MM = 297;
export const MM_TO_PX_96DPI = 96 / 25.4; // ~3.779527559
export const PT_TO_MM = 25.4 / 72; // ~0.352778

export function roundMm(value: number, step = 0.5): number {
  return Math.round(value / step) * step;
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function snapValueToGrid(value: number, gridSizeMm: number): number {
  if (gridSizeMm <= 0) return value;
  return Math.round(value / gridSizeMm) * gridSizeMm;
}

export function calculateSnap(
  moving: { id: string; x: number; y: number; width: number; height: number; pageIndex: number },
  allElements: LetterheadElement[],
  pageSettings: PageSettings,
  thresholdMm = 2.0
): { x: number; y: number; guides: SnapGuide[] } {
  let snappedX = moving.x;
  let snappedY = moving.y;
  const guides: SnapGuide[] = [];

  if (!pageSettings.snapToGuides) {
    if (pageSettings.snapToGrid) {
      snappedX = snapValueToGrid(snappedX, pageSettings.gridSizeMm);
      snappedY = snapValueToGrid(snappedY, pageSettings.gridSizeMm);
    }
    return { x: roundMm(snappedX), y: roundMm(snappedY), guides };
  }

  const { margins, widthMm, heightMm } = pageSettings;
  const xTargets: Array<{ pos: number; kind: SnapGuide['kind'] }> = [
    { pos: margins.left, kind: 'margin' },
    { pos: widthMm - margins.right, kind: 'margin' },
    { pos: widthMm / 2, kind: 'page-center' },
  ];
  const yTargets: Array<{ pos: number; kind: SnapGuide['kind'] }> = [
    { pos: margins.top, kind: 'margin' },
    { pos: heightMm - margins.bottom, kind: 'margin' },
    { pos: heightMm / 2, kind: 'page-center' },
  ];

  for (const el of allElements) {
    if (el.id === moving.id || el.pageIndex !== moving.pageIndex || !el.visible) continue;
    xTargets.push(
      { pos: el.x, kind: 'element' },
      { pos: el.x + el.width / 2, kind: 'element' },
      { pos: el.x + el.width, kind: 'element' }
    );
    yTargets.push(
      { pos: el.y, kind: 'element' },
      { pos: el.y + el.height / 2, kind: 'element' },
      { pos: el.y + el.height, kind: 'element' }
    );
  }

  // Check X edges: left, center, right
  let bestDx = thresholdMm + 1;
  let bestXGuide: SnapGuide | null = null;
  let bestNewX = snappedX;

  const movingXEdges = [
    { offset: 0, edgePos: moving.x },
    { offset: moving.width / 2, edgePos: moving.x + moving.width / 2 },
    { offset: moving.width, edgePos: moving.x + moving.width },
  ];

  for (const edge of movingXEdges) {
    for (const target of xTargets) {
      const diff = Math.abs(edge.edgePos - target.pos);
      if (diff < bestDx) {
        bestDx = diff;
        bestNewX = target.pos - edge.offset;
        bestXGuide = { axis: 'x', positionMm: target.pos, kind: target.kind };
      }
    }
  }

  if (bestXGuide && bestDx <= thresholdMm) {
    snappedX = bestNewX;
    guides.push(bestXGuide);
  } else if (pageSettings.snapToGrid) {
    snappedX = snapValueToGrid(snappedX, pageSettings.gridSizeMm);
  }

  // Check Y edges: top, center, bottom
  let bestDy = thresholdMm + 1;
  let bestYGuide: SnapGuide | null = null;
  let bestNewY = snappedY;

  const movingYEdges = [
    { offset: 0, edgePos: moving.y },
    { offset: moving.height / 2, edgePos: moving.y + moving.height / 2 },
    { offset: moving.height, edgePos: moving.y + moving.height },
  ];

  for (const edge of movingYEdges) {
    for (const target of yTargets) {
      const diff = Math.abs(edge.edgePos - target.pos);
      if (diff < bestDy) {
        bestDy = diff;
        bestNewY = target.pos - edge.offset;
        bestYGuide = { axis: 'y', positionMm: target.pos, kind: target.kind };
      }
    }
  }

  if (bestYGuide && bestDy <= thresholdMm) {
    snappedY = bestNewY;
    guides.push(bestYGuide);
  } else if (pageSettings.snapToGrid) {
    snappedY = snapValueToGrid(snappedY, pageSettings.gridSizeMm);
  }

  return {
    x: roundMm(snappedX),
    y: roundMm(snappedY),
    guides,
  };
}

export type AlignmentTarget = 'left' | 'center-x' | 'right' | 'top' | 'middle-y' | 'bottom';

export function alignElementToPage(
  el: LetterheadElement,
  alignment: AlignmentTarget,
  pageSettings: PageSettings,
  respectMargins = true
): LetterheadElement {
  const leftBound = respectMargins ? pageSettings.margins.left : 0;
  const rightBound = respectMargins ? pageSettings.widthMm - pageSettings.margins.right : pageSettings.widthMm;
  const topBound = respectMargins ? pageSettings.margins.top : 0;
  const bottomBound = respectMargins ? pageSettings.heightMm - pageSettings.margins.bottom : pageSettings.heightMm;

  switch (alignment) {
    case 'left':
      return { ...el, x: roundMm(leftBound) };
    case 'center-x':
      return { ...el, x: roundMm((pageSettings.widthMm - el.width) / 2) };
    case 'right':
      return { ...el, x: roundMm(rightBound - el.width) };
    case 'top':
      return { ...el, y: roundMm(topBound) };
    case 'middle-y':
      return { ...el, y: roundMm((pageSettings.heightMm - el.height) / 2) };
    case 'bottom':
      return { ...el, y: roundMm(bottomBound - el.height) };
  }
}

export function reorderZIndex(
  elements: LetterheadElement[],
  targetId: string,
  action: 'forward' | 'backward' | 'front' | 'back'
): LetterheadElement[] {
  const target = elements.find((e) => e.id === targetId);
  if (!target) return elements;

  const samePage = elements
    .filter((e) => e.pageIndex === target.pageIndex)
    .sort((a, b) => a.zIndex - b.zIndex);

  const idx = samePage.findIndex((e) => e.id === targetId);
  if (idx === -1) return elements;

  const reordered = [...samePage];
  if (action === 'forward' && idx < reordered.length - 1) {
    [reordered[idx], reordered[idx + 1]] = [reordered[idx + 1], reordered[idx]];
  } else if (action === 'backward' && idx > 0) {
    [reordered[idx], reordered[idx - 1]] = [reordered[idx - 1], reordered[idx]];
  } else if (action === 'front') {
    const [item] = reordered.splice(idx, 1);
    reordered.push(item);
  } else if (action === 'back') {
    const [item] = reordered.splice(idx, 1);
    reordered.unshift(item);
  }

  const newZMap = new Map<string, number>();
  reordered.forEach((item, i) => newZMap.set(item.id, (i + 1) * 10));

  return elements.map((el) =>
    newZMap.has(el.id) ? { ...el, zIndex: newZMap.get(el.id)! } : el
  );
}

export function rectsOverlap(
  a: Pick<LetterheadElement, 'x' | 'y' | 'width' | 'height'>,
  b: Pick<LetterheadElement, 'x' | 'y' | 'width' | 'height'>,
  padding = 0
): boolean {
  return !(
    a.x + a.width + padding <= b.x ||
    b.x + b.width + padding <= a.x ||
    a.y + a.height + padding <= b.y ||
    b.y + b.height + padding <= a.y
  );
}
