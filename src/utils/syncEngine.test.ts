import test from 'node:test';
import assert from 'node:assert/strict';
import { createRedesignedGrashyProject } from '../data/grashyInitialProject.ts';
import { LocalDocumentRecord } from '../types/sync.ts';
import {
  coalesceSyncQueue,
  deriveSyncStatusSnapshot,
  detectDocumentConflict,
  extractClientFromFormData,
  fromCloudId,
  resolveClientForDocument,
  resolveDocumentConflict,
  sortSyncQueueByDependencies,
  toCloudId,
} from './syncEngine.ts';

function makeLocalDoc(overrides: Partial<LocalDocumentRecord> = {}): LocalDocumentRecord {
  const project = createRedesignedGrashyProject();
  return {
    id: project.id,
    name: project.name,
    version: 2,
    baseCloudVersion: 2,
    updatedAt: '2026-11-17T08:00:00.000Z',
    syncStatus: 'synced',
    project,
    ...overrides,
  };
}

test('deriveSyncStatusSnapshot exposes exact user-facing connection states', () => {
  const offline = deriveSyncStatusSnapshot({
    isOnline: false,
    isSyncing: false,
    supabaseConfigured: true,
    isAuthenticated: true,
    userEmail: 'samuelm3545@gmail.com',
    pendingCount: 2,
    conflictCount: 0,
    lastSyncedAt: null,
    lastError: null,
  });
  assert.equal(offline.state, 'offline');
  assert.equal(offline.label, 'Offline – Working locally');

  const syncing = deriveSyncStatusSnapshot({
    isOnline: true,
    isSyncing: true,
    supabaseConfigured: true,
    isAuthenticated: true,
    userEmail: 'samuelm3545@gmail.com',
    pendingCount: 2,
    conflictCount: 0,
    lastSyncedAt: null,
    lastError: null,
  });
  assert.equal(syncing.state, 'syncing');
  assert.equal(syncing.label, 'Syncing – Uploading changes');

  const pending = deriveSyncStatusSnapshot({
    isOnline: true,
    isSyncing: false,
    supabaseConfigured: true,
    isAuthenticated: true,
    userEmail: 'samuelm3545@gmail.com',
    pendingCount: 3,
    conflictCount: 0,
    lastSyncedAt: null,
    lastError: null,
  });
  assert.equal(pending.state, 'pending');
  assert.equal(pending.label, 'Pending – Changes awaiting synchronization');

  const err = deriveSyncStatusSnapshot({
    isOnline: true,
    isSyncing: false,
    supabaseConfigured: true,
    isAuthenticated: true,
    userEmail: 'samuelm3545@gmail.com',
    pendingCount: 1,
    conflictCount: 0,
    lastSyncedAt: null,
    lastError: 'Network timeout',
  });
  assert.equal(err.state, 'error');
  assert.equal(err.label, 'Error – Synchronization failed');

  const synced = deriveSyncStatusSnapshot({
    isOnline: true,
    isSyncing: false,
    supabaseConfigured: true,
    isAuthenticated: true,
    userEmail: 'samuelm3545@gmail.com',
    pendingCount: 0,
    conflictCount: 0,
    lastSyncedAt: '2026-11-17T08:10:00.000Z',
    lastError: null,
  });
  assert.equal(synced.state, 'synced');
  assert.equal(synced.label, 'Online – Synced');
});

test('detectDocumentConflict never overwrites diverged local pending edits with newer remote changes', () => {
  const localPendingDiverged = makeLocalDoc({
    version: 3,
    baseCloudVersion: 2,
    syncStatus: 'pending',
  });
  assert.equal(
    detectDocumentConflict(localPendingDiverged, {
      version: 4,
      updatedAt: '2026-11-17T08:05:00.000Z',
    }),
    'conflict'
  );

  // Synced local document cleanly pulls newer cloud version
  const localSynced = makeLocalDoc({
    version: 2,
    baseCloudVersion: 2,
    syncStatus: 'synced',
  });
  assert.equal(
    detectDocumentConflict(localSynced, {
      version: 3,
      updatedAt: '2026-11-17T08:05:00.000Z',
    }),
    'pull_remote'
  );

  // Pending local edits with unadvanced remote version push cleanly
  const localPendingAhead = makeLocalDoc({
    version: 3,
    baseCloudVersion: 2,
    syncStatus: 'pending',
  });
  assert.equal(
    detectDocumentConflict(localPendingAhead, {
      version: 2,
      updatedAt: '2026-11-17T08:00:00.000Z',
    }),
    'push_local'
  );
});

test('resolveDocumentConflict preserves unsynced local work as version snapshot or duplicate copy', () => {
  const remoteProject = createRedesignedGrashyProject();
  remoteProject.formData.clientName = 'Remote Client Ltd';

  const conflicted = makeLocalDoc({
    version: 3,
    baseCloudVersion: 2,
    syncStatus: 'conflict',
    conflictRemote: {
      version: 4,
      updatedAt: '2026-11-17T08:15:00.000Z',
      name: 'Remote Doc',
      project: remoteProject,
    },
  });

  // keep_remote must preserve local v3 into archivedVersion!
  const remoteChoice = resolveDocumentConflict(conflicted, 'keep_remote', '2026-11-17T08:20:00.000Z');
  assert.equal(remoteChoice.updatedPrimary.project.formData.clientName, 'Remote Client Ltd');
  assert.equal(remoteChoice.updatedPrimary.syncStatus, 'synced');
  assert.ok(remoteChoice.archivedVersion);
  assert.equal(remoteChoice.archivedVersion!.version, 3);

  // keep_local bumps past remote v4 -> v5 and queues push
  const localChoice = resolveDocumentConflict(conflicted, 'keep_local', '2026-11-17T08:20:00.000Z');
  assert.equal(localChoice.updatedPrimary.version, 5);
  assert.equal(localChoice.updatedPrimary.syncStatus, 'pending');
  assert.ok(localChoice.archivedVersion);

  // keep_both creates independent copy of local and syncs primary to remote
  const bothChoice = resolveDocumentConflict(conflicted, 'keep_both', '2026-11-17T08:20:00.000Z');
  assert.ok(bothChoice.duplicateCopy);
  assert.equal(bothChoice.duplicateCopy!.syncStatus, 'pending');
  assert.equal(bothChoice.updatedPrimary.version, 4);
});

test('coalesceSyncQueue keeps single latest pending operation per entity', () => {
  const q1 = coalesceSyncQueue([], {
    entityType: 'document',
    entityId: 'doc-1',
    operation: 'upsert',
    createdAt: '2026-11-17T08:00:00.000Z',
  });
  const q2 = coalesceSyncQueue(q1, {
    entityType: 'document',
    entityId: 'doc-1',
    operation: 'delete',
    createdAt: '2026-11-17T08:01:00.000Z',
  });
  assert.equal(q2.length, 1);
  assert.equal(q2[0].operation, 'delete');
});

test('extractClientFromFormData normalizes client directory entry', () => {
  const project = createRedesignedGrashyProject();
  const client = extractClientFromFormData(project.formData, undefined, '2026-11-17T08:00:00.000Z');
  assert.ok(client);
  assert.equal(client!.id, 'client-sunculture-kenya-ltd');
  assert.equal(client!.name, 'Sunculture Kenya Ltd');
  assert.equal(client!.venue, 'Muthangari Drive');
  assert.equal(client!.defaultPax, '200');
});

test('toCloudId and fromCloudId isolate records per userId idempotently', () => {
  const uid = 'user-123';
  const cloud = toCloudId(uid, 'client-sunculture-kenya-ltd');
  assert.equal(cloud, 'user-123:client-sunculture-kenya-ltd');
  // Calling toCloudId twice must never double-prefix
  assert.equal(toCloudId(uid, cloud), cloud);
  assert.equal(fromCloudId(uid, cloud), 'client-sunculture-kenya-ltd');
  // Other user IDs remain untouched by fromCloudId
  assert.equal(fromCloudId('user-999', cloud), cloud);
});

test('resolveClientForDocument repairs documents referencing clients missing from local store', () => {
  const doc = makeLocalDoc({
    clientId: 'client-legacy-missing',
    syncStatus: 'pending',
  });
  doc.project.formData.clientName = 'Savannah Events Co';

  const emptyClients = new Map();
  const repaired = resolveClientForDocument(doc, emptyClients, '2026-11-17T10:00:00.000Z');
  assert.ok(repaired.clientRecord);
  assert.equal(repaired.clientRecord!.id, 'client-legacy-missing');
  assert.equal(repaired.clientRecord!.name, 'Savannah Events Co');
  assert.equal(repaired.resolvedClientId, 'client-legacy-missing');
  assert.equal(repaired.needsLocalClientInsert, true);
  assert.equal(repaired.needsLocalDocRepair, false);

  // Dangling clientId with blank formData.clientName resolves cleanly to undefined
  const blankDoc = makeLocalDoc({ clientId: 'client-orphan', syncStatus: 'pending' });
  blankDoc.project.formData.clientName = '';
  const cleared = resolveClientForDocument(blankDoc, emptyClients, '2026-11-17T10:00:00.000Z');
  assert.equal(cleared.clientRecord, null);
  assert.equal(cleared.resolvedClientId, undefined);
  assert.equal(cleared.needsLocalDocRepair, true);
});

test('sortSyncQueueByDependencies orders clients and templates before documents and versions', () => {
  const sorted = sortSyncQueueByDependencies([
    {
      id: 'version:v1',
      entityType: 'version',
      entityId: 'v1',
      operation: 'upsert',
      createdAt: '2026-11-17T08:00:01.000Z',
      attempts: 0,
    },
    {
      id: 'document:d1',
      entityType: 'document',
      entityId: 'd1',
      operation: 'upsert',
      createdAt: '2026-11-17T08:00:00.000Z',
      attempts: 0,
    },
    {
      id: 'template:t1',
      entityType: 'template',
      entityId: 't1',
      operation: 'upsert',
      createdAt: '2026-11-17T08:00:03.000Z',
      attempts: 0,
    },
    {
      id: 'client:c1',
      entityType: 'client',
      entityId: 'c1',
      operation: 'upsert',
      createdAt: '2026-11-17T08:00:04.000Z',
      attempts: 0,
    },
  ]);

  assert.deepEqual(
    sorted.map((s) => s.entityType),
    ['client', 'template', 'document', 'version']
  );
});
