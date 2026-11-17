import React, { useEffect, useRef, useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  AlignCenter,
  AlignJustify,
  AlignLeft,
  AlignRight,
  AlignVerticalJustifyCenter,
  ArrowDownToLine,
  ArrowUpToLine,
  CheckCircle2,
  Copy,
  Crosshair,
  FileSpreadsheet,
  Info,
  Lock,
  RotateCcw,
  Sliders,
  Sparkles,
  Trash2,
  Unlock,
  Wand2,
  X,
} from 'lucide-react';
import {
  DesignSuggestion,
  LetterheadElement,
  PageSettings,
} from '../types/letterhead';
import { AlignmentTarget } from '../utils/geometry';
import { computeLayoutHealthSummary } from '../utils/designAnalyzer';

export type RightTab = 'audit' | 'properties' | 'page';

interface RightSidebarProps {
  activeTab: RightTab;
  onTabChange: (tab: RightTab) => void;
  suggestions: DesignSuggestion[];
  layoutMode: 'original' | 'refined' | 'custom';
  onApplySuggestion: (suggestion: DesignSuggestion) => void;
  onApplyFullRedesign: () => void;
  onResetOriginal: () => void;
  onLocateElements: (ids: string[]) => void;
  selectedElement: LetterheadElement | null;
  onUpdateElement: (id: string, patch: Partial<LetterheadElement>, recordHistory?: boolean) => void;
  onAlignElement: (id: string, target: AlignmentTarget) => void;
  onReorderElement: (id: string, action: 'forward' | 'backward' | 'front' | 'back') => void;
  onDuplicateElement: (id: string) => void;
  onDeleteElement: (id: string) => void;
  onReplaceImage: (id: string, file: File) => void;
  pageSettings: PageSettings;
  onUpdatePageSettings: (patch: Partial<PageSettings>) => void;
  pageCount: number;
  activePage: number;
  onAddPage: () => void;
  onDeletePage: (pageIndex: number) => void;
  onCloseDrawer?: () => void;
}

const BRAND_SWATCHES = ['#C45911', '#252525', '#D66565', '#1E293B', '#334155', '#64748B', '#FFFFFF'];

const FONT_OPTIONS = [
  'Plus Jakarta Sans',
  'DM Sans',
  'Playfair Display',
  'Source Serif 4',
  'Outfit',
  'Montserrat',
  'Lora',
];

export const RightSidebar: React.FC<RightSidebarProps> = ({
  activeTab,
  onTabChange,
  suggestions,
  layoutMode,
  onApplySuggestion,
  onApplyFullRedesign,
  onResetOriginal,
  onLocateElements,
  selectedElement,
  onUpdateElement,
  onAlignElement,
  onReorderElement,
  onDuplicateElement,
  onDeleteElement,
  onReplaceImage,
  pageSettings,
  onUpdatePageSettings,
  pageCount,
  activePage,
  onAddPage,
  onDeletePage,
  onCloseDrawer,
}) => {
  const replaceImgRef = useRef<HTMLInputElement>(null);
  const summary = computeLayoutHealthSummary(suggestions);

  return (
    <aside
      aria-label="Inspector and Design Assistant"
      className="w-[min(90vw,21rem)] lg:w-80 bg-white border-l border-slate-200 flex flex-col h-full shrink-0"
    >
      {/* Tab Bar */}
      <div className="p-2 border-b border-slate-200 flex items-center gap-1.5">
        <div className="flex-1 grid grid-cols-3 gap-1 p-1 bg-slate-100 rounded-lg" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'audit'}
            onClick={() => onTabChange('audit')}
            className={`flex items-center justify-center gap-1 py-1.5 px-2 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === 'audit'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5 text-[#C45911]" />
            Assistant ({suggestions.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'properties'}
            onClick={() => onTabChange('properties')}
            className={`flex items-center justify-center gap-1 py-1.5 px-2 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === 'properties'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Sliders className="w-3.5 h-3.5" />
            Format
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'page'}
            onClick={() => onTabChange('page')}
            className={`flex items-center justify-center gap-1 py-1.5 px-2 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === 'page'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <FileSpreadsheet className="w-3.5 h-3.5" />
            Page
          </button>
        </div>
        {onCloseDrawer && (
          <button
            type="button"
            onClick={onCloseDrawer}
            aria-label="Close right sidebar"
            className="lg:hidden p-1.5 rounded-md text-slate-500 hover:text-slate-900 hover:bg-slate-100 shrink-0"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto p-3.5 space-y-4">
        {/* TAB 1: DESIGN ASSISTANT */}
        {activeTab === 'audit' && (
          <div className="space-y-4">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-2.5">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h2 className="text-xs font-semibold text-slate-900">
                    Document Analysis
                  </h2>
                  <p className="text-[11px] text-slate-600 mt-0.5 leading-relaxed">
                    {summary.statusText}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 text-[11px] font-mono tabular-nums text-slate-600 pt-1 border-t border-slate-200">
                <span>High: <strong className="text-red-700">{summary.highCount}</strong></span>
                <span aria-hidden="true">·</span>
                <span>Medium: <strong className="text-amber-700">{summary.mediumCount}</strong></span>
                <span aria-hidden="true">·</span>
                <span>Low: <strong className="text-slate-800">{summary.lowCount}</strong></span>
              </div>

              <div className="flex flex-col gap-1.5 pt-1">
                <button
                  type="button"
                  onClick={onApplyFullRedesign}
                  className="w-full flex items-center justify-center gap-1.5 py-2 px-3 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-xs font-semibold transition-colors"
                >
                  <Wand2 className="w-3.5 h-3.5" />
                  Apply Professional Redesign
                </button>
                {layoutMode !== 'original' && (
                  <button
                    type="button"
                    onClick={onResetOriginal}
                    className="w-full flex items-center justify-center gap-1.5 py-1.5 px-3 rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium transition-colors"
                  >
                    <RotateCcw className="w-3 h-3" />
                    Revert to Original PDF Layout
                  </button>
                )}
              </div>
            </div>

            {suggestions.length === 0 ? (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50/60 p-4 text-center space-y-1.5">
                <CheckCircle2 className="w-5 h-5 text-emerald-600 mx-auto" />
                <div className="text-xs font-semibold text-emerald-950">
                  Balanced Layout Verified
                </div>
                <p className="text-[11px] text-emerald-800 leading-relaxed">
                  All Grashy Caterers elements sit cleanly within margins with consistent spacing, readable typography, and no clipping.
                </p>
              </div>
            ) : (
              <div className="space-y-2.5">
                {suggestions.map((s) => (
                  <div
                    key={s.id}
                    className="rounded-lg border border-slate-200 bg-white p-3 space-y-2"
                  >
                    <div className="flex items-start gap-2">
                      {s.severity === 'high' ? (
                        <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                      ) : s.severity === 'medium' ? (
                        <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                      ) : (
                        <Info className="w-4 h-4 text-slate-500 shrink-0 mt-0.5" />
                      )}
                      <div className="min-w-0">
                        <div className="text-[11px] font-medium text-slate-500">
                          {s.category} · {s.severity === 'high' ? 'High priority' : s.severity === 'medium' ? 'Medium priority' : 'Suggestion'}
                        </div>
                        <h3 className="text-xs font-semibold text-slate-900 mt-0.5 leading-snug">
                          {s.title}
                        </h3>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-600 leading-relaxed">
                      {s.explanation}
                    </p>

                    <div className="flex items-center justify-between gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => onLocateElements(s.affectedElementIds)}
                        className="flex items-center gap-1 text-[11px] font-medium text-slate-600 hover:text-slate-900"
                      >
                        <Crosshair className="w-3 h-3" />
                        Locate
                      </button>

                      {s.canAutoFix && (
                        <button
                          type="button"
                          onClick={() => onApplySuggestion(s)}
                          className="px-2.5 py-1 rounded bg-slate-900 hover:bg-slate-800 text-white text-[11px] font-medium transition-colors"
                        >
                          {s.fixLabel}
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: ELEMENT PROPERTIES & TYPOGRAPHY */}
        {activeTab === 'properties' && (
          <>
            {!selectedElement ? (
              <div className="text-center py-10 px-4 border border-dashed border-slate-200 rounded-lg space-y-2">
                <Sliders className="w-5 h-5 text-slate-400 mx-auto" />
                <div className="text-xs font-medium text-slate-700">
                  No Element Selected
                </div>
                <p className="text-[11px] text-slate-500 leading-relaxed">
                  Click any logo, heading, text box, or divider on the A4 canvas—or pick a layer on the left—to adjust coordinates, alignment, and typography.
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                {/* Element Header */}
                <div className="flex items-center justify-between gap-2 pb-2 border-b border-slate-200">
                  <div className="min-w-0">
                    <input
                      type="text"
                      aria-label="Element layer name"
                      value={selectedElement.name}
                      onChange={(e) =>
                        onUpdateElement(selectedElement.id, { name: e.target.value })
                      }
                      className="text-xs font-semibold text-slate-900 bg-transparent border border-transparent hover:border-slate-200 focus:border-[#C45911] rounded px-1 py-0.5 w-full outline-none"
                    />
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      type="button"
                      onClick={() =>
                        onUpdateElement(selectedElement.id, { locked: !selectedElement.locked })
                      }
                      aria-label={selectedElement.locked ? 'Unlock element' : 'Lock element'}
                      className={`p-1.5 rounded border text-xs ${
                        selectedElement.locked
                          ? 'border-orange-200 bg-orange-50 text-[#C45911]'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      {selectedElement.locked ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
                    </button>
                    <button
                      type="button"
                      onClick={() => onDuplicateElement(selectedElement.id)}
                      aria-label="Duplicate element"
                      className="p-1.5 rounded border border-slate-200 text-slate-600 hover:bg-slate-50"
                    >
                      <Copy className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onDeleteElement(selectedElement.id)}
                      aria-label="Delete element"
                      className="p-1.5 rounded border border-slate-200 text-red-600 hover:bg-red-50"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>

                {/* Numerical Coordinates */}
                <div className="space-y-2">
                  <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Position & Size (mm)
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <NumberField
                      label="X (mm)"
                      value={selectedElement.x}
                      step={0.5}
                      onChange={(v) => onUpdateElement(selectedElement.id, { x: v })}
                    />
                    <NumberField
                      label="Y (mm)"
                      value={selectedElement.y}
                      step={0.5}
                      onChange={(v) => onUpdateElement(selectedElement.id, { y: v })}
                    />
                    <NumberField
                      label="Width (mm)"
                      value={selectedElement.width}
                      min={2}
                      step={0.5}
                      onChange={(v) => onUpdateElement(selectedElement.id, { width: Math.max(2, v) })}
                    />
                    <NumberField
                      label="Height (mm)"
                      value={selectedElement.height}
                      min={1}
                      step={0.5}
                      onChange={(v) => onUpdateElement(selectedElement.id, { height: Math.max(1, v) })}
                    />
                    <NumberField
                      label="Rotate (°)"
                      value={selectedElement.rotation}
                      step={1}
                      onChange={(v) => onUpdateElement(selectedElement.id, { rotation: v })}
                    />
                    <NumberField
                      label="Opacity (%)"
                      value={Math.round(selectedElement.opacity * 100)}
                      min={5}
                      max={100}
                      step={5}
                      onChange={(v) =>
                        onUpdateElement(selectedElement.id, {
                          opacity: Math.min(1, Math.max(0.05, v / 100)),
                        })
                      }
                    />
                  </div>
                </div>

                {/* Page Alignment & Layer Order */}
                <div className="space-y-2 pt-2 border-t border-slate-200">
                  <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Align to Margins & Layer Order
                  </div>
                  <div className="grid grid-cols-6 gap-1">
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'left')}
                      aria-label="Align left margin"
                      title="Align left margin"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <AlignLeft className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'center-x')}
                      aria-label="Center horizontally"
                      title="Center horizontally"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <AlignCenter className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'right')}
                      aria-label="Align right margin"
                      title="Align right margin"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <AlignRight className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'top')}
                      aria-label="Align top margin"
                      title="Align top margin"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <ArrowUpToLine className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'middle-y')}
                      aria-label="Center vertically"
                      title="Center vertically"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <AlignVerticalJustifyCenter className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onAlignElement(selectedElement.id, 'bottom')}
                      aria-label="Align bottom margin"
                      title="Align bottom margin"
                      className="p-1.5 rounded border border-slate-200 hover:bg-slate-50 flex justify-center text-slate-700"
                    >
                      <ArrowDownToLine className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <div className="grid grid-cols-4 gap-1">
                    <button
                      type="button"
                      onClick={() => onReorderElement(selectedElement.id, 'front')}
                      aria-label="Bring to front"
                      className="py-1 px-1.5 rounded border border-slate-200 hover:bg-slate-50 text-[11px] font-medium text-slate-700"
                    >
                      Front
                    </button>
                    <button
                      type="button"
                      onClick={() => onReorderElement(selectedElement.id, 'forward')}
                      aria-label="Bring forward"
                      className="py-1 px-1.5 rounded border border-slate-200 hover:bg-slate-50 text-[11px] font-medium text-slate-700"
                    >
                      Forward
                    </button>
                    <button
                      type="button"
                      onClick={() => onReorderElement(selectedElement.id, 'backward')}
                      aria-label="Send backward"
                      className="py-1 px-1.5 rounded border border-slate-200 hover:bg-slate-50 text-[11px] font-medium text-slate-700"
                    >
                      Backw.
                    </button>
                    <button
                      type="button"
                      onClick={() => onReorderElement(selectedElement.id, 'back')}
                      aria-label="Send to back"
                      className="py-1 px-1.5 rounded border border-slate-200 hover:bg-slate-50 text-[11px] font-medium text-slate-700"
                    >
                      Back
                    </button>
                  </div>
                </div>

                {/* Text & Typography Controls */}
                {selectedElement.type === 'text' && selectedElement.textStyle && (
                  <div className="space-y-3 pt-2 border-t border-slate-200">
                    <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      Typography & Copy
                    </div>

                    <div>
                      <label htmlFor="el-content-edit" className="block text-[11px] font-medium text-slate-600 mb-1">
                        Text Content
                      </label>
                      <textarea
                        id="el-content-edit"
                        rows={4}
                        value={selectedElement.content || ''}
                        onChange={(e) =>
                          onUpdateElement(selectedElement.id, { content: e.target.value })
                        }
                        className="w-full text-xs p-2 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-slate-50 focus:bg-white"
                      />
                    </div>

                    <div>
                      <label htmlFor="el-font-family" className="block text-[11px] font-medium text-slate-600 mb-1">
                        Font Family
                      </label>
                      <select
                        id="el-font-family"
                        value={selectedElement.textStyle.fontFamily}
                        onChange={(e) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: { ...selectedElement.textStyle!, fontFamily: e.target.value },
                          })
                        }
                        className="w-full text-xs p-1.5 rounded-md border border-slate-200 bg-white outline-none"
                      >
                        {FONT_OPTIONS.map((f) => (
                          <option key={f} value={f}>
                            {f}
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <NumberField
                        label="Size (pt)"
                        value={selectedElement.textStyle.fontSize}
                        min={6}
                        max={72}
                        step={0.5}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: { ...selectedElement.textStyle!, fontSize: Math.max(6, v) },
                          })
                        }
                      />
                      <div>
                        <label htmlFor="el-font-weight" className="block text-[11px] font-medium text-slate-600 mb-1">
                          Weight
                        </label>
                        <select
                          id="el-font-weight"
                          value={selectedElement.textStyle.fontWeight}
                          onChange={(e) =>
                            onUpdateElement(selectedElement.id, {
                              textStyle: {
                                ...selectedElement.textStyle!,
                                fontWeight: Number(e.target.value),
                              },
                            })
                          }
                          className="w-full text-xs p-1.5 rounded-md border border-slate-200 bg-white outline-none"
                        >
                          <option value={400}>Regular (400)</option>
                          <option value={500}>Medium (500)</option>
                          <option value={600}>SemiBold (600)</option>
                          <option value={700}>Bold (700)</option>
                        </select>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <NumberField
                        label="Line Height"
                        value={selectedElement.textStyle.lineHeight}
                        min={0.9}
                        max={3}
                        step={0.05}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: { ...selectedElement.textStyle!, lineHeight: Math.max(0.9, v) },
                          })
                        }
                      />
                      <NumberField
                        label="Tracking (em)"
                        value={selectedElement.textStyle.letterSpacing}
                        min={-0.05}
                        max={0.4}
                        step={0.01}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: { ...selectedElement.textStyle!, letterSpacing: v },
                          })
                        }
                      />
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <NumberField
                        label="Para Space (pt)"
                        value={selectedElement.textStyle.paragraphSpacing}
                        min={0}
                        max={24}
                        step={1}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: { ...selectedElement.textStyle!, paragraphSpacing: Math.max(0, v) },
                          })
                        }
                      />
                      <NumberField
                        label="Padding (mm)"
                        value={selectedElement.textStyle.paddingLeft}
                        min={0}
                        max={20}
                        step={0.5}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: {
                              ...selectedElement.textStyle!,
                              paddingTop: v,
                              paddingRight: v,
                              paddingBottom: v,
                              paddingLeft: v,
                            },
                          })
                        }
                      />
                    </div>

                    {/* Text Alignment & Italic */}
                    <div className="flex items-center gap-1">
                      {(['left', 'center', 'right', 'justify'] as const).map((align) => (
                        <button
                          key={align}
                          type="button"
                          onClick={() =>
                            onUpdateElement(selectedElement.id, {
                              textStyle: { ...selectedElement.textStyle!, textAlign: align },
                            })
                          }
                          aria-label={`Align text ${align}`}
                          className={`flex-1 py-1.5 rounded border flex justify-center ${
                            selectedElement.textStyle?.textAlign === align
                              ? 'border-[#C45911] bg-orange-50 text-[#C45911]'
                              : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                          }`}
                        >
                          {align === 'left' && <AlignLeft className="w-3.5 h-3.5" />}
                          {align === 'center' && <AlignCenter className="w-3.5 h-3.5" />}
                          {align === 'right' && <AlignRight className="w-3.5 h-3.5" />}
                          {align === 'justify' && <AlignJustify className="w-3.5 h-3.5" />}
                        </button>
                      ))}
                      <button
                        type="button"
                        onClick={() =>
                          onUpdateElement(selectedElement.id, {
                            textStyle: {
                              ...selectedElement.textStyle!,
                              fontStyle:
                                selectedElement.textStyle!.fontStyle === 'italic' ? 'normal' : 'italic',
                            },
                          })
                        }
                        aria-label="Toggle italic"
                        className={`px-2.5 py-1 rounded border text-xs font-serif italic font-semibold ${
                          selectedElement.textStyle.fontStyle === 'italic'
                            ? 'border-[#C45911] bg-orange-50 text-[#C45911]'
                            : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                        }`}
                      >
                        I
                      </button>
                    </div>

                    {/* Text Color & Brand Swatches */}
                    <div>
                      <div className="flex items-center justify-between mb-1.5">
                        <label htmlFor="el-text-color" className="text-[11px] font-medium text-slate-600">
                          Text Color
                        </label>
                        <input
                          id="el-text-color"
                          type="color"
                          value={selectedElement.textStyle.color}
                          onChange={(e) =>
                            onUpdateElement(selectedElement.id, {
                              textStyle: { ...selectedElement.textStyle!, color: e.target.value },
                            })
                          }
                          className="w-6 h-5 rounded border border-slate-200 cursor-pointer"
                        />
                      </div>
                      <div className="flex items-center gap-1.5">
                        {BRAND_SWATCHES.filter((c) => c !== '#FFFFFF').map((hex) => (
                          <button
                            key={hex}
                            type="button"
                            onClick={() =>
                              onUpdateElement(selectedElement.id, {
                                textStyle: { ...selectedElement.textStyle!, color: hex },
                              })
                            }
                            aria-label={`Set text color ${hex}`}
                            className="w-5 h-5 rounded border border-slate-300"
                            style={{ backgroundColor: hex }}
                          />
                        ))}
                      </div>
                    </div>
                  </div>
                )}

                {/* Shape / Line / Container Box Styling */}
                {(selectedElement.type === 'line' ||
                  selectedElement.type === 'shape' ||
                  selectedElement.shapeStyle) && (
                  <div className="space-y-2.5 pt-2 border-t border-slate-200">
                    <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      Border & Fill
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label htmlFor="el-stroke-color" className="block text-[11px] font-medium text-slate-600 mb-1">
                          Stroke Color
                        </label>
                        <input
                          id="el-stroke-color"
                          type="color"
                          value={
                            selectedElement.shapeStyle?.stroke &&
                            selectedElement.shapeStyle.stroke !== 'transparent'
                              ? selectedElement.shapeStyle.stroke
                              : '#C45911'
                          }
                          onChange={(e) =>
                            onUpdateElement(selectedElement.id, {
                              shapeStyle: {
                                fill: selectedElement.shapeStyle?.fill || 'transparent',
                                strokeWidth: selectedElement.shapeStyle?.strokeWidth ?? 1,
                                borderRadius: selectedElement.shapeStyle?.borderRadius ?? 0,
                                lineStyle: selectedElement.shapeStyle?.lineStyle || 'solid',
                                stroke: e.target.value,
                              },
                            })
                          }
                          className="w-full h-7 rounded border border-slate-200 cursor-pointer"
                        />
                      </div>
                      <NumberField
                        label="Stroke (pt)"
                        value={selectedElement.shapeStyle?.strokeWidth ?? 1}
                        min={0}
                        max={12}
                        step={0.25}
                        onChange={(v) =>
                          onUpdateElement(selectedElement.id, {
                            shapeStyle: {
                              fill: selectedElement.shapeStyle?.fill || 'transparent',
                              stroke: selectedElement.shapeStyle?.stroke || '#C45911',
                              borderRadius: selectedElement.shapeStyle?.borderRadius ?? 0,
                              lineStyle: selectedElement.shapeStyle?.lineStyle || 'solid',
                              strokeWidth: Math.max(0, v),
                            },
                          })
                        }
                      />
                    </div>

                    {selectedElement.type !== 'line' && (
                      <div className="grid grid-cols-2 gap-2">
                        <div>
                          <div className="flex items-center justify-between mb-1">
                            <label htmlFor="el-fill-color" className="text-[11px] font-medium text-slate-600">
                              Fill Color
                            </label>
                            <button
                              type="button"
                              onClick={() =>
                                onUpdateElement(selectedElement.id, {
                                  shapeStyle: {
                                    fill: 'transparent',
                                    stroke: selectedElement.shapeStyle?.stroke || '#C45911',
                                    strokeWidth: selectedElement.shapeStyle?.strokeWidth ?? 1,
                                    borderRadius: selectedElement.shapeStyle?.borderRadius ?? 0,
                                    lineStyle: selectedElement.shapeStyle?.lineStyle || 'solid',
                                  },
                                })
                              }
                              className="text-[10px] font-medium text-slate-500 hover:text-slate-900"
                            >
                              Clear
                            </button>
                          </div>
                          <input
                            id="el-fill-color"
                            type="color"
                            value={
                              selectedElement.shapeStyle?.fill &&
                              selectedElement.shapeStyle.fill !== 'transparent'
                                ? selectedElement.shapeStyle.fill
                                : '#FFF7ED'
                            }
                            onChange={(e) =>
                              onUpdateElement(selectedElement.id, {
                                shapeStyle: {
                                  stroke: selectedElement.shapeStyle?.stroke || '#FDBA74',
                                  strokeWidth: selectedElement.shapeStyle?.strokeWidth ?? 0.75,
                                  borderRadius: selectedElement.shapeStyle?.borderRadius ?? 2,
                                  lineStyle: selectedElement.shapeStyle?.lineStyle || 'solid',
                                  fill: e.target.value,
                                },
                              })
                            }
                            className="w-full h-7 rounded border border-slate-200 cursor-pointer"
                          />
                        </div>
                        <NumberField
                          label="Radius (mm)"
                          value={selectedElement.shapeStyle?.borderRadius ?? 0}
                          min={0}
                          max={20}
                          step={0.5}
                          onChange={(v) =>
                            onUpdateElement(selectedElement.id, {
                              shapeStyle: {
                                fill: selectedElement.shapeStyle?.fill || 'transparent',
                                stroke: selectedElement.shapeStyle?.stroke || '#C45911',
                                strokeWidth: selectedElement.shapeStyle?.strokeWidth ?? 1,
                                lineStyle: selectedElement.shapeStyle?.lineStyle || 'solid',
                                borderRadius: Math.max(0, v),
                              },
                            })
                          }
                        />
                      </div>
                    )}

                    <div>
                      <label htmlFor="el-line-style" className="block text-[11px] font-medium text-slate-600 mb-1">
                        Line Style
                      </label>
                      <select
                        id="el-line-style"
                        value={selectedElement.shapeStyle?.lineStyle || 'solid'}
                        onChange={(e) =>
                          onUpdateElement(selectedElement.id, {
                            shapeStyle: {
                              fill: selectedElement.shapeStyle?.fill || 'transparent',
                              stroke: selectedElement.shapeStyle?.stroke || '#C45911',
                              strokeWidth: selectedElement.shapeStyle?.strokeWidth ?? 1,
                              borderRadius: selectedElement.shapeStyle?.borderRadius ?? 0,
                              lineStyle: e.target.value as 'solid' | 'dashed' | 'dotted',
                            },
                          })
                        }
                        className="w-full text-xs p-1.5 rounded-md border border-slate-200 bg-white outline-none"
                      >
                        <option value="solid">Solid</option>
                        <option value="dashed">Dashed</option>
                        <option value="dotted">Dotted</option>
                      </select>
                    </div>
                  </div>
                )}

                {/* Image Controls */}
                {selectedElement.type === 'image' && (
                  <div className="space-y-2.5 pt-2 border-t border-slate-200">
                    <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                      Image Options
                    </div>
                    <input
                      ref={replaceImgRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) {
                          onReplaceImage(selectedElement.id, f);
                          e.target.value = '';
                        }
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => replaceImgRef.current?.click()}
                      className="w-full py-1.5 px-3 rounded border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-700"
                    >
                      Replace Image Asset
                    </button>
                  </div>
                )}
              </div>
            )}
          </>
        )}

        {/* TAB 3: PAGE & MARGINS */}
        {activeTab === 'page' && (
          <div className="space-y-4">
            <div className="space-y-2">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                A4 Page Margins (mm)
              </h2>
              <div className="grid grid-cols-2 gap-2">
                <NumberField
                  label="Top (mm)"
                  value={pageSettings.margins.top}
                  min={0}
                  max={60}
                  step={1}
                  onChange={(v) =>
                    onUpdatePageSettings({
                      margins: { ...pageSettings.margins, top: Math.max(0, v) },
                    })
                  }
                />
                <NumberField
                  label="Bottom (mm)"
                  value={pageSettings.margins.bottom}
                  min={0}
                  max={60}
                  step={1}
                  onChange={(v) =>
                    onUpdatePageSettings({
                      margins: { ...pageSettings.margins, bottom: Math.max(0, v) },
                    })
                  }
                />
                <NumberField
                  label="Left (mm)"
                  value={pageSettings.margins.left}
                  min={0}
                  max={50}
                  step={1}
                  onChange={(v) =>
                    onUpdatePageSettings({
                      margins: { ...pageSettings.margins, left: Math.max(0, v) },
                    })
                  }
                />
                <NumberField
                  label="Right (mm)"
                  value={pageSettings.margins.right}
                  min={0}
                  max={50}
                  step={1}
                  onChange={(v) =>
                    onUpdatePageSettings({
                      margins: { ...pageSettings.margins, right: Math.max(0, v) },
                    })
                  }
                />
              </div>
            </div>

            <div className="space-y-2 pt-3 border-t border-slate-200">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Guides & Snapping
              </h2>
              <label className="flex items-center justify-between text-xs text-slate-700 py-1 cursor-pointer">
                <span>Show millimeter rulers</span>
                <input
                  type="checkbox"
                  checked={pageSettings.showRulers}
                  onChange={(e) => onUpdatePageSettings({ showRulers: e.target.checked })}
                  className="accent-[#C45911]"
                />
              </label>
              <label className="flex items-center justify-between text-xs text-slate-700 py-1 cursor-pointer">
                <span>Show printable margins</span>
                <input
                  type="checkbox"
                  checked={pageSettings.showMargins}
                  onChange={(e) => onUpdatePageSettings({ showMargins: e.target.checked })}
                  className="accent-[#C45911]"
                />
              </label>
              <label className="flex items-center justify-between text-xs text-slate-700 py-1 cursor-pointer">
                <span>Snap to alignment guides</span>
                <input
                  type="checkbox"
                  checked={pageSettings.snapToGuides}
                  onChange={(e) => onUpdatePageSettings({ snapToGuides: e.target.checked })}
                  className="accent-[#C45911]"
                />
              </label>
              <label className="flex items-center justify-between text-xs text-slate-700 py-1 cursor-pointer">
                <span>Snap to grid ({pageSettings.gridSizeMm}mm)</span>
                <input
                  type="checkbox"
                  checked={pageSettings.snapToGrid}
                  onChange={(e) => onUpdatePageSettings({ snapToGrid: e.target.checked })}
                  className="accent-[#C45911]"
                />
              </label>
            </div>

            <div className="space-y-2 pt-3 border-t border-slate-200">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Multi-Page Document ({pageCount} page{pageCount > 1 ? 's' : ''})
              </h2>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={onAddPage}
                  className="flex-1 py-1.5 px-2.5 rounded border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-700"
                >
                  Add Continuation Page
                </button>
                {pageCount > 1 && (
                  <button
                    type="button"
                    onClick={() => onDeletePage(activePage)}
                    className="py-1.5 px-2.5 rounded border border-red-200 text-xs font-medium text-red-600 hover:bg-red-50"
                  >
                    Remove Page
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};

interface NumberFieldProps {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onChange: (value: number) => void;
}

const NumberField: React.FC<NumberFieldProps> = ({
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
}) => {
  const id = `num-${label.toLowerCase().replace(/[^a-z0-9]/g, '-')}`;
  const formatted = Number.isFinite(value) ? String(Number(value.toFixed(2))) : '0';
  const [draft, setDraft] = useState(formatted);
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) {
      setDraft(formatted);
    }
  }, [formatted, focused]);

  return (
    <div>
      <label htmlFor={id} className="block text-[11px] font-medium text-slate-600 mb-1">
        {label}
      </label>
      <input
        id={id}
        type="number"
        value={focused ? draft : formatted}
        min={min}
        max={max}
        step={step}
        onFocus={() => {
          setFocused(true);
          setDraft(formatted);
        }}
        onChange={(e) => {
          const next = e.target.value;
          setDraft(next);
          const num = parseFloat(next);
          if (!Number.isNaN(num)) onChange(num);
        }}
        onBlur={() => {
          setFocused(false);
          const num = parseFloat(draft);
          if (!Number.isNaN(num)) {
            onChange(num);
          } else {
            setDraft(formatted);
          }
        }}
        className="w-full text-xs font-mono tabular-nums px-2 py-1 rounded-md border border-slate-200 focus:border-[#C45911] outline-none bg-white"
      />
    </div>
  );
};
