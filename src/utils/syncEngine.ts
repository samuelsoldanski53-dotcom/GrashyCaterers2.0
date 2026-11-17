import { LetterheadFormData, LetterheadProject } from '../types/letterhead';
import {
  ConflictStrategy,
  DocumentVersionRecord,
  LocalClientRecord,
  LocalDocumentRecord,
  SyncConnectionState,
  SyncQueueItem,
  SyncStatusSnapshot,
} from '../types/sync';

export type DocumentReconciliationAction =
  | 'no_change'
  | 'push_local'
  | 'pull_remote'
  | 'conflict';

export function deriveSyncStatusSnapshot(params: {
  isOnline: boolean;
  isSyncing: boolean;
  supabaseConfigured: boolean;
  isAuthenticated: boolean;
  userEmail: string | null;
  pendingCount: number;
  conflictCount: number;
  lastSyncedAt: string | null;
  lastError: string | null;
}): SyncStatusSnapshot {
  const {
    isOnline,
    isSyncing,
    supabaseConfigured,
    isAuthenticated,
    userEmail,
    pendingCount,
    conflictCount,
    lastSyncedAt,
    lastError,
  } = params;

  let state: SyncConnectionState;
  let label: string;
  let shortLabel: string;

  if (!isOnline) {
    state = 'offline';
    label = 'Offline – Working locally';
    shortLabel = pendingCount > 0 ? `Offline (${pendingCount})` : 'Offline';
  } else if (isSyncing) {
    state = 'syncing';
    label = 'Syncing – Uploading changes';
    shortLabel = 'Syncing...';
  } else if (lastError || conflictCount > 0) {
    state = 'error';
    label =
      conflictCount > 0
        ? `Error – Synchronization failed (${conflictCount} conflict${conflictCount > 1 ? 's' : ''})`
        : 'Error – Synchronization failed';
    shortLabel = conflictCount > 0 ? `Conflict (${conflictCount})` : 'Sync Error';
  } else if (pendingCount > 0) {
    state = 'pending';
    label = 'Pending – Changes awaiting synchronization';
    shortLabel = `Pending (${pendingCount})`;
  } else {
    state = 'synced';
    label = 'Online – Synced';
    shortLabel = supabaseConfigured && isAuthenticated ? 'Synced' : 'Local Ready';
  }

  return {
    state,
    label,
    shortLabel,
    isOnline,
    isSyncing,
    supabaseConfigured,
    isAuthenticated,
    userEmail,
    pendingCount,
    conflictCount,
    lastSyncedAt,
    lastError,
  };
}

/**
 * Pure conflict detector ensuring newer or divergent cloud/local documents
 * are NEVER overwritten silently.
 */
export function detectDocumentConflict(
  localDoc: LocalDocumentRecord | undefined,
  remoteDoc: {
    version: number;
    updatedAt: string;
    isDeleted?: boolean;
  }
): DocumentReconciliationAction {
  if (!localDoc) {
    return remoteDoc.isDeleted ? 'no_change' : 'pull_remote';
  }

  if (localDoc.syncStatus === 'conflict') {
    return 'conflict';
  }

  const hasLocalUnsyncedChanges =
    localDoc.syncStatus === 'pending' || localDoc.syncStatus === 'error';
  const remoteAdvanced = remoteDoc.version > localDoc.baseCloudVersion;

  if (hasLocalUnsyncedChanges && remoteAdvanced) {
    return 'conflict';
  }

  if (hasLocalUnsyncedChanges) {
    return 'push_local';
  }

  if (remoteAdvanced || (remoteDoc.isDeleted && !localDoc.isDeleted)) {
    return 'pull_remote';
  }

  if (remoteDoc.version === localDoc.version && remoteDoc.updatedAt === localDoc.updatedAt) {
    return 'no_change';
  }

  if (remoteDoc.updatedAt > localDoc.updatedAt) {
    return 'pull_remote';
  }

  return 'no_change';
}

/**
 * Resolves a detected document conflict without losing either version:
 * - keep_local: bumps version past remote so cloud update is clean, keeps local state
 * - keep_remote: archives unsaved local draft into documentVersions before adopting remote
 * - keep_both: creates an independent local duplicate and restores original ID to remote
 */
export function resolveDocumentConflict(
  localDoc: LocalDocumentRecord,
  strategy: ConflictStrategy,
  nowIso: string = new Date().toISOString()
): {
  updatedPrimary: LocalDocumentRecord;
  archivedVersion?: DocumentVersionRecord;
  duplicateCopy?: LocalDocumentRecord;
} {
  const remote = localDoc.conflictRemote;
  if (!remote) {
    return {
      updatedPrimary: {
        ...localDoc,
        syncStatus: 'pending',
        updatedAt: nowIso,
      },
    };
  }

  if (strategy === 'keep_local') {
    const nextVersion = Math.max(localDoc.version, remote.version) + 1;
    const updatedProject: LetterheadProject = {
      ...localDoc.project,
      version: nextVersion,
      updatedAt: nowIso,
    };
    const archivedVersion: DocumentVersionRecord = {
      id: `${localDoc.id}-cloud-v${remote.version}-${Date.now()}`,
      documentId: localDoc.id,
      version: remote.version,
      summary: `Cloud v${remote.version} archived before keeping local changes`,
      createdAt: remote.updatedAt,
      snapshot: remote.project,
      syncStatus: 'synced',
    };
    return {
      updatedPrimary: {
        ...localDoc,
        version: nextVersion,
        baseCloudVersion: remote.version,
        updatedAt: nowIso,
        syncStatus: 'pending',
        conflictRemote: undefined,
        lastError: undefined,
        project: updatedProject,
      },
      archivedVersion,
    };
  }

  if (strategy === 'keep_remote') {
    const archivedVersion: DocumentVersionRecord = {
      id: `${localDoc.id}-local-backup-v${localDoc.version}-${Date.now()}`,
      documentId: localDoc.id,
      version: localDoc.version,
      summary: `Preserved local draft v${localDoc.version} before adopting cloud v${remote.version}`,
      createdAt: localDoc.updatedAt,
      snapshot: localDoc.project,
      syncStatus: 'pending',
    };
    return {
      updatedPrimary: {
        ...localDoc,
        name: remote.name,
        version: remote.version,
        baseCloudVersion: remote.version,
        updatedAt: remote.updatedAt,
        syncedAt: nowIso,
        syncStatus: 'synced',
        conflictRemote: undefined,
        lastError: undefined,
        project: remote.project,
      },
      archivedVersion,
    };
  }

  // keep_both
  const copyId = `${localDoc.id}-copy-${Date.now()}`;
  const duplicateProject: LetterheadProject = {
    ...localDoc.project,
    id: copyId,
    name: `${localDoc.name} (Local Copy)`,
    version: 1,
    updatedAt: nowIso,
  };
  const duplicateCopy: LocalDocumentRecord = {
    id: copyId,
    name: duplicateProject.name,
    clientId: localDoc.clientId,
    templateId: localDoc.templateId,
    version: 1,
    baseCloudVersion: 0,
    updatedAt: nowIso,
    syncStatus: 'pending',
    project: duplicateProject,
  };

  return {
    updatedPrimary: {
      ...localDoc,
      name: remote.name,
      version: remote.version,
      baseCloudVersion: remote.version,
      updatedAt: remote.updatedAt,
      syncedAt: nowIso,
      syncStatus: 'synced',
      conflictRemote: undefined,
      lastError: undefined,
      project: remote.project,
    },
    duplicateCopy,
  };
}

/**
 * Coalesces pending operations for the same entity so rapid typing/edits
 * produce one clean pending queue entry instead of hundreds.
 */
export function coalesceSyncQueue(
  existingQueue: SyncQueueItem[],
  incoming: Omit<SyncQueueItem, 'id' | 'attempts'> & { id?: string }
): SyncQueueItem[] {
  const remaining = existingQueue.filter(
    (item) => !(item.entityType === incoming.entityType && item.entityId === incoming.entityId)
  );
  const nextItem: SyncQueueItem = {
    id: incoming.id || `${incoming.entityType}:${incoming.entityId}`,
    entityType: incoming.entityType,
    entityId: incoming.entityId,
    operation: incoming.operation,
    createdAt: incoming.createdAt,
    attempts: 0,
    lastError: incoming.lastError,
  };
  return [...remaining, nextItem];
}

export function normalizeClientId(clientName: string): string {
  const slug = clientName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug ? `client-${slug}` : '';
}

export function extractClientFromFormData(
  formData: LetterheadFormData,
  existing?: LocalClientRecord,
  nowIso: string = new Date().toISOString()
): LocalClientRecord | null {
  const name = formData.clientName.trim();
  if (!name) return null;
  const id = existing?.id || normalizeClientId(name);
  if (!id) return null;

  return {
    id,
    name,
    venue: formData.venue.trim(),
    defaultPax: formData.pax.trim(),
    contactPerson: existing?.contactPerson || '',
    phoneNumber: existing?.phoneNumber || '',
    email: existing?.email || '',
    updatedAt: nowIso,
    syncStatus: 'pending',
    isDeleted: false,
  };
}

export function extractPaymentInfo(formData: LetterheadFormData) {
  return {
    transportCost: formData.transportCost,
    subtotalCost: formData.subtotalCost,
    vatAmount: formData.vatAmount,
    totalAmount: formData.totalAmount,
    depositAmount: formData.depositAmount,
    balance: formData.balance,
    paymentMethod: formData.paymentMethod,
    bankDetails: formData.bankDetails,
    mpesaDetails: formData.mpesaDetails,
    paymentInstructions: formData.paymentInstructions,
  };
}

export function toCloudId(userId: string, localId: string): string {
  const trimmedLocal = localId.trim();
  if (!trimmedLocal) return '';
  const prefix = `${userId}:`;
  return trimmedLocal.startsWith(prefix) ? trimmedLocal : `${prefix}${trimmedLocal}`;
}

export function fromCloudId(userId: string, cloudId: string): string {
  const prefix = `${userId}:`;
  return cloudId.startsWith(prefix) ? cloudId.slice(prefix.length) : cloudId;
}

export function resolveClientForDocument(
  doc: LocalDocumentRecord,
  clientsById: Map<string, LocalClientRecord>,
  nowIso: string = new Date().toISOString()
): {
  clientRecord: LocalClientRecord | null;
  resolvedClientId: string | undefined;
  needsLocalClientInsert: boolean;
  needsLocalDocRepair: boolean;
} {
  // 1. If doc.clientId already resolves in local clients table, use it directly
  if (doc.clientId) {
    const existingById = clientsById.get(doc.clientId);
    if (existingById) {
      return {
        clientRecord: existingById,
        resolvedClientId: existingById.id,
        needsLocalClientInsert: false,
        needsLocalDocRepair: false,
      };
    }
  }

  // 2. Otherwise reconstruct/repair from formData.clientName (preserving existing clientId if present)
  const extracted = extractClientFromFormData(doc.project.formData, undefined, nowIso);
  if (extracted) {
    const targetId = doc.clientId || extracted.id;
    const existingTarget = clientsById.get(targetId);
    if (existingTarget) {
      return {
        clientRecord: existingTarget,
        resolvedClientId: existingTarget.id,
        needsLocalClientInsert: false,
        needsLocalDocRepair: doc.clientId !== existingTarget.id,
      };
    }
    const repairedClient: LocalClientRecord = {
      ...extracted,
      id: targetId,
      syncStatus: 'pending',
    };
    return {
      clientRecord: repairedClient,
      resolvedClientId: repairedClient.id,
      needsLocalClientInsert: true,
      needsLocalDocRepair: doc.clientId !== repairedClient.id,
    };
  }

  // 3. No client referenced and no clientName in formData -> clear dangling clientId if any
  return {
    clientRecord: null,
    resolvedClientId: undefined,
    needsLocalClientInsert: false,
    needsLocalDocRepair: Boolean(doc.clientId),
  };
}

export function sortSyncQueueByDependencies(queue: SyncQueueItem[]): SyncQueueItem[] {
  const orderWeight: Record<string, number> = {
    client: 1,
    template: 2,
    document: 3,
    version: 4,
  };
  return [...queue].sort(
    (a, b) =>
      (orderWeight[a.entityType] || 9) - (orderWeight[b.entityType] || 9) ||
      a.createdAt.localeCompare(b.createdAt)
  );
}
