import React, { useRef } from 'react';
import {
  Undo2,
  Redo2,
  ZoomIn,
  ZoomOut,
  Grid3X3,
  Magnet,
  SquareDashedBottom,
  Upload,
  Save,
  FileDown,
  FileText,
  Plus,
  PanelLeft,
  SlidersHorizontal,
  ClipboardList,
  PenTool,
  Cloud,
  CloudOff,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';
import { EditorMode } from '../types/letterhead';
import { SyncStatusSnapshot } from '../types/sync';
import { PWAInstallButton } from './PWAInstallButton';

interface TopToolbarProps {
  editorMode: EditorMode;
  onModeChange: (mode: EditorMode) => void;
  projectName: string;
  onProjectNameChange: (name: string) => void;
  canUndo: boolean;
  canRedo: boolean;
  onUndo: () => void;
  onRedo: () => void;
  zoom: number;
  onZoomChange: (zoom: number) => void;
  showMargins: boolean;
  onToggleMargins: () => void;
  showGrid: boolean;
  onToggleGrid: () => void;
  snapToGuides: boolean;
  onToggleSnap: () => void;
  pageCount: number;
  activePage: number;
  onActivePageChange: (page: number) => void;
  onAddPage: () => void;
  onImportFile: (file: File) => void;
  onSaveProject: () => void;
  onExportPdf: () => void;
  onExportDocx: () => void;
  isExporting: boolean;
  leftSidebarOpen: boolean;
  rightSidebarOpen: boolean;
  onToggleLeftDrawer: () => void;
  onToggleRightDrawer: () => void;
  syncStatus: SyncStatusSnapshot;
  onOpenSyncCenter: () => void;
}

export const TopToolbar: React.FC<TopToolbarProps> = ({
  editorMode,
  onModeChange,
  projectName,
  onProjectNameChange,
  canUndo,
  canRedo,
  onUndo,
  onRedo,
  zoom,
  onZoomChange,
  showMargins,
  onToggleMargins,
  showGrid,
  onToggleGrid,
  snapToGuides,
  onToggleSnap,
  pageCount,
  activePage,
  onActivePageChange,
  onAddPage,
  onImportFile,
  onSaveProject,
  onExportPdf,
  onExportDocx,
  isExporting,
  leftSidebarOpen,
  rightSidebarOpen,
  onToggleLeftDrawer,
  onToggleRightDrawer,
  syncStatus,
  onOpenSyncCenter,
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const syncBadgeClasses =
    syncStatus.state === 'synced'
      ? 'border-emerald-200 bg-emerald-50/80 text-emerald-800 hover:bg-emerald-100/70'
      : syncStatus.state === 'offline'
        ? 'border-slate-200 bg-slate-100 text-slate-700 hover:bg-slate-200/70'
        : syncStatus.state === 'syncing'
          ? 'border-sky-200 bg-sky-50 text-sky-800 hover:bg-sky-100/70'
          : syncStatus.state === 'pending'
            ? 'border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100/70'
            : 'border-red-200 bg-red-50 text-red-800 hover:bg-red-100/70';

  const syncDotClass =
    syncStatus.state === 'synced'
      ? 'bg-emerald-500'
      : syncStatus.state === 'offline'
        ? 'bg-slate-400'
        : syncStatus.state === 'syncing'
          ? 'bg-sky-500 animate-pulse'
          : syncStatus.state === 'pending'
            ? 'bg-amber-500'
            : 'bg-red-500';

  return (
    <header className="h-14 bg-white border-b border-slate-200 px-1.5 sm:px-3 lg:px-4 flex items-center justify-between gap-1 sm:gap-2.5 shrink-0 z-30">
      {/* Left: Mobile Drawer Toggle + Brand & Document Title */}
      <div className="flex items-center gap-1 sm:gap-2 min-w-0 shrink-0">
        <button
          type="button"
          onClick={onToggleLeftDrawer}
          aria-expanded={leftSidebarOpen}
          aria-label={editorMode === 'form' ? 'Toggle document form panel' : 'Toggle elements and layers panel'}
          title={editorMode === 'form' ? 'Toggle Form Panel' : 'Toggle Layers Panel'}
          className={`p-1.5 sm:p-2 rounded-md border shrink-0 transition-colors ${
            leftSidebarOpen
              ? 'border-orange-200 bg-orange-50 text-[#C45911]'
              : 'border-slate-200 text-slate-700 hover:bg-slate-50'
          }`}
        >
          <PanelLeft className="w-4 h-4" />
        </button>

        <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
          <img
            src="/assets/grashy-logo.png"
            alt="Grashy Caterers"
            className="h-5 sm:h-7 w-auto object-contain"
          />
          <span className="hidden sm:inline font-display font-bold text-sm tracking-tight text-slate-900 whitespace-nowrap">
            Grashy Studio
          </span>
        </div>

        <span className="hidden 2xl:inline text-slate-300" aria-hidden="true">
          /
        </span>

        <label htmlFor="project-name-input" className="sr-only">
          Document Name
        </label>
        <input
          id="project-name-input"
          type="text"
          value={projectName}
          onChange={(e) => onProjectNameChange(e.target.value)}
          className="hidden 2xl:block text-xs font-medium text-slate-700 bg-slate-50 hover:bg-slate-100 focus:bg-white border border-transparent focus:border-slate-300 rounded px-2 py-1 w-44 truncate outline-none transition-colors"
        />
      </div>

      {/* Mode Switcher + Editing Controls */}
      <div className="flex items-center gap-2">
        {/* Two-Mode Segmented Control */}
        <div
          role="tablist"
          aria-label="Editing mode"
          className="flex items-center bg-slate-100 p-0.5 rounded-lg border border-slate-200 shrink-0"
        >
          <button
            type="button"
            role="tab"
            aria-selected={editorMode === 'form'}
            data-testid="mode-tab-form"
            onClick={() => onModeChange('form')}
            className={`flex items-center gap-1 sm:gap-1.5 px-1.5 sm:px-2.5 py-1 rounded-md text-[11px] sm:text-xs font-semibold whitespace-nowrap shrink-0 transition-colors ${
              editorMode === 'form'
                ? 'bg-white text-[#C45911] shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ClipboardList className="w-3.5 h-3.5 hidden sm:inline" />
            <span className="sm:hidden">Form</span>
            <span className="hidden sm:inline">Form Generator</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={editorMode === 'visual'}
            data-testid="mode-tab-visual"
            onClick={() => onModeChange('visual')}
            className={`flex items-center gap-1 sm:gap-1.5 px-1.5 sm:px-2.5 py-1 rounded-md text-[11px] sm:text-xs font-semibold whitespace-nowrap shrink-0 transition-colors ${
              editorMode === 'visual'
                ? 'bg-white text-[#C45911] shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <PenTool className="w-3.5 h-3.5 hidden sm:inline" />
            <span className="sm:hidden">Visual</span>
            <span className="hidden sm:inline">Visual Editor</span>
          </button>
        </div>

        {/* Center Controls */}
        <div className="hidden md:flex items-center gap-1.5">
          <div className="flex items-center border border-slate-200 rounded-md bg-slate-50/70 p-0.5">
            <button
              type="button"
              onClick={onUndo}
              disabled={!canUndo}
              aria-label="Undo"
              title="Undo (Ctrl+Z)"
              className="p-1.5 rounded text-slate-700 hover:bg-white disabled:opacity-35 disabled:pointer-events-none transition-colors"
            >
              <Undo2 className="w-3.5 h-3.5" />
            </button>
            <button
              type="button"
              onClick={onRedo}
              disabled={!canRedo}
              aria-label="Redo"
              title="Redo (Ctrl+Y)"
              className="p-1.5 rounded text-slate-700 hover:bg-white disabled:opacity-35 disabled:pointer-events-none transition-colors"
            >
              <Redo2 className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="flex items-center border border-slate-200 rounded-md bg-slate-50/70 p-0.5">
            <button
              type="button"
              onClick={() => onZoomChange(Math.max(0.45, Number((zoom - 0.1).toFixed(2))))}
              aria-label="Zoom out"
              className="p-1.5 rounded text-slate-700 hover:bg-white transition-colors"
            >
              <ZoomOut className="w-3.5 h-3.5" />
            </button>
            <label htmlFor="zoom-select" className="sr-only">
              Canvas Zoom
            </label>
            <select
              id="zoom-select"
              value={Math.round(zoom * 100)}
              onChange={(e) => onZoomChange(Number(e.target.value) / 100)}
              className="text-xs font-mono tabular-nums text-slate-700 bg-transparent px-1 py-1 outline-none cursor-pointer"
            >
              {Array.from(new Set([50, 68, 75, 85, 100, 125, 150, Math.round(zoom * 100)]))
                .sort((a, b) => a - b)
                .map((pct) => (
                  <option key={pct} value={pct}>
                    {pct}%
                  </option>
                ))}
            </select>
            <button
              type="button"
              onClick={() => onZoomChange(Math.min(1.75, Number((zoom + 0.1).toFixed(2))))}
              aria-label="Zoom in"
              className="p-1.5 rounded text-slate-700 hover:bg-white transition-colors"
            >
              <ZoomIn className="w-3.5 h-3.5" />
            </button>
          </div>

          {editorMode === 'visual' && (
            <div className="hidden xl:flex items-center gap-1.5">
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={onToggleMargins}
                  aria-pressed={showMargins}
                  title="Toggle Margins"
                  aria-label="Toggle Margins"
                  className={`flex items-center gap-1 px-2 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 border transition-colors ${
                    showMargins
                      ? 'bg-orange-50 text-[#C45911] border-orange-200'
                      : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <SquareDashedBottom className="w-3.5 h-3.5" />
                  <span className="hidden 2xl:inline">Margins</span>
                </button>
                <button
                  type="button"
                  onClick={onToggleGrid}
                  aria-pressed={showGrid}
                  title="Toggle Grid"
                  aria-label="Toggle Grid"
                  className={`flex items-center gap-1 px-2 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 border transition-colors ${
                    showGrid
                      ? 'bg-orange-50 text-[#C45911] border-orange-200'
                      : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <Grid3X3 className="w-3.5 h-3.5" />
                  <span className="hidden 2xl:inline">Grid</span>
                </button>
                <button
                  type="button"
                  onClick={onToggleSnap}
                  aria-pressed={snapToGuides}
                  title="Toggle Snap to Guides"
                  aria-label="Toggle Snap"
                  className={`flex items-center gap-1 px-2 py-1.5 rounded-md text-xs font-medium whitespace-nowrap shrink-0 border transition-colors ${
                    snapToGuides
                      ? 'bg-orange-50 text-[#C45911] border-orange-200'
                      : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <Magnet className="w-3.5 h-3.5" />
                  <span className="hidden 2xl:inline">Snap</span>
                </button>
              </div>

              <div className="flex items-center gap-1">
                <label htmlFor="page-select" className="sr-only">
                  Active Page
                </label>
                <select
                  id="page-select"
                  value={activePage}
                  onChange={(e) => onActivePageChange(Number(e.target.value))}
                  className="text-xs font-medium tabular-nums text-slate-700 border border-slate-200 rounded-md px-2 py-1.5 bg-white outline-none"
                >
                  {Array.from({ length: pageCount }, (_, i) => (
                    <option key={i} value={i}>
                      Page {i + 1} / {pageCount}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={onAddPage}
                  aria-label="Add A4 page"
                  title="Add new A4 page"
                  className="p-1.5 rounded-md border border-slate-200 text-slate-700 hover:bg-slate-50 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Right: File Operations (Import, Save, Export PDF, Export DOCX) */}
      <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.docx,.png,.jpg,.jpeg,.webp,.svg,.json"
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) {
              onImportFile(file);
              e.target.value = '';
            }
          }}
        />

        <button
          type="button"
          onClick={onOpenSyncCenter}
          title={`${syncStatus.label} — Click to open Sync, Docs & Versions`}
          aria-label={`Sync status: ${syncStatus.label}`}
          data-testid="sync-status-indicator"
          className={`flex items-center gap-1 sm:gap-1.5 p-1.5 md:px-2.5 md:py-1.5 rounded-md border text-xs font-medium whitespace-nowrap shrink-0 transition-colors ${syncBadgeClasses}`}
        >
          <span className={`hidden sm:inline-block w-2 h-2 rounded-full shrink-0 ${syncDotClass}`} />
          {syncStatus.state === 'offline' ? (
            <CloudOff className="w-3.5 h-3.5 shrink-0" />
          ) : syncStatus.state === 'syncing' ? (
            <RefreshCw className="w-3.5 h-3.5 animate-spin shrink-0" />
          ) : syncStatus.state === 'error' ? (
            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          ) : (
            <Cloud className="w-3.5 h-3.5 shrink-0" />
          )}
          <span className="hidden md:inline">{syncStatus.shortLabel}</span>
        </button>

        <PWAInstallButton />

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          aria-label="Import file"
          className="flex items-center gap-1.5 p-1.5 sm:px-2.5 sm:py-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 whitespace-nowrap shrink-0 transition-colors"
        >
          <Upload className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Import</span>
        </button>

        <button
          type="button"
          onClick={onSaveProject}
          aria-label="Save project"
          className="flex items-center gap-1.5 p-1.5 sm:px-2.5 sm:py-1.5 rounded-md border border-slate-200 text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 whitespace-nowrap shrink-0 transition-colors"
        >
          <Save className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Save</span>
        </button>

        <button
          type="button"
          onClick={onExportDocx}
          disabled={isExporting}
          aria-label="Export DOCX"
          className="flex items-center gap-1 sm:gap-1.5 px-1.5 sm:px-2.5 py-1.5 rounded-md border border-slate-200 text-[11px] sm:text-xs font-medium text-slate-800 bg-white hover:bg-slate-50 disabled:opacity-50 whitespace-nowrap shrink-0 transition-colors"
        >
          <FileText className="w-3.5 h-3.5 text-sky-700 hidden sm:inline" />
          <span className="hidden 2xl:inline">Export DOCX</span>
          <span className="2xl:hidden">DOCX</span>
        </button>

        <button
          type="button"
          onClick={onExportPdf}
          disabled={isExporting}
          aria-label="Export PDF"
          className="flex items-center gap-1 sm:gap-1.5 px-1.5 sm:px-3 py-1.5 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-[11px] sm:text-xs font-semibold disabled:opacity-50 whitespace-nowrap shrink-0 transition-colors"
        >
          <FileDown className="w-3.5 h-3.5 hidden sm:inline" />
          <span className="hidden 2xl:inline">Export PDF</span>
          <span className="2xl:hidden">PDF</span>
        </button>

        {editorMode === 'visual' && (
          <button
            type="button"
            onClick={onToggleRightDrawer}
            aria-expanded={rightSidebarOpen}
            aria-label="Toggle properties and design assistant panel"
            title="Toggle Format & Assistant Panel"
            className={`hidden sm:inline-flex p-1.5 sm:p-2 rounded-md border shrink-0 transition-colors ${
              rightSidebarOpen
                ? 'border-orange-200 bg-orange-50 text-[#C45911]'
                : 'border-slate-200 text-slate-700 hover:bg-slate-50'
            }`}
          >
            <SlidersHorizontal className="w-4 h-4" />
          </button>
        )}
      </div>
    </header>
  );
};
