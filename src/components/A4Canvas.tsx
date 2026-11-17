import React, { useEffect, useRef, useState } from 'react';
import { RotateCw } from 'lucide-react';
import {
  LetterheadElement,
  PageSettings,
  SnapGuide,
} from '../types/letterhead';
import {
  calculateSnap,
  clamp,
  MM_TO_PX_96DPI,
  roundMm,
} from '../utils/geometry';

interface A4CanvasProps {
  interactive?: boolean;
  pageSettings: PageSettings;
  pageCount: number;
  activePage: number;
  elements: LetterheadElement[];
  selectedId: string | null;
  highlightedIds: string[];
  zoom: number;
  onSelectPage: (pageIndex: number) => void;
  onSelectElement: (id: string | null) => void;
  onUpdateElement: (id: string, patch: Partial<LetterheadElement>, recordHistory?: boolean) => void;
  onCommitElementHistory: (snapshotBefore: LetterheadElement[]) => void;
}

type DragMode =
  | { kind: 'move'; startX: number; startY: number; origX: number; origY: number }
  | {
      kind: 'resize';
      handle: 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';
      startX: number;
      startY: number;
      origX: number;
      origY: number;
      origW: number;
      origH: number;
    }
  | {
      kind: 'rotate';
      centerX: number;
      centerY: number;
      startAngle: number;
      origRot: number;
    };

export const A4Canvas: React.FC<A4CanvasProps> = ({
  interactive = true,
  pageSettings,
  pageCount,
  activePage,
  elements,
  selectedId,
  highlightedIds,
  zoom,
  onSelectPage,
  onSelectElement,
  onUpdateElement,
  onCommitElementHistory,
}) => {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [snapGuides, setSnapGuides] = useState<SnapGuide[]>([]);
  const inlineEditSnapshotRef = useRef<{
    elId: string;
    snapshot: LetterheadElement[];
    initialContent: string;
  } | null>(null);
  const dragStateRef = useRef<{
    elId: string;
    mode: DragMode;
    snapshotBefore: LetterheadElement[];
    didMove: boolean;
  } | null>(null);

  const pxPerMm = MM_TO_PX_96DPI * zoom;
  const pageWidthPx = pageSettings.widthMm * pxPerMm;
  const pageHeightPx = pageSettings.heightMm * pxPerMm;

  // Keep latest callbacks in refs for window mouse listeners
  const elementsRef = useRef(elements);
  elementsRef.current = elements;
  const pageSettingsRef = useRef(pageSettings);
  pageSettingsRef.current = pageSettings;
  const pxPerMmRef = useRef(pxPerMm);
  pxPerMmRef.current = pxPerMm;

  useEffect(() => {
    const handlePointerMove = (e: PointerEvent) => {
      const drag = dragStateRef.current;
      if (!drag) return;
      const el = elementsRef.current.find((item) => item.id === drag.elId);
      if (!el) return;

      if (drag.mode.kind === 'move' || drag.mode.kind === 'resize') {
        const distPx = Math.hypot(e.clientX - drag.mode.startX, e.clientY - drag.mode.startY);
        if (!drag.didMove && distPx < 2.5) return;
      }

      const scale = pxPerMmRef.current;
      drag.didMove = true;

      if (drag.mode.kind === 'move') {
        const dxMm = (e.clientX - drag.mode.startX) / scale;
        const dyMm = (e.clientY - drag.mode.startY) / scale;
        const rawX = drag.mode.origX + dxMm;
        const rawY = drag.mode.origY + dyMm;

        const snapped = calculateSnap(
          {
            id: el.id,
            x: rawX,
            y: rawY,
            width: el.width,
            height: el.height,
            pageIndex: el.pageIndex,
          },
          elementsRef.current,
          pageSettingsRef.current
        );

        setSnapGuides(snapped.guides);
        onUpdateElement(
          el.id,
          {
            x: clamp(snapped.x, -20, pageSettingsRef.current.widthMm - 4),
            y: clamp(snapped.y, -20, pageSettingsRef.current.heightMm - 4),
          },
          false
        );
      } else if (drag.mode.kind === 'resize') {
        const dxMm = (e.clientX - drag.mode.startX) / scale;
        const dyMm = (e.clientY - drag.mode.startY) / scale;
        const { handle, origX, origY, origW, origH } = drag.mode;
        let nextX = origX;
        let nextY = origY;
        let nextW = origW;
        let nextH = origH;

        const minW = 6;
        const minH = el.type === 'line' ? 1 : 4;

        if (handle.includes('e')) {
          nextW = Math.max(minW, roundMm(origW + dxMm));
        }
        if (handle.includes('s')) {
          nextH = Math.max(minH, roundMm(origH + dyMm));
        }
        if (handle.includes('w')) {
          const proposedW = Math.max(minW, roundMm(origW - dxMm));
          nextX = roundMm(origX + (origW - proposedW));
          nextW = proposedW;
        }
        if (handle.includes('n')) {
          const proposedH = Math.max(minH, roundMm(origH - dyMm));
          nextY = roundMm(origY + (origH - proposedH));
          nextH = proposedH;
        }

        onUpdateElement(
          el.id,
          { x: nextX, y: nextY, width: nextW, height: nextH },
          false
        );
      } else if (drag.mode.kind === 'rotate') {
        const angleRad = Math.atan2(
          e.clientY - drag.mode.centerY,
          e.clientX - drag.mode.centerX
        );
        const deg = ((angleRad - drag.mode.startAngle) * 180) / Math.PI;
        let nextRot = Math.round(drag.mode.origRot + deg);
        if (e.shiftKey) {
          nextRot = Math.round(nextRot / 15) * 15;
        }
        onUpdateElement(el.id, { rotation: ((nextRot % 360) + 360) % 360 }, false);
      }
    };

    const handlePointerUp = () => {
      const drag = dragStateRef.current;
      if (!drag) return;
      if (drag.didMove) {
        onCommitElementHistory(drag.snapshotBefore);
      }
      dragStateRef.current = null;
      setSnapGuides([]);
    };

    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
    return () => {
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
    };
  }, [onUpdateElement, onCommitElementHistory]);

  const startMove = (e: React.PointerEvent, el: LetterheadElement) => {
    e.stopPropagation();
    onSelectPage(el.pageIndex);
    onSelectElement(el.id);
    if (el.locked || editingId === el.id) return;

    dragStateRef.current = {
      elId: el.id,
      mode: {
        kind: 'move',
        startX: e.clientX,
        startY: e.clientY,
        origX: el.x,
        origY: el.y,
      },
      snapshotBefore: elements.map((item) => ({ ...item })),
      didMove: false,
    };
  };

  const startResize = (
    e: React.PointerEvent,
    el: LetterheadElement,
    handle: 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w'
  ) => {
    e.stopPropagation();
    if (el.locked) return;
    dragStateRef.current = {
      elId: el.id,
      mode: {
        kind: 'resize',
        handle,
        startX: e.clientX,
        startY: e.clientY,
        origX: el.x,
        origY: el.y,
        origW: el.width,
        origH: el.height,
      },
      snapshotBefore: elements.map((item) => ({ ...item })),
      didMove: false,
    };
  };

  const startRotate = (e: React.PointerEvent, el: LetterheadElement, boxEl: HTMLElement | null) => {
    e.stopPropagation();
    if (el.locked || !boxEl) return;
    const rect = boxEl.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const startAngle = Math.atan2(e.clientY - centerY, e.clientX - centerX);

    dragStateRef.current = {
      elId: el.id,
      mode: {
        kind: 'rotate',
        centerX,
        centerY,
        startAngle,
        origRot: el.rotation,
      },
      snapshotBefore: elements.map((item) => ({ ...item })),
      didMove: false,
    };
  };

  const commitInlineEditIfChanged = () => {
    const info = inlineEditSnapshotRef.current;
    if (!info) return;
    inlineEditSnapshotRef.current = null;
    const current = elementsRef.current.find((item) => item.id === info.elId);
    if (current && (current.content || '') !== info.initialContent) {
      onCommitElementHistory(info.snapshot);
    }
  };

  return (
    <div
      className="flex-1 overflow-auto canvas-tray-bg p-3 sm:p-6 md:p-10 flex flex-col gap-6 sm:gap-10 select-none"
      onClick={() => {
        commitInlineEditIfChanged();
        setEditingId(null);
        onSelectElement(null);
      }}
    >
      {Array.from({ length: pageCount }, (_, pageIdx) => {
        const pageElements = elements
          .filter((el) => el.pageIndex === pageIdx && el.visible)
          .sort((a, b) => a.zIndex - b.zIndex);

        const isActivePage = pageIdx === activePage;

        return (
          <div key={pageIdx} className="relative flex flex-col items-center mx-auto shrink-0">
            {/* Page Label */}
            <div className="w-full flex items-center justify-between mb-2 text-[11px] font-mono tabular-nums text-slate-500">
              <span>
                {interactive ? `Page ${pageIdx + 1} · A4 (210 × 297 mm)` : `Live Preview · Page ${pageIdx + 1} (A4)`}
              </span>
              {pageCount > 1 && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectPage(pageIdx);
                  }}
                  className={`px-2 py-0.5 rounded text-[11px] font-sans font-medium ${
                    isActivePage ? 'text-[#C45911] font-semibold' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  {isActivePage ? 'Active Page' : 'Select Page'}
                </button>
              )}
            </div>

            <div className="relative flex">
              {/* Vertical Left Ruler */}
              {interactive && pageSettings.showRulers && (
                <div
                  className="w-5 bg-white/80 border-y border-l border-slate-300 relative overflow-hidden shrink-0"
                  style={{ height: pageHeightPx }}
                  aria-hidden="true"
                >
                  {Array.from({ length: 30 }, (_, cm) => (
                    <div
                      key={cm}
                      className="absolute left-0 right-0 border-t border-slate-300 text-[8px] font-mono text-slate-400 pl-0.5"
                      style={{ top: cm * 10 * pxPerMm }}
                    >
                      {cm > 0 && cm % 2 === 0 ? cm * 10 : ''}
                    </div>
                  ))}
                </div>
              )}

              <div className="flex flex-col">
                {/* Horizontal Top Ruler */}
                {interactive && pageSettings.showRulers && (
                  <div
                    className="h-5 bg-white/80 border-t border-x border-slate-300 relative overflow-hidden"
                    style={{ width: pageWidthPx }}
                    aria-hidden="true"
                  >
                    {Array.from({ length: 22 }, (_, cm) => (
                      <div
                        key={cm}
                        className="absolute top-0 bottom-0 border-l border-slate-300 text-[8px] font-mono text-slate-400 pl-0.5"
                        style={{ left: cm * 10 * pxPerMm }}
                      >
                        {cm > 0 && cm % 2 === 0 ? cm * 10 : ''}
                      </div>
                    ))}
                  </div>
                )}

                {/* A4 Paper Surface */}
                <div
                  data-testid={`a4-page-${pageIdx}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onSelectPage(pageIdx);
                    commitInlineEditIfChanged();
                    setEditingId(null);
                    onSelectElement(null);
                  }}
                  className={`relative isolate bg-white overflow-hidden transition-shadow ${
                    isActivePage
                      ? 'shadow-[0_12px_40px_rgba(15,23,42,0.16)] ring-1 ring-slate-300'
                      : 'shadow-[0_6px_24px_rgba(15,23,42,0.10)] ring-1 ring-slate-200'
                  }`}
                  style={{
                    width: pageWidthPx,
                    height: pageHeightPx,
                  }}
                >
                  {/* Optional Snap Grid */}
                  {interactive && pageSettings.showGrid && (
                    <div
                      className="absolute inset-0 pointer-events-none"
                      style={{
                        backgroundImage:
                          'linear-gradient(to right, rgba(15,23,42,0.05) 1px, transparent 1px), linear-gradient(to bottom, rgba(15,23,42,0.05) 1px, transparent 1px)',
                        backgroundSize: `${pageSettings.gridSizeMm * pxPerMm}px ${
                          pageSettings.gridSizeMm * pxPerMm
                        }px`,
                      }}
                    />
                  )}

                  {/* Optional Margin Guide Box */}
                  {interactive && pageSettings.showMargins && (
                    <div
                      className="absolute pointer-events-none border border-dashed border-sky-400/55"
                      style={{
                        left: pageSettings.margins.left * pxPerMm,
                        top: pageSettings.margins.top * pxPerMm,
                        right: pageSettings.margins.right * pxPerMm,
                        bottom: pageSettings.margins.bottom * pxPerMm,
                      }}
                    />
                  )}

                  {/* Active Snap Alignment Guides */}
                  {isActivePage &&
                    snapGuides.map((g, idx) =>
                      g.axis === 'x' ? (
                        <div
                          key={`gx-${idx}`}
                          className="absolute top-0 bottom-0 border-l border-dashed border-[#C45911] pointer-events-none z-[999]"
                          style={{ left: g.positionMm * pxPerMm }}
                        />
                      ) : (
                        <div
                          key={`gy-${idx}`}
                          className="absolute left-0 right-0 border-t border-dashed border-[#C45911] pointer-events-none z-[999]"
                          style={{ top: g.positionMm * pxPerMm }}
                        />
                      )
                    )}

                  {/* Document Elements */}
                  {pageElements.map((el) => {
                    const isSelected = interactive && el.id === selectedId;
                    const isHighlighted = interactive && highlightedIds.includes(el.id);
                    const isEditing = interactive && editingId === el.id;

                    return (
                      <ElementRenderer
                        key={el.id}
                        element={el}
                        interactive={interactive}
                        pxPerMm={pxPerMm}
                        zoom={zoom}
                        isSelected={isSelected}
                        isHighlighted={isHighlighted}
                        isEditing={isEditing}
                        onPointerDown={(e) => {
                          if (!interactive) return;
                          startMove(e, el);
                        }}
                        onDoubleClick={(e) => {
                          if (!interactive) return;
                          e.stopPropagation();
                          if (el.type === 'text' && !el.locked) {
                            commitInlineEditIfChanged();
                            inlineEditSnapshotRef.current = {
                              elId: el.id,
                              snapshot: elements.map((item) => ({ ...item })),
                              initialContent: el.content || '',
                            };
                            onSelectElement(el.id);
                            setEditingId(el.id);
                          }
                        }}
                        onContentChange={(val) =>
                          onUpdateElement(el.id, { content: val }, false)
                        }
                        onStopEditing={() => {
                          commitInlineEditIfChanged();
                          setEditingId(null);
                        }}
                        onResizeStart={(e, handle) => startResize(e, el, handle)}
                        onRotateStart={(e, box) => startRotate(e, el, box)}
                      />
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

interface ElementRendererProps {
  element: LetterheadElement;
  interactive: boolean;
  pxPerMm: number;
  zoom: number;
  isSelected: boolean;
  isHighlighted: boolean;
  isEditing: boolean;
  onPointerDown: (e: React.PointerEvent) => void;
  onDoubleClick: (e: React.MouseEvent) => void;
  onContentChange: (value: string) => void;
  onStopEditing: () => void;
  onResizeStart: (
    e: React.PointerEvent,
    handle: 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w'
  ) => void;
  onRotateStart: (e: React.PointerEvent, boxEl: HTMLElement | null) => void;
}

const ElementRenderer: React.FC<ElementRendererProps> = ({
  element,
  interactive,
  pxPerMm,
  zoom,
  isSelected,
  isHighlighted,
  isEditing,
  onPointerDown,
  onDoubleClick,
  onContentChange,
  onStopEditing,
  onResizeStart,
  onRotateStart,
}) => {
  const boxRef = useRef<HTMLDivElement>(null);

  const left = element.x * pxPerMm;
  const top = element.y * pxPerMm;
  const width = element.width * pxPerMm;
  const height = element.height * pxPerMm;
  const ptScale = (96 / 72) * zoom;

  const handles: Array<{
    id: 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';
    cls: string;
  }> = [
    { id: 'nw', cls: '-top-1 -left-1 cursor-nwse-resize' },
    { id: 'n', cls: '-top-1 left-1/2 -translate-x-1/2 cursor-ns-resize' },
    { id: 'ne', cls: '-top-1 -right-1 cursor-nesw-resize' },
    { id: 'e', cls: 'top-1/2 -right-1 -translate-y-1/2 cursor-ew-resize' },
    { id: 'se', cls: '-bottom-1 -right-1 cursor-nwse-resize' },
    { id: 's', cls: '-bottom-1 left-1/2 -translate-x-1/2 cursor-ns-resize' },
    { id: 'sw', cls: '-bottom-1 -left-1 cursor-nesw-resize' },
    { id: 'w', cls: 'top-1/2 -left-1 -translate-y-1/2 cursor-ew-resize' },
  ];

  const boxBackground =
    element.shapeStyle?.fill && element.shapeStyle.fill !== 'transparent'
      ? element.shapeStyle.fill
      : undefined;
  const boxBorder =
    element.type !== 'line' &&
    element.shapeStyle &&
    element.shapeStyle.stroke !== 'transparent' &&
    element.shapeStyle.strokeWidth > 0
      ? `${element.shapeStyle.strokeWidth * ptScale}px ${element.shapeStyle.lineStyle} ${element.shapeStyle.stroke}`
      : undefined;

  return (
    <div
      ref={boxRef}
      data-testid={`canvas-element-${element.id}`}
      onPointerDown={onPointerDown}
      onDoubleClick={onDoubleClick}
      onClick={(e) => e.stopPropagation()}
      style={{
        position: 'absolute',
        left,
        top,
        width,
        height,
        zIndex: element.zIndex,
        opacity: element.opacity,
        transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
        transformOrigin: 'center center',
        backgroundColor: element.type !== 'line' ? boxBackground : undefined,
        border: boxBorder,
        borderRadius:
          element.shapeStyle?.borderRadius && element.type !== 'line'
            ? `${element.shapeStyle.borderRadius * pxPerMm}px`
            : undefined,
      }}
      className={`group ${
        interactive && !element.locked && !isEditing ? 'touch-none' : ''
      } ${
        !interactive
          ? 'cursor-default'
          : element.locked
            ? 'cursor-default'
            : 'cursor-move'
      } ${
        isSelected
          ? 'ring-1 ring-[#C45911]'
          : isHighlighted
            ? 'ring-2 ring-amber-500 bg-amber-50/30'
            : interactive
              ? 'hover:ring-1 hover:ring-slate-300'
              : ''
      }`}
    >
      {/* Image Element */}
      {element.type === 'image' && element.imageSrc && (
        <img
          src={element.imageSrc}
          alt={element.name}
          draggable={false}
          referrerPolicy="no-referrer"
          className="w-full h-full pointer-events-none"
          style={{ objectFit: element.objectFit || 'contain' }}
        />
      )}

      {/* Line Element */}
      {element.type === 'line' && element.shapeStyle && (
        <div className="w-full h-full flex items-center pointer-events-none">
          <div
            className="w-full"
            style={{
              borderTopWidth: `${Math.max(1, element.shapeStyle.strokeWidth * ptScale)}px`,
              borderTopStyle: element.shapeStyle.lineStyle,
              borderTopColor: element.shapeStyle.stroke,
            }}
          />
        </div>
      )}

      {/* Text Element */}
      {element.type === 'text' && element.textStyle && (
        <>
          {isEditing ? (
            <textarea
              autoFocus
              aria-label={`Edit ${element.name}`}
              value={element.content || ''}
              onChange={(e) => onContentChange(e.target.value)}
              onBlur={onStopEditing}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  e.preventDefault();
                  onStopEditing();
                }
              }}
              onPointerDown={(e) => e.stopPropagation()}
              className="w-full h-full resize-none outline-none bg-white/95 select-text"
              style={{
                fontFamily: element.textStyle.fontFamily,
                fontSize: `${element.textStyle.fontSize * ptScale}px`,
                fontWeight: element.textStyle.fontWeight,
                fontStyle: element.textStyle.fontStyle,
                letterSpacing: `${element.textStyle.letterSpacing}em`,
                lineHeight: element.textStyle.lineHeight,
                color: element.textStyle.color,
                textAlign: element.textStyle.textAlign,
                paddingTop: `${element.textStyle.paddingTop * pxPerMm}px`,
                paddingRight: `${element.textStyle.paddingRight * pxPerMm}px`,
                paddingBottom: `${element.textStyle.paddingBottom * pxPerMm}px`,
                paddingLeft: `${element.textStyle.paddingLeft * pxPerMm}px`,
              }}
            />
          ) : (
            <div
              className="w-full h-full overflow-hidden whitespace-pre-wrap break-words"
              style={{
                fontFamily: element.textStyle.fontFamily,
                fontSize: `${element.textStyle.fontSize * ptScale}px`,
                fontWeight: element.textStyle.fontWeight,
                fontStyle: element.textStyle.fontStyle,
                letterSpacing: `${element.textStyle.letterSpacing}em`,
                lineHeight: element.textStyle.lineHeight,
                color: element.textStyle.color,
                textAlign: element.textStyle.textAlign,
                paddingTop: `${element.textStyle.paddingTop * pxPerMm}px`,
                paddingRight: `${element.textStyle.paddingRight * pxPerMm}px`,
                paddingBottom: `${element.textStyle.paddingBottom * pxPerMm}px`,
                paddingLeft: `${element.textStyle.paddingLeft * pxPerMm}px`,
              }}
            >
              {element.content || ''}
            </div>
          )}
        </>
      )}

      {/* Selection Handles */}
      {isSelected && !isEditing && !element.locked && (
        <>
          {/* Rotation Handle */}
          <button
            type="button"
            aria-label={`Rotate ${element.name}`}
            onPointerDown={(e) => onRotateStart(e, boxRef.current)}
            className="absolute -top-6 left-1/2 -translate-x-1/2 w-5 h-5 sm:w-4 sm:h-4 rounded-full bg-white border border-[#C45911] text-[#C45911] flex items-center justify-center shadow-sm cursor-grab active:cursor-grabbing touch-none"
          >
            <RotateCw className="w-2.5 h-2.5" />
          </button>

          {/* 8 Resize Handles */}
          {handles.map((h) => (
            <div
              key={h.id}
              onPointerDown={(e) => onResizeStart(e, h.id)}
              className={`absolute w-3 h-3 sm:w-2.5 sm:h-2.5 bg-white border border-[#C45911] rounded-[1px] z-20 touch-none ${h.cls}`}
            />
          ))}
        </>
      )}
    </div>
  );
};
