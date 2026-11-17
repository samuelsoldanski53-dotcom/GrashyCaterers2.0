import { LetterheadElement, LetterheadProject, PageSettings } from './letterhead';

export type RecordSyncStatus = 'synced' | 'pending' | 'conflict' | 'error';

export type SyncConnectionState =
  | 'synced'
  | 'offline'
  | 'syncing'
  | 'pending'
  | 'error';

export type SyncEntityType = 'document' | 'client' | 'template' | 'version';

export interface LocalClientRecord {
  id: string;
  name: string;
  venue: string;
  defaultPax: string;
  contactPerson: string;
  phoneNumber: string;
  email: string;
  updatedAt: string;
  syncStatus: RecordSyncStatus;
  isDeleted?: boolean;
}

export interface LocalTemplateRecord {
  id: string;
  name: string;
  layoutMode: 'original' | 'refined' | 'custom';
  pageSettings: PageSettings;
  elements: LetterheadElement[];
  isDefault: boolean;
  updatedAt: string;
  syncStatus: RecordSyncStatus;
  isDeleted?: boolean;
}

export interface LocalDocumentRecord {
  id: string;
  name: string;
  clientId?: string;
  templateId?: string;
  version: number;
  baseCloudVersion: number;
  updatedAt: string;
  syncedAt?: string;
  syncStatus: RecordSyncStatus;
  isDeleted?: boolean;
  isBootSeed?: boolean;
  storageBackupPath?: string;
  project: LetterheadProject;
  conflictRemote?: {
    version: number;
    updatedAt: string;
    name: string;
    project: LetterheadProject;
  };
  lastError?: string;
}

export interface DocumentVersionRecord {
  id: string;
  documentId: string;
  version: number;
  summary: string;
  createdAt: string;
  snapshot: LetterheadProject;
  syncStatus: RecordSyncStatus;
}

export interface SyncQueueItem {
  id: string;
  entityType: SyncEntityType;
  entityId: string;
  operation: 'upsert' | 'delete';
  createdAt: string;
  attempts: number;
  lastError?: string;
}

export interface MetaRecord {
  key: string;
  value: string;
}

export interface CloudUserSession {
  userId: string;
  email: string;
}

export interface SyncStatusSnapshot {
  state: SyncConnectionState;
  label: string;
  shortLabel: string;
  isOnline: boolean;
  isSyncing: boolean;
  supabaseConfigured: boolean;
  isAuthenticated: boolean;
  userEmail: string | null;
  pendingCount: number;
  conflictCount: number;
  lastSyncedAt: string | null;
  lastError: string | null;
}

export type ConflictStrategy = 'keep_local' | 'keep_remote' | 'keep_both';
