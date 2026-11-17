import React, { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import {
  ClipboardList,
  Eye,
  Layers,
  Maximize2,
  Redo2,
  SlidersHorizontal,
  Undo2,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import {
  createInitialHistoryState,
  historyReducer,
} from './utils/historyReducer';
import {
  EditorMode,
  FontPairing,
  LetterheadElement,
  LetterheadFormData,
  LetterheadProject,
  PageSettings,
} from './types/letterhead';
import {
  ConflictStrategy,
  DocumentVersionRecord,
  LocalClientRecord,
  LocalDocumentRecord,
  LocalTemplateRecord,
  CloudUserSession,
} from './types/sync';
import {
  applyCompleteRedesign,
  createOriginalGrashyProject,
  createRedesignedGrashyProject,
} from './data/grashyInitialProject';
import { analyzeLetterheadDesign } from './utils/designAnalyzer';
import {
  alignElementToPage,
  AlignmentTarget,
  MM_TO_PX_96DPI,
  reorderZIndex,
  roundMm,
} from './utils/geometry';
import {
  formatMenuOnlyText,
  parseQuotationText,
} from './utils/quoteParser';
import {
  computeBalanceFromAmounts,
  computeTotalFromBreakdown,
  DEFAULT_FORM_DATA,
  createResetClientFormData,
  syncElementEditToForm,
  syncFormToElements,
} from './utils/formSync';
import {
  exportProjectJson,
  exportToDocx,
  exportToPdf,
  importFileIntoProject,
} from './utils/exporters';
import {
  configureAndroidShell,
  registerAndroidBackHandler,
} from './utils/androidBridge';
import { deriveSyncStatusSnapshot } from './utils/syncEngine';
import {
  createNewLocalDocument,
  deleteLocalDocumentById,
  ensureLocalDatabaseSeeded,
  getWorkspaceSnapshotFromDb,
  localDb,
  resolveConflictInDb,
  saveLocalDocumentWorkingCopy,
  saveTemplateFromProjectToDb,
  setActiveDocumentId,
} from './db/localDb';
import {
  getSupabaseClient,
  isSupabaseConfigured,
  mapSessionToCloudUser,
  signInCloud,
  signOutCloud,
  signUpCloud,
  synchronizeLocalAndSupabase,
} from './services/supabaseSyncService';
import { useOnlineStatus } from './hooks/useOnlineStatus';
import { TopToolbar } from './components/TopToolbar';
import { FormGeneratorPanel } from './components/FormGeneratorPanel';
import { LeftSidebar, LeftTab } from './components/LeftSidebar';
import { A4Canvas } from './components/A4Canvas';
import { RightSidebar, RightTab } from './components/RightSidebar';
import { SyncStatusModal } from './components/SyncStatusModal';

const STORAGE_KEY = 'grashy-studio-project-v3';

function getFitZoomForWidth(viewportWidth: number, includeRuler = false): number {
  if (viewportWidth >= 1024) return 0.68;
  const pagePxAt1x = 210 * MM_TO_PX_96DPI;
  const chromePadding = includeRuler ? 60 : 32;
  const fit = (viewportWidth - chromePadding) / pagePxAt1x;
  return Math.max(0.35, Math.min(0.68, Math.floor(fit * 100) / 100));
}

function loadInitialProject(): LetterheadProject {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as LetterheadProject;
      if (parsed && Array.isArray(parsed.elements) && parsed.pageSettings) {
        const mergedForm: LetterheadFormData = {
          ...DEFAULT_FORM_DATA,
          ...(parsed.formData || {}),
        };
        const needsBreakdownSync = parsed.elements.some(
          (el) =>
            el.role === 'cost-summary' &&
            !/Transport:|Total Cost \(Excl\. VAT\):/i.test(el.content || '')
        );
        return {
          ...parsed,
          formData: mergedForm,
          elements: needsBreakdownSync
            ? syncFormToElements(parsed.elements, mergedForm)
            : parsed.elements,
        };
      }
    }
  } catch {
    // Fallback to redesigned Grashy letterhead
  }
  return createRedesignedGrashyProject();
}

export default function App() {
  const [history, dispatch] = useReducer(historyReducer, undefined, () =>
    createInitialHistoryState(loadInitialProject())
  );
  const project = history.present;
  const canUndo = history.past.length > 0 || history.checkpoint !== null;
  const canRedo = history.future.length > 0;

  const [editorMode, setEditorMode] = useState<EditorMode>('form');
  const [deletedElements, setDeletedElements] = useState<LetterheadElement[]>([]);

  const [activePage, setActivePage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [highlightedIds, setHighlightedIds] = useState<string[]>([]);
  const [isDesktop, setIsDesktop] = useState(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 1024 : true
  );
  const [zoom, setZoom] = useState(() =>
    typeof window !== 'undefined' ? getFitZoomForWidth(window.innerWidth) : 0.68
  );

  const [leftTab, setLeftTab] = useState<LeftTab>('layers');
  const [rightTab, setRightTab] = useState<RightTab>('properties');
  const [desktopLeftOpen, setDesktopLeftOpen] = useState(true);
  const [desktopRightOpen, setDesktopRightOpen] = useState(true);
  const [mobileDrawer, setMobileDrawer] = useState<'none' | 'left' | 'right'>('none');
  const [mobileFormPane, setMobileFormPane] = useState<'form' | 'preview'>('form');

  const [isExporting, setIsExporting] = useState(false);
  const [notice, setNotice] = useState<{ text: string; tone: 'info' | 'error' } | null>(null);

  // Offline-first IndexedDB & Supabase Sync State
  const isOnline = useOnlineStatus();
  const [syncModalOpen, setSyncModalOpen] = useState(false);
  const [dbDocuments, setDbDocuments] = useState<LocalDocumentRecord[]>([]);
  const [dbClients, setDbClients] = useState<LocalClientRecord[]>([]);
  const [dbTemplates, setDbTemplates] = useState<LocalTemplateRecord[]>([]);
  const [dbVersions, setDbVersions] = useState<DocumentVersionRecord[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [conflictCount, setConflictCount] = useState(0);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);
  const [cloudUser, setCloudUser] = useState<CloudUserSession | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncError, setSyncError] = useState<string | null>(null);
  const dbHydratedRef = useRef(false);
  const skipNextAutoDbWriteRef = useRef(false);
  const hasUnsavedUserEditsRef = useRef(false);
  const isSyncingRef = useRef(false);
  const projectRef = useRef(project);
  projectRef.current = project;
  const cloudUserRef = useRef<CloudUserSession | null>(cloudUser);
  cloudUserRef.current = cloudUser;
  const supabaseConfigured = isSupabaseConfigured();

  const refreshWorkspaceSnapshot = useCallback(async () => {
    try {
      const snap = await getWorkspaceSnapshotFromDb();
      setDbDocuments(snap.documents);
      setDbClients(snap.clients);
      setDbTemplates(snap.templates);
      setDbVersions(snap.versions);
      setPendingCount(snap.pendingCount);
      setConflictCount(snap.conflictCount);
      setLastSyncedAt(snap.lastSyncedAt);
      return snap;
    } catch {
      return null;
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const mq = window.matchMedia('(min-width: 1024px)');
    const syncViewport = () => {
      const desktop = mq.matches;
      setIsDesktop(desktop);
      setMobileDrawer('none');
      setZoom((prev) => {
        if (!desktop && prev > 0.6) {
          return getFitZoomForWidth(window.innerWidth);
        }
        if (desktop && prev < 0.55) {
          return 0.68;
        }
        return prev;
      });
    };
    mq.addEventListener('change', syncViewport);
    return () => mq.removeEventListener('change', syncViewport);
  }, []);

  const showNotice = useCallback((text: string, tone: 'info' | 'error' = 'info') => {
    setNotice({ text, tone });
  }, []);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(null), 3500);
    return () => clearTimeout(t);
  }, [notice]);

  // Hydrate IndexedDB on boot (preserving/migrating existing localStorage state)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const snap = await ensureLocalDatabaseSeeded(loadInitialProject());
        if (cancelled) return;
        dbHydratedRef.current = true;
        setDbDocuments(snap.documents);
        setDbClients(snap.clients);
        setDbTemplates(snap.templates);
        setDbVersions(snap.versions);
        setPendingCount(snap.pendingCount);
        setConflictCount(snap.conflictCount);
        setLastSyncedAt(snap.lastSyncedAt);
        if (snap.activeDocument?.project) {
          skipNextAutoDbWriteRef.current = true;
          dispatch({ type: 'LOAD_DOCUMENT', project: snap.activeDocument.project });
        }
      } catch {
        dbHydratedRef.current = true;
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Track Supabase auth session if configured
  useEffect(() => {
    const client = getSupabaseClient();
    if (!client) return;
    let mounted = true;
    client.auth.getSession().then(({ data }) => {
      if (mounted) {
        setCloudUser(mapSessionToCloudUser(data.session));
      }
    });
    const { data: subscription } = client.auth.onAuthStateChange((_event, session) => {
      if (mounted) {
        setCloudUser(mapSessionToCloudUser(session));
      }
    });
    return () => {
      mounted = false;
      subscription.subscription.unsubscribe();
    };
  }, []);

  // Sync automatically into localStorage (quick synchronous fallback) and Dexie IndexedDB
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Ignore quota errors
    }

    if (!dbHydratedRef.current) return;
    if (skipNextAutoDbWriteRef.current) {
      skipNextAutoDbWriteRef.current = false;
      return;
    }

    hasUnsavedUserEditsRef.current = true;
    const timer = setTimeout(() => {
      saveLocalDocumentWorkingCopy(project)
        .then(() => {
          hasUnsavedUserEditsRef.current = false;
          return refreshWorkspaceSnapshot();
        })
        .catch(() => {
          // Non-fatal IndexedDB persistence fallback
        });
    }, 280);

    return () => clearTimeout(timer);
  }, [project, refreshWorkspaceSnapshot]);

  const handleSyncNow = useCallback(
    async (silent = false, userOverride?: CloudUserSession | null) => {
      const activeUser = userOverride !== undefined ? userOverride : cloudUserRef.current;
      if (!isOnline) {
        if (!silent) showNotice('Offline – changes saved locally and queued for sync');
        return;
      }
      if (!supabaseConfigured) {
        if (!silent) {
          showNotice(
            'Saved offline in IndexedDB. Configure VITE_SUPABASE_URL & VITE_SUPABASE_ANON_KEY for cloud sync.'
          );
        }
        return;
      }
      if (!activeUser) {
        if (!silent) {
          setSyncModalOpen(true);
          showNotice('Sign in inside the Sync Center to synchronize with Supabase');
        }
        return;
      }
      if (isSyncingRef.current) return;

      isSyncingRef.current = true;
      setIsSyncing(true);
      setSyncError(null);
      try {
        const currentProject = projectRef.current;
        const existingDoc = await localDb.documents.get(currentProject.id);
        if (!existingDoc?.isBootSeed || hasUnsavedUserEditsRef.current) {
          await saveLocalDocumentWorkingCopy(currentProject);
          hasUnsavedUserEditsRef.current = false;
        }
        const res = await synchronizeLocalAndSupabase(activeUser.userId);
        const snap = await refreshWorkspaceSnapshot();
        if (
          snap &&
          snap.activeDocument.id === currentProject.id &&
          res.pulledCount > 0 &&
          snap.activeDocument.syncStatus === 'synced'
        ) {
          skipNextAutoDbWriteRef.current = true;
          dispatch({ type: 'LOAD_DOCUMENT', project: snap.activeDocument.project });
        }
        if (!silent) {
          if (res.conflictCount > 0) {
            showNotice(
              `Sync paused on ${res.conflictCount} conflict(s) — review before overwriting`,
              'error'
            );
          } else {
            showNotice(
              `Synced (${res.uploadedCount} uploaded, ${res.pulledCount} retrieved)`
            );
          }
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Synchronization failed';
        setSyncError(msg);
        if (!silent) showNotice(msg, 'error');
      } finally {
        isSyncingRef.current = false;
        setIsSyncing(false);
      }
    },
    [isOnline, supabaseConfigured, refreshWorkspaceSnapshot, showNotice]
  );

  // Automatic background synchronization when internet reconnects & periodically while online
  useEffect(() => {
    if (!isOnline || !supabaseConfigured || !cloudUser) return;
    handleSyncNow(true, cloudUser);
    const interval = setInterval(() => {
      handleSyncNow(true);
    }, 45000);
    return () => clearInterval(interval);
  }, [isOnline, supabaseConfigured, cloudUser, handleSyncNow]);

  const syncStatus = useMemo(
    () =>
      deriveSyncStatusSnapshot({
        isOnline,
        isSyncing,
        supabaseConfigured,
        isAuthenticated: Boolean(cloudUser),
        userEmail: cloudUser?.email || null,
        pendingCount,
        conflictCount,
        lastSyncedAt,
        lastError: syncError,
      }),
    [
      isOnline,
      isSyncing,
      supabaseConfigured,
      cloudUser,
      pendingCount,
      conflictCount,
      lastSyncedAt,
      syncError,
    ]
  );

  const handleModeChange = useCallback((mode: EditorMode) => {
    dispatch({ type: 'FINALIZE_CHECKPOINT' });
    setEditorMode(mode);
    setMobileDrawer('none');
    if (typeof window !== 'undefined' && window.innerWidth < 1024) {
      setZoom(getFitZoomForWidth(window.innerWidth, mode === 'visual'));
    }
    if (mode === 'form') {
      setSelectedId(null);
      setHighlightedIds([]);
    }
  }, []);

  const handleToggleLeftSidebar = useCallback(() => {
    if (isDesktop) {
      setDesktopLeftOpen((v) => !v);
    } else if (editorMode === 'form') {
      setMobileFormPane((p) => (p === 'form' ? 'preview' : 'form'));
    } else {
      setMobileDrawer((d) => (d === 'left' ? 'none' : 'left'));
    }
  }, [isDesktop, editorMode]);

  const handleToggleRightSidebar = useCallback(() => {
    if (isDesktop) {
      setDesktopRightOpen((v) => !v);
    } else {
      setMobileDrawer((d) => (d === 'right' ? 'none' : 'right'));
    }
  }, [isDesktop]);

  const commitProjectChange = useCallback(
    (updater: (prev: LetterheadProject) => LetterheadProject) => {
      dispatch({ type: 'COMMIT', updater });
    },
    []
  );

  const handleUndo = useCallback(() => {
    dispatch({ type: 'UNDO' });
  }, []);

  const handleRedo = useCallback(() => {
    dispatch({ type: 'REDO' });
  }, []);

  // Mode 1: Form updates populate live preview while preserving all Visual Editor geometry/styles
  const handleFormChange = useCallback((patch: Partial<LetterheadFormData>) => {
    dispatch({
      type: 'LIVE_UPDATE',
      captureCheckpoint: true,
      updater: (prev) => {
        const nextForm: LetterheadFormData = {
          ...prev.formData,
          ...patch,
        };
        if (
          (patch.subtotalCost !== undefined || patch.vatAmount !== undefined) &&
          patch.totalAmount === undefined
        ) {
          const computedTotal = computeTotalFromBreakdown(
            nextForm.subtotalCost,
            nextForm.vatAmount,
            nextForm.totalAmount
          );
          if (computedTotal !== null) {
            nextForm.totalAmount = computedTotal;
          }
        }
        if (
          (patch.totalAmount !== undefined ||
            patch.depositAmount !== undefined ||
            patch.subtotalCost !== undefined ||
            patch.vatAmount !== undefined) &&
          patch.balance === undefined
        ) {
          const computed = computeBalanceFromAmounts(
            nextForm.totalAmount,
            nextForm.depositAmount
          );
          if (computed !== null) {
            nextForm.balance = computed;
          }
        }
        return {
          ...prev,
          formData: nextForm,
          elements: syncFormToElements(prev.elements, nextForm),
        };
      },
    });
  }, []);

  const handleGenerateDocument = useCallback(() => {
    const syncedElements = syncFormToElements(project.elements, project.formData);
    const updatedProject: LetterheadProject = {
      ...project,
      elements: syncedElements,
    };
    dispatch({
      type: 'COMMIT',
      skipIfUnchanged: true,
      updater: (prev) => ({
        ...prev,
        elements: syncFormToElements(prev.elements, prev.formData),
      }),
    });
    const clientLabel = project.formData.clientName.trim();
    saveLocalDocumentWorkingCopy(updatedProject, {
      recordVersion: true,
      versionSummary: clientLabel
        ? `Generated quotation for ${clientLabel}`
        : 'Generated document',
    })
      .then(() => refreshWorkspaceSnapshot())
      .catch(() => {});
    showNotice(
      clientLabel
        ? `Generated document for ${clientLabel}`
        : 'Document generated and updated'
    );
  }, [project, refreshWorkspaceSnapshot, showNotice]);

  const handleNewClient = useCallback(() => {
    commitProjectChange((prev) => {
      const nextForm = createResetClientFormData(prev.formData);
      return {
        ...prev,
        formData: nextForm,
        elements: syncFormToElements(prev.elements, nextForm),
      };
    });
    showNotice('Cleared event fields for a new client (sender & payment details kept)');
  }, [commitProjectChange, showNotice]);

  const handleApplyQuoteToLetterhead = useCallback(
    (rawText: string) => {
      const parsed = parseQuotationText(rawText);
      const formattedMenu = formatMenuOnlyText(parsed);

      commitProjectChange((prev) => {
        const nextTotal = parsed.totalCost ? `KES ${parsed.totalCost}` : prev.formData.totalAmount;
        const computedBalance = computeBalanceFromAmounts(
          nextTotal,
          prev.formData.depositAmount
        );

        const nextForm: LetterheadFormData = {
          ...prev.formData,
          date: parsed.date || prev.formData.date,
          clientName: parsed.client || prev.formData.clientName,
          pax: parsed.pax || prev.formData.pax,
          venue: parsed.venue || prev.formData.venue,
          eventDetails: parsed.menuItems.length > 0 ? formattedMenu : prev.formData.eventDetails,
          transportCost: parsed.transport || prev.formData.transportCost,
          subtotalCost: parsed.subtotal || prev.formData.subtotalCost,
          vatAmount: parsed.vat || prev.formData.vatAmount,
          totalAmount: nextTotal,
          balance: computedBalance ?? prev.formData.balance,
          paymentInstructions:
            'Thank you for your enquiry, we are delighted to be at your service.\n50% deposit confirms booking.',
        };

        return {
          ...prev,
          formData: nextForm,
          elements: syncFormToElements(prev.elements, nextForm),
        };
      });
      showNotice('Populated form & letterhead from pasted quote');
    },
    [commitProjectChange, showNotice]
  );

  // Mode 2: Element edits sync back to formData when content changes
  const handleUpdateElement = useCallback(
    (id: string, patch: Partial<LetterheadElement>, recordHistory = true) => {
      const apply = (prev: LetterheadProject): LetterheadProject => {
        const target = prev.elements.find((el) => el.id === id);
        let nextForm = prev.formData;
        if (target && patch.content !== undefined) {
          nextForm = syncElementEditToForm(prev.formData, target.role, patch.content);
        }
        return {
          ...prev,
          layoutMode: prev.layoutMode === 'original' ? 'custom' : prev.layoutMode,
          formData: nextForm,
          elements: prev.elements.map((el) => (el.id === id ? { ...el, ...patch } : el)),
        };
      };

      if (recordHistory) {
        commitProjectChange(apply);
      } else {
        dispatch({ type: 'LIVE_UPDATE', captureCheckpoint: true, updater: apply });
      }
    },
    [commitProjectChange]
  );

  const handleCommitElementHistory = useCallback(
    (snapshotBefore: LetterheadElement[]) => {
      dispatch({ type: 'FINALIZE_CHECKPOINT', fallbackElements: snapshotBefore });
    },
    []
  );

  const handleSelectElement = useCallback((id: string | null) => {
    setSelectedId(id);
    setHighlightedIds([]);
    if (id) {
      setRightTab('properties');
    }
  }, []);

  const handleDuplicateElement = useCallback(
    (id: string) => {
      const src = project.elements.find((e) => e.id === id);
      if (!src) return;
      const maxZ = Math.max(0, ...project.elements.map((e) => e.zIndex));
      const copy: LetterheadElement = {
        ...src,
        id: `${src.id}-copy-${Date.now()}`,
        name: `${src.name} (Copy)`,
        role: 'custom',
        x: roundMm(src.x + 5),
        y: roundMm(src.y + 5),
        zIndex: maxZ + 10,
        locked: false,
      };
      commitProjectChange((prev) => ({
        ...prev,
        elements: [...prev.elements, copy],
      }));
      setSelectedId(copy.id);
    },
    [project.elements, commitProjectChange]
  );

  const handleDeleteElement = useCallback(
    (id: string) => {
      const target = project.elements.find((e) => e.id === id);
      if (!target) return;
      setDeletedElements((d) => [target, ...d.filter((item) => item.id !== target.id)]);
      commitProjectChange((prev) => ({
        ...prev,
        elements: prev.elements.filter((e) => e.id !== id),
      }));
      if (selectedId === id) setSelectedId(null);
    },
    [project.elements, selectedId, commitProjectChange]
  );

  const handleRestoreElement = useCallback(
    (id: string) => {
      const target = deletedElements.find((e) => e.id === id);
      if (!target) return;
      setDeletedElements((d) => d.filter((e) => e.id !== id));
      commitProjectChange((prev) => ({
        ...prev,
        elements: [...prev.elements, { ...target, pageIndex: activePage }],
      }));
      setSelectedId(target.id);
    },
    [deletedElements, activePage, commitProjectChange]
  );

  const handleReorderElement = useCallback(
    (id: string, action: 'forward' | 'backward' | 'front' | 'back') => {
      commitProjectChange((prev) => ({
        ...prev,
        elements: reorderZIndex(prev.elements, id, action),
      }));
    },
    [commitProjectChange]
  );

  const handleAlignElement = useCallback(
    (id: string, target: AlignmentTarget) => {
      commitProjectChange((prev) => ({
        ...prev,
        elements: prev.elements.map((el) =>
          el.id === id ? alignElementToPage(el, target, prev.pageSettings) : el
        ),
      }));
    },
    [commitProjectChange]
  );

  const handleAddPresetElement = useCallback(
    (kind: 'heading' | 'subtitle' | 'body' | 'line' | 'box') => {
      const maxZ = Math.max(0, ...project.elements.map((e) => e.zIndex));
      const { margins, widthMm } = project.pageSettings;
      const contentW = widthMm - margins.left - margins.right;
      const id = `el-${kind}-${Date.now()}`;

      let newEl: LetterheadElement;
      if (kind === 'heading') {
        newEl = {
          id,
          pageIndex: activePage,
          type: 'text',
          role: 'custom',
          name: 'Section Heading',
          x: margins.left,
          y: 90,
          width: contentW,
          height: 11,
          rotation: 0,
          opacity: 1,
          zIndex: maxZ + 10,
          locked: false,
          visible: true,
          content: 'CATERING QUOTATION',
          textStyle: {
            fontFamily: 'Plus Jakarta Sans',
            fontSize: 16,
            fontWeight: 700,
            fontStyle: 'normal',
            letterSpacing: 0.02,
            lineHeight: 1.25,
            paragraphSpacing: 2,
            color: '#C45911',
            textAlign: 'left',
            paddingTop: 1,
            paddingRight: 1,
            paddingBottom: 1,
            paddingLeft: 1,
          },
        };
      } else if (kind === 'subtitle') {
        newEl = {
          id,
          pageIndex: activePage,
          type: 'text',
          role: 'custom',
          name: 'Subtitle Text',
          x: margins.left,
          y: 102,
          width: contentW,
          height: 8,
          rotation: 0,
          opacity: 1,
          zIndex: maxZ + 10,
          locked: false,
          visible: true,
          content: 'Prepared exclusively for your upcoming event',
          textStyle: {
            fontFamily: 'DM Sans',
            fontSize: 10.5,
            fontWeight: 500,
            fontStyle: 'italic',
            letterSpacing: 0,
            lineHeight: 1.4,
            paragraphSpacing: 2,
            color: '#475569',
            textAlign: 'left',
            paddingTop: 1,
            paddingRight: 1,
            paddingBottom: 1,
            paddingLeft: 1,
          },
        };
      } else if (kind === 'body') {
        newEl = {
          id,
          pageIndex: activePage,
          type: 'text',
          role: 'custom',
          name: 'Text Box',
          x: margins.left,
          y: 115,
          width: contentW,
          height: 36,
          rotation: 0,
          opacity: 1,
          zIndex: maxZ + 10,
          locked: false,
          visible: true,
          content: 'Enter menu items, service notes, or terms & conditions here.',
          textStyle: {
            fontFamily: 'DM Sans',
            fontSize: 10.5,
            fontWeight: 400,
            fontStyle: 'normal',
            letterSpacing: 0,
            lineHeight: 1.5,
            paragraphSpacing: 3,
            color: '#1E293B',
            textAlign: 'left',
            paddingTop: 1.5,
            paddingRight: 1.5,
            paddingBottom: 1.5,
            paddingLeft: 1.5,
          },
        };
      } else if (kind === 'line') {
        newEl = {
          id,
          pageIndex: activePage,
          type: 'line',
          role: 'custom',
          name: 'Divider Line',
          x: margins.left,
          y: 155,
          width: contentW,
          height: 2,
          rotation: 0,
          opacity: 1,
          zIndex: maxZ + 10,
          locked: false,
          visible: true,
          shapeStyle: {
            fill: 'transparent',
            stroke: '#C45911',
            strokeWidth: 1.2,
            borderRadius: 0,
            lineStyle: 'solid',
          },
        };
      } else {
        newEl = {
          id,
          pageIndex: activePage,
          type: 'shape',
          role: 'custom',
          name: 'Shape Box',
          x: margins.left,
          y: 162,
          width: contentW,
          height: 28,
          rotation: 0,
          opacity: 1,
          zIndex: maxZ + 5,
          locked: false,
          visible: true,
          shapeStyle: {
            fill: '#FFF7ED',
            stroke: '#FDBA74',
            strokeWidth: 0.75,
            borderRadius: 2,
            lineStyle: 'solid',
          },
        };
      }

      commitProjectChange((prev) => ({
        ...prev,
        elements: [...prev.elements, newEl],
      }));
      setSelectedId(newEl.id);
      setRightTab('properties');
    },
    [project.elements, project.pageSettings, activePage, commitProjectChange]
  );

  const handleApplyFontPairing = useCallback(
    (pairing: FontPairing) => {
      commitProjectChange((prev) => ({
        ...prev,
        elements: prev.elements.map((el) => {
          if (el.type !== 'text' || !el.textStyle) return el;
          const isDisplay =
            el.role === 'company-title' ||
            el.role === 'closing' ||
            el.role === 'client-name' ||
            el.textStyle.fontSize >= 14;
          return {
            ...el,
            textStyle: {
              ...el.textStyle,
              fontFamily: isDisplay ? pairing.displayFont : pairing.bodyFont,
            },
          };
        }),
      }));
      showNotice(`Applied "${pairing.name}" typography pairing`);
    },
    [commitProjectChange, showNotice]
  );

  const handleImportFile = useCallback(
    async (file: File) => {
      try {
        const maxZ = Math.max(0, ...project.elements.map((e) => e.zIndex));
        const result = await importFileIntoProject(file, activePage, maxZ + 10);
        if (result.kind === 'project' && result.project) {
          commitProjectChange(() => ({
            ...result.project!,
            formData: {
              ...DEFAULT_FORM_DATA,
              ...(result.project!.formData || {}),
            },
          }));
          setDeletedElements([]);
          setActivePage(0);
          setSelectedId(null);
        } else if (result.kind === 'elements' && result.addedElements) {
          commitProjectChange((prev) => ({
            ...prev,
            elements: [...prev.elements, ...result.addedElements!],
          }));
          setSelectedId(result.addedElements[0].id);
        }
        showNotice(result.message);
      } catch (err) {
        showNotice(
          err instanceof Error ? err.message : 'Unable to import file.',
          'error'
        );
      }
    },
    [project.elements, activePage, commitProjectChange, showNotice]
  );

  const handleReplaceImage = useCallback(
    async (id: string, file: File) => {
      const reader = new FileReader();
      reader.onload = () => {
        handleUpdateElement(id, { imageSrc: String(reader.result) });
        showNotice(`Replaced image with "${file.name}"`);
      };
      reader.readAsDataURL(file);
    },
    [handleUpdateElement, showNotice]
  );

  const handleAddPage = useCallback(() => {
    commitProjectChange((prev) => {
      const nextPageIdx = prev.pageCount;
      const headerLogo = prev.elements.find((e) => e.pageIndex === 0 && e.role === 'logo');
      const footerWave = prev.elements.find((e) => e.pageIndex === 0 && e.role === 'footer-wave');
      const headerRule = prev.elements.find((e) => e.pageIndex === 0 && e.role === 'separator');

      const extras: LetterheadElement[] = [];
      if (headerLogo) {
        extras.push({
          ...headerLogo,
          id: `p${nextPageIdx}-logo-${Date.now()}`,
          pageIndex: nextPageIdx,
          width: Math.min(34, headerLogo.width),
          height: Math.min(23, headerLogo.height),
        });
      }
      if (headerRule) {
        extras.push({
          ...headerRule,
          id: `p${nextPageIdx}-sep-${Date.now()}`,
          pageIndex: nextPageIdx,
        });
      }
      if (footerWave) {
        extras.push({
          ...footerWave,
          id: `p${nextPageIdx}-wave-${Date.now()}`,
          pageIndex: nextPageIdx,
        });
      }
      extras.push({
        id: `p${nextPageIdx}-body-${Date.now()}`,
        pageIndex: nextPageIdx,
        type: 'text',
        role: 'custom',
        name: `Page ${nextPageIdx + 1} Content`,
        x: prev.pageSettings.margins.left,
        y: 52,
        width:
          prev.pageSettings.widthMm -
          prev.pageSettings.margins.left -
          prev.pageSettings.margins.right,
        height: 190,
        rotation: 0,
        opacity: 1,
        zIndex: 40,
        locked: false,
        visible: true,
        content: 'Continuation sheet — Terms, Event Schedule, or Additional Notes',
        textStyle: {
          fontFamily: 'DM Sans',
          fontSize: 10.5,
          fontWeight: 400,
          fontStyle: 'normal',
          letterSpacing: 0,
          lineHeight: 1.5,
          paragraphSpacing: 3,
          color: '#1E293B',
          textAlign: 'left',
          paddingTop: 2,
          paddingRight: 2,
          paddingBottom: 2,
          paddingLeft: 2,
        },
      });

      setActivePage(nextPageIdx);
      return {
        ...prev,
        pageCount: prev.pageCount + 1,
        elements: [...prev.elements, ...extras],
      };
    });
    showNotice('Added continuation A4 page');
  }, [commitProjectChange, showNotice]);

  const handleDeletePage = useCallback(
    (pageIndex: number) => {
      if (project.pageCount <= 1) return;
      commitProjectChange((prev) => ({
        ...prev,
        pageCount: prev.pageCount - 1,
        elements: prev.elements
          .filter((e) => e.pageIndex !== pageIndex)
          .map((e) => (e.pageIndex > pageIndex ? { ...e, pageIndex: e.pageIndex - 1 } : e)),
      }));
      setActivePage((p) => Math.max(0, Math.min(p, project.pageCount - 2)));
      setSelectedId(null);
    },
    [project.pageCount, commitProjectChange]
  );

  // Keyboard shortcuts (Undo, Redo, Delete, Duplicate, Arrow nudge)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const inInput =
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable);

      if (e.key === 'Escape' && mobileDrawer !== 'none') {
        e.preventDefault();
        setMobileDrawer('none');
        return;
      }

      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'z') {
        if (inInput) return;
        e.preventDefault();
        if (e.shiftKey) handleRedo();
        else handleUndo();
        return;
      }

      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'y') {
        if (inInput) return;
        e.preventDefault();
        handleRedo();
        return;
      }

      if (editorMode !== 'visual' || inInput || !selectedId) return;

      const selected = project.elements.find((el) => el.id === selectedId);
      if (!selected) return;

      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'd') {
        e.preventDefault();
        handleDuplicateElement(selectedId);
        return;
      }

      if (e.key === 'Delete' || e.key === 'Backspace') {
        e.preventDefault();
        handleDeleteElement(selectedId);
        return;
      }

      if (!selected.locked && ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) {
        e.preventDefault();
        const step = e.shiftKey ? 2 : 0.5;
        const dx = e.key === 'ArrowLeft' ? -step : e.key === 'ArrowRight' ? step : 0;
        const dy = e.key === 'ArrowUp' ? -step : e.key === 'ArrowDown' ? step : 0;
        handleUpdateElement(selectedId, {
          x: roundMm(selected.x + dx),
          y: roundMm(selected.y + dy),
        });
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [
    editorMode,
    mobileDrawer,
    selectedId,
    project.elements,
    handleUndo,
    handleRedo,
    handleDuplicateElement,
    handleDeleteElement,
    handleUpdateElement,
  ]);

  const suggestions = useMemo(() => analyzeLetterheadDesign(project), [project]);
  const selectedElement = useMemo(
    () => project.elements.find((el) => el.id === selectedId) || null,
    [project.elements, selectedId]
  );
  const visibleDeletedElements = useMemo(
    () =>
      deletedElements.filter(
        (d) => !project.elements.some((existing) => existing.id === d.id)
      ),
    [deletedElements, project.elements]
  );

  const androidBackStateRef = useRef({
    syncModalOpen,
    mobileDrawer,
    editorMode,
    mobileFormPane,
    selectedId,
  });
  androidBackStateRef.current = {
    syncModalOpen,
    mobileDrawer,
    editorMode,
    mobileFormPane,
    selectedId,
  };

  useEffect(() => {
    void configureAndroidShell();
    return registerAndroidBackHandler(
      () => androidBackStateRef.current,
      (action) => {
        if (action === 'close-sync-modal') {
          setSyncModalOpen(false);
        } else if (action === 'close-drawer') {
          setMobileDrawer('none');
        } else if (action === 'clear-selection') {
          setSelectedId(null);
        } else if (action === 'back-to-form') {
          setMobileFormPane('form');
        }
      }
    );
  }, []);

  const handleExportPdf = async () => {
    setIsExporting(true);
    try {
      const mode = await exportToPdf(project);
      showNotice(
        mode === 'native-shared'
          ? 'A4 PDF saved & opened in Android Share sheet'
          : 'Exported high-resolution A4 PDF'
      );
    } catch {
      showNotice('PDF export failed', 'error');
    } finally {
      setIsExporting(false);
    }
  };

  const handleExportDocx = async () => {
    setIsExporting(true);
    try {
      const mode = await exportToDocx(project);
      showNotice(
        mode === 'native-shared'
          ? 'Word (.docx) saved & opened in Android Share sheet'
          : 'Exported editable Word (.docx) document'
      );
    } catch {
      showNotice('DOCX export failed', 'error');
    } finally {
      setIsExporting(false);
    }
  };

  const handleUpdatePageSettings = useCallback(
    (patch: Partial<PageSettings>) => {
      commitProjectChange((prev) => ({
        ...prev,
        pageSettings: { ...prev.pageSettings, ...patch },
      }));
    },
    [commitProjectChange]
  );

  const leftSidebarOpen = isDesktop
    ? desktopLeftOpen
    : editorMode === 'form'
      ? mobileFormPane === 'form'
      : mobileDrawer === 'left';
  const rightSidebarOpen = isDesktop
    ? desktopRightOpen
    : mobileDrawer === 'right';

  const fitZoomToScreen = useCallback(() => {
    if (typeof window === 'undefined') return;
    setZoom(getFitZoomForWidth(window.innerWidth, editorMode === 'visual'));
  }, [editorMode]);

  return (
    <div className="h-dvh w-screen flex flex-col overflow-hidden bg-[#EEF0F3] app-safe-shell">
      <TopToolbar
        editorMode={editorMode}
        onModeChange={handleModeChange}
        projectName={project.name}
        onProjectNameChange={(name) =>
          dispatch({
            type: 'LIVE_UPDATE',
            captureCheckpoint: true,
            updater: (prev) => ({ ...prev, name }),
          })
        }
        canUndo={canUndo}
        canRedo={canRedo}
        onUndo={handleUndo}
        onRedo={handleRedo}
        zoom={zoom}
        onZoomChange={setZoom}
        showMargins={project.pageSettings.showMargins}
        onToggleMargins={() =>
          handleUpdatePageSettings({ showMargins: !project.pageSettings.showMargins })
        }
        showGrid={project.pageSettings.showGrid}
        onToggleGrid={() =>
          handleUpdatePageSettings({ showGrid: !project.pageSettings.showGrid })
        }
        snapToGuides={project.pageSettings.snapToGuides}
        onToggleSnap={() =>
          handleUpdatePageSettings({ snapToGuides: !project.pageSettings.snapToGuides })
        }
        pageCount={project.pageCount}
        activePage={activePage}
        onActivePageChange={setActivePage}
        onAddPage={handleAddPage}
        onImportFile={handleImportFile}
        onSaveProject={() => {
          saveLocalDocumentWorkingCopy(project, {
            recordVersion: true,
            versionSummary: 'Manual save & JSON export',
          })
            .then(() => refreshWorkspaceSnapshot())
            .catch(() => {});
          void exportProjectJson(project);
          showNotice('Saved locally (IndexedDB + version snapshot) and exported JSON');
        }}
        onExportPdf={handleExportPdf}
        onExportDocx={handleExportDocx}
        isExporting={isExporting}
        leftSidebarOpen={leftSidebarOpen}
        rightSidebarOpen={rightSidebarOpen}
        onToggleLeftDrawer={handleToggleLeftSidebar}
        onToggleRightDrawer={handleToggleRightSidebar}
        syncStatus={syncStatus}
        onOpenSyncCenter={() => setSyncModalOpen(true)}
      />

      {/* Mobile Mode 1 Sub-Navigation: Instant switching between Form & Live Preview on phones */}
      {!isDesktop && editorMode === 'form' && (
        <div
          role="tablist"
          aria-label="Mobile form workspace view"
          className="bg-white border-b border-slate-200 px-3 py-1.5 grid grid-cols-2 gap-1.5 shrink-0"
        >
          <button
            type="button"
            role="tab"
            aria-selected={mobileFormPane === 'form'}
            onClick={() => setMobileFormPane('form')}
            className={`flex items-center justify-center gap-1.5 py-1.5 rounded-md text-xs font-semibold transition-colors ${
              mobileFormPane === 'form'
                ? 'bg-orange-50 text-[#C45911] border border-orange-200'
                : 'text-slate-600 bg-slate-50 border border-transparent'
            }`}
          >
            <ClipboardList className="w-3.5 h-3.5" />
            Edit Form
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mobileFormPane === 'preview'}
            onClick={() => {
              fitZoomToScreen();
              setMobileFormPane('preview');
            }}
            className={`flex items-center justify-center gap-1.5 py-1.5 rounded-md text-xs font-semibold transition-colors ${
              mobileFormPane === 'preview'
                ? 'bg-orange-50 text-[#C45911] border border-orange-200'
                : 'text-slate-600 bg-slate-50 border border-transparent'
            }`}
          >
            <Eye className="w-3.5 h-3.5" />
            Live Preview
          </button>
        </div>
      )}

      {/* Workspace Body */}
      <div className="flex-1 flex min-h-0 relative">
        {/* Mobile Backdrop for Visual Editor Drawers */}
        {!isDesktop && editorMode === 'visual' && mobileDrawer !== 'none' && (
          <button
            type="button"
            aria-label="Close open sidebar"
            onClick={() => setMobileDrawer('none')}
            className="fixed inset-0 top-14 z-40 bg-slate-900/40"
          />
        )}

        {/* Left Panel: Full-width Form on phones when mobileFormPane==='form'; slide-over drawer in Visual on phones; collapsible static sidebar on Desktop */}
        {((isDesktop && desktopLeftOpen) ||
          (!isDesktop && editorMode === 'form' && mobileFormPane === 'form') ||
          (!isDesktop && editorMode === 'visual' && mobileDrawer === 'left')) && (
          <div
            className={
              !isDesktop && editorMode === 'form'
                ? 'w-full h-full flex min-h-0'
                : !isDesktop && editorMode === 'visual'
                  ? 'fixed top-14 bottom-0 left-0 z-50 bg-white shadow-2xl flex'
                  : 'static h-full flex shrink-0'
            }
          >
            {editorMode === 'form' ? (
              <FormGeneratorPanel
                formData={project.formData}
                onChangeForm={handleFormChange}
                onApplyRawQuote={handleApplyQuoteToLetterhead}
                onGenerateDocument={() => {
                  handleGenerateDocument();
                  if (!isDesktop) {
                    fitZoomToScreen();
                    setMobileFormPane('preview');
                  }
                }}
                onNewClient={handleNewClient}
                onSwitchToVisual={() => handleModeChange('visual')}
                onMobilePreview={
                  !isDesktop
                    ? () => {
                        fitZoomToScreen();
                        setMobileFormPane('preview');
                      }
                    : undefined
                }
              />
            ) : (
              <LeftSidebar
                activeTab={leftTab}
                onTabChange={setLeftTab}
                elements={project.elements}
                deletedElements={visibleDeletedElements}
                activePage={activePage}
                selectedId={selectedId}
                onSelectElement={(id) => {
                  handleSelectElement(id);
                  if (!isDesktop) setMobileDrawer('none');
                }}
                onToggleVisibility={(id) => {
                  const el = project.elements.find((e) => e.id === id);
                  if (el) handleUpdateElement(id, { visible: !el.visible });
                }}
                onToggleLock={(id) => {
                  const el = project.elements.find((e) => e.id === id);
                  if (el) handleUpdateElement(id, { locked: !el.locked });
                }}
                onDuplicateElement={handleDuplicateElement}
                onDeleteElement={handleDeleteElement}
                onRestoreElement={handleRestoreElement}
                onReorderElement={(id, act) => handleReorderElement(id, act)}
                onAddPresetElement={(kind) => {
                  handleAddPresetElement(kind);
                  if (!isDesktop) setMobileDrawer('none');
                }}
                onUploadImage={(file) => {
                  handleImportFile(file);
                  if (!isDesktop) setMobileDrawer('none');
                }}
                onApplyFontPairing={handleApplyFontPairing}
                onCloseDrawer={!isDesktop ? () => setMobileDrawer('none') : undefined}
              />
            )}
          </div>
        )}

        {/* Central A4 Document Canvas / Live Preview */}
        {(isDesktop || editorMode === 'visual' || mobileFormPane === 'preview') && (
          <main className="flex-1 flex flex-col min-w-0 min-h-0 relative isolate">
            <A4Canvas
              interactive={editorMode === 'visual'}
              pageSettings={project.pageSettings}
              pageCount={project.pageCount}
              activePage={activePage}
              elements={project.elements}
              selectedId={selectedId}
              highlightedIds={highlightedIds}
              zoom={zoom}
              onSelectPage={setActivePage}
              onSelectElement={handleSelectElement}
              onUpdateElement={handleUpdateElement}
              onCommitElementHistory={handleCommitElementHistory}
            />

            {/* Mobile Visual Editor Bottom Control Bar */}
            {!isDesktop && editorMode === 'visual' && (
              <div className="bg-white border-t border-slate-200 px-2 py-1.5 flex items-center justify-between gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => setMobileDrawer((d) => (d === 'left' ? 'none' : 'left'))}
                  aria-expanded={mobileDrawer === 'left'}
                  className={`flex items-center gap-1 px-2.5 py-1.5 rounded-md text-xs font-semibold border ${
                    mobileDrawer === 'left'
                      ? 'bg-orange-50 text-[#C45911] border-orange-200'
                      : 'bg-white text-slate-700 border-slate-200'
                  }`}
                >
                  <Layers className="w-3.5 h-3.5" />
                  Layers
                </button>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={handleUndo}
                    disabled={!canUndo}
                    aria-label="Mobile Undo"
                    className="p-1.5 rounded-md border border-slate-200 text-slate-700 disabled:opacity-35"
                  >
                    <Undo2 className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={handleRedo}
                    disabled={!canRedo}
                    aria-label="Mobile Redo"
                    className="p-1.5 rounded-md border border-slate-200 text-slate-700 disabled:opacity-35"
                  >
                    <Redo2 className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setZoom((z) => Math.max(0.35, Number((z - 0.08).toFixed(2))))}
                    aria-label="Mobile Zoom out"
                    className="p-1.5 rounded-md border border-slate-200 text-slate-700"
                  >
                    <ZoomOut className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={fitZoomToScreen}
                    aria-label="Fit page to screen"
                    className="px-2 py-1 rounded-md border border-slate-200 text-[11px] font-mono tabular-nums text-slate-700 flex items-center gap-1"
                  >
                    <Maximize2 className="w-3 h-3" />
                    {Math.round(zoom * 100)}%
                  </button>
                  <button
                    type="button"
                    onClick={() => setZoom((z) => Math.min(1.5, Number((z + 0.08).toFixed(2))))}
                    aria-label="Mobile Zoom in"
                    className="p-1.5 rounded-md border border-slate-200 text-slate-700"
                  >
                    <ZoomIn className="w-3.5 h-3.5" />
                  </button>
                </div>

                <button
                  type="button"
                  onClick={() => setMobileDrawer((d) => (d === 'right' ? 'none' : 'right'))}
                  aria-expanded={mobileDrawer === 'right'}
                  className={`flex items-center gap-1 px-2.5 py-1.5 rounded-md text-xs font-semibold border ${
                    mobileDrawer === 'right'
                      ? 'bg-orange-50 text-[#C45911] border-orange-200'
                      : 'bg-white text-slate-700 border-slate-200'
                  }`}
                >
                  <SlidersHorizontal className="w-3.5 h-3.5" />
                  Format
                </button>
              </div>
            )}

            {/* Non-blocking status notification */}
            {notice && (
              <div
                role="status"
                className={`fixed ${
                  !isDesktop && editorMode === 'visual' ? 'bottom-14' : 'bottom-4'
                } left-4 ${
                  editorMode === 'form' && desktopLeftOpen ? 'lg:left-[25rem]' : ''
                } ${
                  editorMode === 'visual' && desktopLeftOpen ? 'lg:left-[19rem]' : ''
                } z-50 px-3.5 py-2 rounded-lg shadow-lg text-xs font-medium border ${
                  notice.tone === 'error'
                    ? 'bg-red-950 text-white border-red-800'
                    : 'bg-slate-900 text-white border-slate-700'
                }`}
              >
                {notice.text}
              </div>
            )}
          </main>
        )}

        {/* Notice when Form is full-screen on phone */}
        {!isDesktop && editorMode === 'form' && mobileFormPane === 'form' && notice && (
          <div
            role="status"
            className={`fixed bottom-16 left-4 right-4 z-50 px-3.5 py-2 rounded-lg shadow-lg text-xs font-medium border text-center ${
              notice.tone === 'error'
                ? 'bg-red-950 text-white border-red-800'
                : 'bg-slate-900 text-white border-slate-700'
            }`}
          >
            {notice.text}
          </div>
        )}

        {/* Right Properties & Design Assistant Inspector (Mode 2 Visual Editor) */}
        {editorMode === 'visual' &&
          ((isDesktop && desktopRightOpen) || (!isDesktop && mobileDrawer === 'right')) && (
          <div
            className={
              !isDesktop
                ? 'fixed top-14 bottom-0 right-0 z-50 bg-white shadow-2xl flex'
                : 'static h-full flex shrink-0'
            }
          >
            <RightSidebar
              activeTab={rightTab}
              onTabChange={setRightTab}
              suggestions={suggestions}
              layoutMode={project.layoutMode}
              onApplySuggestion={(s) => {
                if (!s.applyFix) return;
                commitProjectChange((prev) => {
                  const next = s.applyFix!(prev);
                  let nextForm = next.formData;
                  for (const nextEl of next.elements) {
                    const prevEl = prev.elements.find((p) => p.id === nextEl.id);
                    if (
                      nextEl.content !== undefined &&
                      nextEl.content !== prevEl?.content
                    ) {
                      nextForm = syncElementEditToForm(nextForm, nextEl.role, nextEl.content);
                      if (
                        nextEl.role === 'contact' &&
                        nextForm.phoneNumber.trim() === '0727 4'
                      ) {
                        nextForm = {
                          ...nextForm,
                          phoneNumber: DEFAULT_FORM_DATA.phoneNumber,
                        };
                      }
                    }
                  }
                  return { ...next, formData: nextForm };
                });
                showNotice(`Applied fix: ${s.fixLabel}`);
              }}
              onApplyFullRedesign={() => {
                commitProjectChange((prev) => applyCompleteRedesign(prev));
                setDeletedElements([]);
                showNotice('Applied complete Grashy Caterers professional redesign');
              }}
              onResetOriginal={() => {
                commitProjectChange(() => createOriginalGrashyProject());
                setDeletedElements([]);
                setActivePage(0);
                setSelectedId(null);
                showNotice('Restored original uploaded PDF layout');
              }}
              onLocateElements={(ids) => {
                setHighlightedIds(ids);
                if (ids.length > 0) setSelectedId(ids[0]);
                if (!isDesktop) setMobileDrawer('none');
              }}
              selectedElement={selectedElement}
              onUpdateElement={handleUpdateElement}
              onAlignElement={handleAlignElement}
              onReorderElement={handleReorderElement}
              onDuplicateElement={handleDuplicateElement}
              onDeleteElement={handleDeleteElement}
              onReplaceImage={handleReplaceImage}
              pageSettings={project.pageSettings}
              onUpdatePageSettings={handleUpdatePageSettings}
              pageCount={project.pageCount}
              activePage={activePage}
              onAddPage={handleAddPage}
              onDeletePage={handleDeletePage}
              onCloseDrawer={!isDesktop ? () => setMobileDrawer('none') : undefined}
            />
          </div>
        )}
      </div>

      {/* Sync, Version History, Multi-Document, Templates & Cloud Account Center */}
      <SyncStatusModal
        isOpen={syncModalOpen}
        onClose={() => setSyncModalOpen(false)}
        syncStatus={syncStatus}
        activeDocumentId={project.id}
        documents={dbDocuments}
        clients={dbClients}
        templates={dbTemplates}
        versions={dbVersions}
        onSyncNow={() => handleSyncNow(false)}
        onSwitchDocument={async (docId) => {
          await saveLocalDocumentWorkingCopy(project);
          const target = await localDb.documents.get(docId);
          if (!target) return;
          await setActiveDocumentId(docId);
          skipNextAutoDbWriteRef.current = true;
          dispatch({ type: 'LOAD_DOCUMENT', project: target.project });
          setDeletedElements([]);
          setSelectedId(null);
          setActivePage(0);
          await refreshWorkspaceSnapshot();
          showNotice(`Opened "${target.name}"`);
        }}
        onCreateNewDocument={async () => {
          await saveLocalDocumentWorkingCopy(project);
          const created = await createNewLocalDocument();
          skipNextAutoDbWriteRef.current = true;
          dispatch({ type: 'LOAD_DOCUMENT', project: created.project });
          setDeletedElements([]);
          setSelectedId(null);
          setActivePage(0);
          await refreshWorkspaceSnapshot();
          showNotice(`Created "${created.name}"`);
        }}
        onDeleteDocument={async (docId) => {
          await deleteLocalDocumentById(docId);
          const snap = await refreshWorkspaceSnapshot();
          if (docId === project.id && snap?.activeDocument) {
            skipNextAutoDbWriteRef.current = true;
            dispatch({ type: 'LOAD_DOCUMENT', project: snap.activeDocument.project });
            setDeletedElements([]);
            setSelectedId(null);
          }
          showNotice('Moved document to trash & queued sync');
        }}
        onSaveVersionSnapshot={async (summary) => {
          await saveLocalDocumentWorkingCopy(project, {
            recordVersion: true,
            versionSummary: summary || 'Manual snapshot',
          });
          await refreshWorkspaceSnapshot();
          showNotice('Saved new document version snapshot');
        }}
        onRestoreVersion={(ver) => {
          commitProjectChange(() => ({
            ...ver.snapshot,
            id: project.id,
            updatedAt: new Date().toISOString(),
          }));
          setDeletedElements([]);
          showNotice(`Restored version v${ver.version}`);
        }}
        onApplyTemplate={(tpl) => {
          commitProjectChange((prev) => ({
            ...prev,
            layoutMode: tpl.layoutMode,
            pageSettings: tpl.pageSettings,
            elements: syncFormToElements(tpl.elements, prev.formData),
          }));
          setDeletedElements([]);
          showNotice(`Applied template "${tpl.name}" while keeping client data`);
        }}
        onSaveCurrentAsTemplate={async (name) => {
          await saveTemplateFromProjectToDb(project, name);
          await refreshWorkspaceSnapshot();
          showNotice(`Saved reusable template "${name}"`);
        }}
        onApplyClientToForm={(client) => {
          handleFormChange({
            clientName: client.name,
            venue: client.venue || project.formData.venue,
            pax: client.defaultPax || project.formData.pax,
          });
          showNotice(`Loaded client "${client.name}" into form`);
        }}
        onResolveConflict={async (docId, strategy) => {
          const resolved = await resolveConflictInDb(docId, strategy);
          const snap = await refreshWorkspaceSnapshot();
          if (resolved && resolved.id === project.id) {
            skipNextAutoDbWriteRef.current = true;
            dispatch({ type: 'LOAD_DOCUMENT', project: resolved.project });
          } else if (snap?.activeDocument.id === project.id) {
            skipNextAutoDbWriteRef.current = true;
            dispatch({ type: 'LOAD_DOCUMENT', project: snap.activeDocument.project });
          }
          showNotice('Conflict resolved safely without data loss');
        }}
        onSignIn={async (email, password) => {
          const user = await signInCloud(email, password);
          cloudUserRef.current = user;
          setCloudUser(user);
          await handleSyncNow(false, user);
        }}
        onSignUp={async (email, password) => {
          const res = await signUpCloud(email, password);
          if (res.session) {
            cloudUserRef.current = res.session;
            setCloudUser(res.session);
            await handleSyncNow(false, res.session);
          }
        }}
        onSignOut={async () => {
          await signOutCloud();
          cloudUserRef.current = null;
          setCloudUser(null);
          showNotice('Signed out of cloud sync — continuing offline locally');
        }}
      />
    </div>
  );
}
