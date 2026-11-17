import React, { useRef } from 'react';
import {
  Layers,
  PlusSquare,
  Eye,
  EyeOff,
  Lock,
  Unlock,
  Copy,
  Trash2,
  ArrowUp,
  ArrowDown,
  RotateCcw,
  Type,
  Heading1,
  Minus,
  Square,
  ImagePlus,
  X,
} from 'lucide-react';
import { FontPairing, LetterheadElement } from '../types/letterhead';
import { FONT_PAIRINGS } from '../data/grashyInitialProject';

export type LeftTab = 'layers' | 'elements';

interface LeftSidebarProps {
  activeTab: LeftTab;
  onTabChange: (tab: LeftTab) => void;
  elements: LetterheadElement[];
  deletedElements: LetterheadElement[];
  activePage: number;
  selectedId: string | null;
  onSelectElement: (id: string) => void;
  onToggleVisibility: (id: string) => void;
  onToggleLock: (id: string) => void;
  onDuplicateElement: (id: string) => void;
  onDeleteElement: (id: string) => void;
  onRestoreElement: (id: string) => void;
  onReorderElement: (id: string, action: 'forward' | 'backward') => void;
  onAddPresetElement: (kind: 'heading' | 'subtitle' | 'body' | 'line' | 'box') => void;
  onUploadImage: (file: File) => void;
  onApplyFontPairing: (pairing: FontPairing) => void;
  onCloseDrawer?: () => void;
}

export const LeftSidebar: React.FC<LeftSidebarProps> = ({
  activeTab,
  onTabChange,
  elements,
  deletedElements,
  activePage,
  selectedId,
  onSelectElement,
  onToggleVisibility,
  onToggleLock,
  onDuplicateElement,
  onDeleteElement,
  onRestoreElement,
  onReorderElement,
  onAddPresetElement,
  onUploadImage,
  onApplyFontPairing,
  onCloseDrawer,
}) => {
  const imgInputRef = useRef<HTMLInputElement>(null);

  const pageElements = elements
    .filter((el) => el.pageIndex === activePage)
    .sort((a, b) => b.zIndex - a.zIndex);

  return (
    <aside
      aria-label="Visual Editor Layers and Elements"
      className="w-[min(86vw,19rem)] lg:w-72 bg-white border-r border-slate-200 flex flex-col h-full shrink-0"
    >
      {/* Tab Switcher */}
      <div className="p-2 border-b border-slate-200 flex items-center gap-1.5">
        <div className="flex-1 grid grid-cols-2 gap-1 p-1 bg-slate-100 rounded-lg" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'layers'}
            onClick={() => onTabChange('layers')}
            className={`flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === 'layers'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Layers className="w-3.5 h-3.5 text-[#C45911]" />
            Layers
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'elements'}
            onClick={() => onTabChange('elements')}
            className={`flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
              activeTab === 'elements'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <PlusSquare className="w-3.5 h-3.5" />
            Add Elements
          </button>
        </div>
        {onCloseDrawer && (
          <button
            type="button"
            onClick={onCloseDrawer}
            aria-label="Close left sidebar"
            className="lg:hidden p-1.5 rounded-md text-slate-500 hover:text-slate-900 hover:bg-slate-100 shrink-0"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Tab Body */}
      <div className="flex-1 overflow-y-auto p-3 space-y-4">
        {/* LAYERS TAB */}
        {activeTab === 'layers' && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Page {activePage + 1} Layers ({pageElements.length})
              </h2>
            </div>

            {pageElements.length === 0 ? (
              <p className="text-xs text-slate-500 py-6 text-center border border-dashed border-slate-200 rounded-lg">
                No elements on this page yet. Add items from Add Elements.
              </p>
            ) : (
              <ul className="space-y-1.5">
                {pageElements.map((el) => {
                  const isSelected = el.id === selectedId;
                  return (
                    <li
                      key={el.id}
                      className={`rounded-lg border transition-colors ${
                        isSelected
                          ? 'border-[#C45911] bg-orange-50/50'
                          : 'border-slate-200 bg-white hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center justify-between px-2.5 py-2 gap-1.5">
                        <button
                          type="button"
                          onClick={() => onSelectElement(el.id)}
                          className="flex-1 text-left min-w-0"
                        >
                          <div className="text-xs font-medium text-slate-900 truncate">
                            {el.name}
                          </div>
                          <div className="text-[11px] font-mono tabular-nums text-slate-500">
                            {el.x.toFixed(0)}mm, {el.y.toFixed(0)}mm · {el.type}
                          </div>
                        </button>

                        <div className="flex items-center gap-0.5 shrink-0">
                          <button
                            type="button"
                            onClick={() => onReorderElement(el.id, 'forward')}
                            aria-label={`Bring ${el.name} forward`}
                            title="Bring forward"
                            className="p-1 rounded text-slate-500 hover:text-slate-900 hover:bg-slate-100"
                          >
                            <ArrowUp className="w-3 h-3" />
                          </button>
                          <button
                            type="button"
                            onClick={() => onReorderElement(el.id, 'backward')}
                            aria-label={`Send ${el.name} backward`}
                            title="Send backward"
                            className="p-1 rounded text-slate-500 hover:text-slate-900 hover:bg-slate-100"
                          >
                            <ArrowDown className="w-3 h-3" />
                          </button>
                          <button
                            type="button"
                            onClick={() => onToggleVisibility(el.id)}
                            aria-label={el.visible ? `Hide ${el.name}` : `Show ${el.name}`}
                            title={el.visible ? 'Hide layer' : 'Show layer'}
                            className="p-1 rounded text-slate-500 hover:text-slate-900 hover:bg-slate-100"
                          >
                            {el.visible ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                          </button>
                          <button
                            type="button"
                            onClick={() => onToggleLock(el.id)}
                            aria-label={el.locked ? `Unlock ${el.name}` : `Lock ${el.name}`}
                            title={el.locked ? 'Unlock' : 'Lock'}
                            className={`p-1 rounded hover:bg-slate-100 ${
                              el.locked ? 'text-[#C45911]' : 'text-slate-500 hover:text-slate-900'
                            }`}
                          >
                            {el.locked ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
                          </button>
                        </div>
                      </div>

                      {isSelected && (
                        <div className="flex items-center justify-end gap-2 px-2.5 pb-2 pt-1 border-t border-orange-200/60">
                          <button
                            type="button"
                            onClick={() => onDuplicateElement(el.id)}
                            className="flex items-center gap-1 text-[11px] font-medium text-slate-700 hover:text-slate-900"
                          >
                            <Copy className="w-3 h-3" />
                            Duplicate
                          </button>
                          <button
                            type="button"
                            onClick={() => onDeleteElement(el.id)}
                            className="flex items-center gap-1 text-[11px] font-medium text-red-600 hover:text-red-700"
                          >
                            <Trash2 className="w-3 h-3" />
                            Delete
                          </button>
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}

            {/* Deleted Elements / Restore */}
            {deletedElements.length > 0 && (
              <div className="pt-3 border-t border-slate-200 space-y-2">
                <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  Removed Elements ({deletedElements.length})
                </h3>
                <ul className="space-y-1">
                  {deletedElements.map((del) => (
                    <li
                      key={del.id}
                      className="flex items-center justify-between px-2.5 py-1.5 rounded-md bg-slate-50 border border-slate-200"
                    >
                      <span className="text-xs text-slate-700 truncate">{del.name}</span>
                      <button
                        type="button"
                        onClick={() => onRestoreElement(del.id)}
                        className="flex items-center gap-1 text-[11px] font-medium text-[#C45911] hover:underline shrink-0"
                      >
                        <RotateCcw className="w-3 h-3" />
                        Restore
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* ADD ELEMENTS TAB */}
        {activeTab === 'elements' && (
          <div className="space-y-5">
            <div>
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2.5">
                Insert Components
              </h2>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => onAddPresetElement('heading')}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <Heading1 className="w-4 h-4 text-[#C45911]" />
                  <span className="text-xs font-medium text-slate-900">Heading</span>
                </button>
                <button
                  type="button"
                  onClick={() => onAddPresetElement('subtitle')}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <Type className="w-4 h-4 text-slate-700" />
                  <span className="text-xs font-medium text-slate-900">Subtitle</span>
                </button>
                <button
                  type="button"
                  onClick={() => onAddPresetElement('body')}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <Type className="w-4 h-4 text-slate-600" />
                  <span className="text-xs font-medium text-slate-900">Text Box</span>
                </button>
                <button
                  type="button"
                  onClick={() => onAddPresetElement('line')}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <Minus className="w-4 h-4 text-[#C45911]" />
                  <span className="text-xs font-medium text-slate-900">Divider Line</span>
                </button>
                <button
                  type="button"
                  onClick={() => onAddPresetElement('box')}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <Square className="w-4 h-4 text-slate-700" />
                  <span className="text-xs font-medium text-slate-900">Shape Box</span>
                </button>
                <button
                  type="button"
                  onClick={() => imgInputRef.current?.click()}
                  className="flex flex-col items-start gap-1.5 p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 text-left transition-colors"
                >
                  <ImagePlus className="w-4 h-4 text-slate-700" />
                  <span className="text-xs font-medium text-slate-900">Image / Logo</span>
                </button>
                <input
                  ref={imgInputRef}
                  type="file"
                  accept="image/*"
                  className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) {
                      onUploadImage(f);
                      e.target.value = '';
                    }
                  }}
                />
              </div>
            </div>

            <div className="pt-3 border-t border-slate-200">
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2.5">
                Professional Font Pairings
              </h2>
              <div className="space-y-2">
                {FONT_PAIRINGS.map((pairing) => (
                  <button
                    key={pairing.id}
                    type="button"
                    onClick={() => onApplyFontPairing(pairing)}
                    className="w-full text-left p-2.5 rounded-lg border border-slate-200 hover:border-[#C45911] hover:bg-orange-50/40 transition-colors"
                  >
                    <div
                      className="text-xs font-semibold text-slate-900"
                      style={{ fontFamily: pairing.displayFont }}
                    >
                      {pairing.name}
                    </div>
                    <div className="text-[11px] text-slate-500 mt-0.5">
                      {pairing.description}
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};
