import React, { useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Cloud,
  CloudOff,
  FilePlus2,
  FolderOpen,
  History,
  LayoutTemplate,
  LogOut,
  RefreshCw,
  RotateCcw,
  Save,
  Trash2,
  Users,
  X,
} from 'lucide-react';
import {
  ConflictStrategy,
  DocumentVersionRecord,
  LocalClientRecord,
  LocalDocumentRecord,
  LocalTemplateRecord,
  SyncStatusSnapshot,
} from '../types/sync';
import { PWAInstallButton } from './PWAInstallButton';

interface SyncStatusModalProps {
  isOpen: boolean;
  onClose: () => void;
  syncStatus: SyncStatusSnapshot;
  activeDocumentId: string;
  documents: LocalDocumentRecord[];
  clients: LocalClientRecord[];
  templates: LocalTemplateRecord[];
  versions: DocumentVersionRecord[];
  onSyncNow: () => Promise<void>;
  onSwitchDocument: (docId: string) => void;
  onCreateNewDocument: () => void;
  onDeleteDocument: (docId: string) => void;
  onSaveVersionSnapshot: (summary?: string) => void;
  onRestoreVersion: (version: DocumentVersionRecord) => void;
  onApplyTemplate: (template: LocalTemplateRecord) => void;
  onSaveCurrentAsTemplate: (name: string) => void;
  onApplyClientToForm: (client: LocalClientRecord) => void;
  onResolveConflict: (docId: string, strategy: ConflictStrategy) => void;
  onSignIn: (email: string, password: string) => Promise<void>;
  onSignUp: (email: string, password: string) => Promise<void>;
  onSignOut: () => Promise<void>;
}

type ModalTab = 'sync' | 'documents' | 'versions' | 'templates' | 'clients';

export const SyncStatusModal: React.FC<SyncStatusModalProps> = ({
  isOpen,
  onClose,
  syncStatus,
  activeDocumentId,
  documents,
  clients,
  templates,
  versions,
  onSyncNow,
  onSwitchDocument,
  onCreateNewDocument,
  onDeleteDocument,
  onSaveVersionSnapshot,
  onRestoreVersion,
  onApplyTemplate,
  onSaveCurrentAsTemplate,
  onApplyClientToForm,
  onResolveConflict,
  onSignIn,
  onSignUp,
  onSignOut,
}) => {
  const [tab, setTab] = useState<ModalTab>('sync');
  const [templateName, setTemplateName] = useState('');
  const [snapshotNote, setSnapshotNote] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authMode, setAuthMode] = useState<'signin' | 'signup'>('signin');
  const [authBusy, setAuthBusy] = useState(false);
  const [authFeedback, setAuthFeedback] = useState<string | null>(null);

  if (!isOpen) return null;

  const conflictedDocs = documents.filter((d) => d.syncStatus === 'conflict');

  const statusTone =
    syncStatus.state === 'synced'
      ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
      : syncStatus.state === 'offline'
        ? 'bg-slate-100 border-slate-300 text-slate-800'
        : syncStatus.state === 'syncing'
          ? 'bg-sky-50 border-sky-200 text-sky-900'
          : syncStatus.state === 'pending'
            ? 'bg-amber-50 border-amber-200 text-amber-900'
            : 'bg-red-50 border-red-200 text-red-900';

  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthBusy(true);
    setAuthFeedback(null);
    try {
      if (authMode === 'signin') {
        await onSignIn(email.trim(), password);
        setAuthFeedback('Signed in — cloud synchronization active.');
      } else {
        await onSignUp(email.trim(), password);
        setAuthFeedback('Account created. Check email if confirmation is enabled.');
      }
      setPassword('');
    } catch (err) {
      setAuthFeedback(err instanceof Error ? err.message : 'Authentication error');
    } finally {
      setAuthBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/45 p-3 sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Offline Storage and Cloud Sync Center"
    >
      <div className="bg-white w-full max-w-2xl max-h-[88dvh] rounded-xl border border-slate-200 shadow-2xl flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between gap-2 bg-slate-50/70 shrink-0">
          <div className="flex items-center gap-2 min-w-0">
            {syncStatus.isOnline ? (
              <Cloud className="w-4 h-4 text-[#C45911] shrink-0" />
            ) : (
              <CloudOff className="w-4 h-4 text-slate-600 shrink-0" />
            )}
            <div className="min-w-0">
              <h2 className="text-sm font-semibold text-slate-900 truncate">
                Workspace Storage, Versions &amp; Cloud Sync
              </h2>
              <p className="text-[11px] text-slate-500 truncate">{syncStatus.label}</p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <PWAInstallButton compact />
            <button
              type="button"
              onClick={onClose}
              aria-label="Close sync center"
              className="p-1.5 rounded-md text-slate-500 hover:text-slate-900 hover:bg-slate-200/70"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div
          className="px-3 pt-2 border-b border-slate-200 flex items-center gap-1 overflow-x-auto bg-white shrink-0"
          role="tablist"
        >
          {(
            [
              ['sync', 'Sync & Cloud', RefreshCw],
              ['documents', `Docs (${documents.length})`, FolderOpen],
              ['versions', `Versions (${versions.length})`, History],
              ['templates', `Templates (${templates.length})`, LayoutTemplate],
              ['clients', `Clients (${clients.length})`, Users],
            ] as const
          ).map(([key, text, Icon]) => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              className={`flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 whitespace-nowrap transition-colors ${
                tab === key
                  ? 'border-[#C45911] text-[#C45911]'
                  : 'border-transparent text-slate-600 hover:text-slate-900'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {text}
            </button>
          ))}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Conflict Alert Banner (always visible if any conflict exists) */}
          {conflictedDocs.length > 0 && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 space-y-2.5">
              <div className="flex items-start gap-2">
                <AlertTriangle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                <div>
                  <h3 className="text-xs font-semibold text-red-900">
                    Version Conflict Detected ({conflictedDocs.length})
                  </h3>
                  <p className="text-[11px] text-red-800 mt-0.5">
                    Unsynchronized local edits diverge from a newer cloud revision. Choose how to
                    protect your work — no version will be overwritten silently.
                  </p>
                </div>
              </div>
              {conflictedDocs.map((doc) => (
                <div
                  key={doc.id}
                  className="rounded-md bg-white border border-red-200 p-2.5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2"
                >
                  <div className="text-xs">
                    <div className="font-semibold text-slate-900">{doc.name}</div>
                    <div className="text-[11px] text-slate-500">
                      Local v{doc.version} vs Cloud v{doc.conflictRemote?.version ?? '?'}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    <button
                      type="button"
                      onClick={() => onResolveConflict(doc.id, 'keep_local')}
                      className="px-2.5 py-1 rounded bg-[#C45911] text-white text-[11px] font-medium hover:bg-[#A8490C]"
                    >
                      Keep Local
                    </button>
                    <button
                      type="button"
                      onClick={() => onResolveConflict(doc.id, 'keep_remote')}
                      className="px-2.5 py-1 rounded border border-slate-300 text-slate-700 text-[11px] font-medium hover:bg-slate-50"
                    >
                      Use Cloud (Backup Local)
                    </button>
                    <button
                      type="button"
                      onClick={() => onResolveConflict(doc.id, 'keep_both')}
                      className="px-2.5 py-1 rounded border border-slate-300 text-slate-700 text-[11px] font-medium hover:bg-slate-50"
                    >
                      Save Both
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {tab === 'sync' && (
            <div className="space-y-4">
              {/* Status Card + Manual Sync Button */}
              <div className={`rounded-lg border p-3.5 space-y-3 ${statusTone}`}>
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2.5">
                  <div className="space-y-0.5">
                    <div className="text-xs font-semibold flex items-center gap-1.5">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      {syncStatus.label}
                    </div>
                    <div className="text-[11px] opacity-85 font-mono tabular-nums">
                      Pending queue: {syncStatus.pendingCount} · Last synced:{' '}
                      {syncStatus.lastSyncedAt
                        ? new Date(syncStatus.lastSyncedAt).toLocaleString()
                        : 'Not yet synced to cloud'}
                    </div>
                    {syncStatus.lastError && (
                      <div className="text-[11px] text-red-700 font-medium pt-0.5">
                        {syncStatus.lastError}
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={onSyncNow}
                    disabled={syncStatus.isSyncing}
                    className="inline-flex items-center justify-center gap-1.5 px-3.5 py-2 rounded-md bg-slate-900 hover:bg-slate-800 disabled:opacity-50 text-white text-xs font-semibold shrink-0 transition-colors"
                  >
                    <RefreshCw
                      className={`w-3.5 h-3.5 ${syncStatus.isSyncing ? 'animate-spin' : ''}`}
                    />
                    SYNC NOW
                  </button>
                </div>
              </div>

              {/* Offline Database Guarantee */}
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 space-y-1">
                <div className="font-semibold text-slate-900">
                  Offline-First IndexedDB (Dexie.js) Active
                </div>
                <p className="text-[11px] text-slate-600 leading-relaxed">
                  All quotations, client details, letterhead templates, payment summaries, and PDF/DOCX
                  exports work without internet. Pending edits are queued locally and uploaded
                  automatically when connection and cloud sign-in are active.
                </p>
              </div>

              {/* Supabase Auth Section */}
              <div className="rounded-lg border border-slate-200 p-3.5 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="text-xs font-semibold text-slate-900">
                    Supabase Cloud Synchronization
                  </h3>
                  {syncStatus.isAuthenticated && (
                    <button
                      type="button"
                      onClick={onSignOut}
                      className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-600 hover:text-red-700"
                    >
                      <LogOut className="w-3 h-3" />
                      Sign Out
                    </button>
                  )}
                </div>

                {!syncStatus.supabaseConfigured ? (
                  <p className="text-[11px] text-slate-600 leading-relaxed">
                    Cloud sync is ready once <code className="font-mono bg-slate-100 px-1 rounded">VITE_SUPABASE_URL</code> and{' '}
                    <code className="font-mono bg-slate-100 px-1 rounded">VITE_SUPABASE_ANON_KEY</code> are set in
                    environment variables and <code className="font-mono bg-slate-100 px-1 rounded">supabase/schema.sql</code> is
                    applied. Until then, every edit and version is stored safely offline in IndexedDB.
                  </p>
                ) : syncStatus.isAuthenticated ? (
                  <div className="text-xs text-slate-700 flex items-center justify-between bg-emerald-50/70 border border-emerald-200 rounded-md px-3 py-2">
                    <span>
                      Signed in as <strong>{syncStatus.userEmail}</strong>
                    </span>
                    <span className="text-[11px] text-emerald-700 font-medium">RLS Protected</span>
                  </div>
                ) : (
                  <form onSubmit={handleAuthSubmit} className="space-y-2.5">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <input
                        type="email"
                        required
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="Email address"
                        aria-label="Cloud sync email"
                        className="px-2.5 py-1.5 text-xs rounded-md border border-slate-200 focus:border-[#C45911] outline-none"
                      />
                      <input
                        type="password"
                        required
                        minLength={6}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="Password (min 6 chars)"
                        aria-label="Cloud sync password"
                        className="px-2.5 py-1.5 text-xs rounded-md border border-slate-200 focus:border-[#C45911] outline-none"
                      />
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <button
                        type="button"
                        onClick={() => setAuthMode((m) => (m === 'signin' ? 'signup' : 'signin'))}
                        className="text-[11px] text-[#C45911] hover:underline font-medium"
                      >
                        {authMode === 'signin'
                          ? 'Need an account? Create one'
                          : 'Already registered? Sign in'}
                      </button>
                      <button
                        type="submit"
                        disabled={authBusy}
                        className="px-3 py-1.5 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-xs font-semibold disabled:opacity-50"
                      >
                        {authBusy
                          ? 'Working...'
                          : authMode === 'signin'
                            ? 'Sign In & Sync'
                            : 'Create Cloud Account'}
                      </button>
                    </div>
                    {authFeedback && (
                      <p className="text-[11px] text-slate-700 font-medium">{authFeedback}</p>
                    )}
                  </form>
                )}
              </div>
            </div>
          )}

          {tab === 'documents' && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <p className="text-xs text-slate-600">
                  Switch between locally cached &amp; synced letterhead documents.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    onCreateNewDocument();
                    onClose();
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-xs font-semibold"
                >
                  <FilePlus2 className="w-3.5 h-3.5" />
                  New Document
                </button>
              </div>

              <div className="space-y-2">
                {documents.map((doc) => {
                  const isActive = doc.id === activeDocumentId;
                  return (
                    <div
                      key={doc.id}
                      className={`rounded-lg border p-3 flex items-center justify-between gap-3 ${
                        isActive
                          ? 'border-[#C45911] bg-orange-50/50'
                          : 'border-slate-200 bg-white hover:border-slate-300'
                      }`}
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-slate-900 truncate">
                            {doc.name}
                          </span>
                          <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">
                            v{doc.version}
                          </span>
                          <span
                            className={`text-[10px] font-medium px-1.5 py-0.5 rounded ${
                              doc.syncStatus === 'synced'
                                ? 'bg-emerald-50 text-emerald-700'
                                : doc.syncStatus === 'conflict'
                                  ? 'bg-red-50 text-red-700'
                                  : 'bg-amber-50 text-amber-800'
                            }`}
                          >
                            {doc.syncStatus}
                          </span>
                        </div>
                        <div className="text-[11px] text-slate-500 mt-0.5 truncate">
                          Client: {doc.project.formData.clientName || 'Unspecified'} · Updated{' '}
                          {new Date(doc.updatedAt).toLocaleString()}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0">
                        {!isActive && (
                          <button
                            type="button"
                            onClick={() => {
                              onSwitchDocument(doc.id);
                              onClose();
                            }}
                            className="px-2.5 py-1 rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-xs font-medium text-slate-800"
                          >
                            Open
                          </button>
                        )}
                        {documents.length > 1 && (
                          <button
                            type="button"
                            onClick={() => onDeleteDocument(doc.id)}
                            aria-label={`Delete ${doc.name}`}
                            className="p-1.5 rounded-md text-slate-400 hover:text-red-600 hover:bg-red-50"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {tab === 'versions' && (
            <div className="space-y-3">
              <div className="flex gap-2">
                <input
                  type="text"
                  value={snapshotNote}
                  onChange={(e) => setSnapshotNote(e.target.value)}
                  placeholder="Optional version note (e.g. Sent revised quote to client)"
                  className="flex-1 px-2.5 py-1.5 text-xs rounded-md border border-slate-200 focus:border-[#C45911] outline-none"
                />
                <button
                  type="button"
                  onClick={() => {
                    onSaveVersionSnapshot(snapshotNote.trim() || undefined);
                    setSnapshotNote('');
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold shrink-0"
                >
                  <Save className="w-3.5 h-3.5" />
                  Save Snapshot
                </button>
              </div>

              <div className="space-y-2">
                {versions.map((ver) => (
                  <div
                    key={ver.id}
                    className="rounded-lg border border-slate-200 bg-white p-3 flex items-center justify-between gap-2"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-semibold text-slate-900 flex items-center gap-2">
                        <span className="font-mono text-[#C45911]">v{ver.version}</span>
                        <span className="truncate">{ver.summary || 'Saved version'}</span>
                      </div>
                      <div className="text-[11px] text-slate-500 mt-0.5">
                        {new Date(ver.createdAt).toLocaleString()} · Sync: {ver.syncStatus}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        onRestoreVersion(ver);
                        onClose();
                      }}
                      className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-700 shrink-0"
                    >
                      <RotateCcw className="w-3 h-3" />
                      Restore
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'templates' && (
            <div className="space-y-3">
              <div className="flex gap-2">
                <input
                  type="text"
                  value={templateName}
                  onChange={(e) => setTemplateName(e.target.value)}
                  placeholder="New template name (saves current layout & branding)"
                  className="flex-1 px-2.5 py-1.5 text-xs rounded-md border border-slate-200 focus:border-[#C45911] outline-none"
                />
                <button
                  type="button"
                  onClick={() => {
                    if (!templateName.trim()) return;
                    onSaveCurrentAsTemplate(templateName.trim());
                    setTemplateName('');
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#C45911] hover:bg-[#A8490C] text-white text-xs font-semibold shrink-0"
                >
                  Save Template
                </button>
              </div>

              <div className="space-y-2">
                {templates.map((tpl) => (
                  <div
                    key={tpl.id}
                    className="rounded-lg border border-slate-200 bg-white p-3 flex items-center justify-between gap-2"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-semibold text-slate-900 truncate">
                        {tpl.name}
                      </div>
                      <div className="text-[11px] text-slate-500">
                        {tpl.elements.length} elements · Mode: {tpl.layoutMode}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        onApplyTemplate(tpl);
                        onClose();
                      }}
                      className="px-2.5 py-1 rounded-md border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-800 shrink-0"
                    >
                      Apply Layout
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'clients' && (
            <div className="space-y-2">
              {clients.length === 0 ? (
                <p className="text-xs text-slate-500">
                  Client records are automatically captured as you generate quotations.
                </p>
              ) : (
                clients.map((client) => (
                  <div
                    key={client.id}
                    className="rounded-lg border border-slate-200 bg-white p-3 flex items-center justify-between gap-2"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-semibold text-slate-900 truncate">
                        {client.name}
                      </div>
                      <div className="text-[11px] text-slate-500 truncate">
                        {client.venue || 'No venue saved'} · PAX: {client.defaultPax || '—'}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        onApplyClientToForm(client);
                        onClose();
                      }}
                      className="px-2.5 py-1 rounded-md border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-800 shrink-0"
                    >
                      Use Client
                    </button>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
