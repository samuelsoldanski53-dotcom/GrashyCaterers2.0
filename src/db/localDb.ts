import Dexie, { Table } from 'dexie';
import {
  createOriginalGrashyProject,
  createRedesignedGrashyProject,
} from '../data/grashyInitialProject';
import { LetterheadProject } from '../types/letterhead';
import {
  ConflictStrategy,
  DocumentVersionRecord,
  LocalClientRecord,
  LocalDocumentRecord,
  LocalTemplateRecord,
  MetaRecord,
  SyncEntityType,
  SyncQueueItem,
} from '../types/sync';
import {
  extractClientFromFormData,
  resolveClientForDocument,
  resolveDocumentConflict,
} from '../utils/syncEngine';

export class GrashyStudioDB extends Dexie {
  documents!: Table<LocalDocumentRecord, string>;
  clients!: Table<LocalClientRecord, string>;
  templates!: Table<LocalTemplateRecord, string>;
  documentVersions!: Table<DocumentVersionRecord, string>;
  syncQueue!: Table<SyncQueueItem, string>;
  meta!: Table<MetaRecord, string>;

  constructor() {
    super('grashy-studio-db');
    this.version(1).stores({
      documents: '&id, name, clientId, templateId, updatedAt, syncStatus, isDeleted',
      clients: '&id, name, updatedAt, syncStatus, isDeleted',
      templates: '&id, name, isDefault, updatedAt, syncStatus, isDeleted',
      documentVersions: '&id, documentId, version, createdAt, syncStatus',
      syncQueue: '&id, entityType, entityId, createdAt',
      meta: '&key',
    });
  }
}

export const localDb = new GrashyStudioDB();

const META_ACTIVE_DOC_ID = 'activeDocumentId';
const META_LAST_SYNCED_AT = 'lastSyncedAt';

export async function enqueueOperation(
  entityType: SyncEntityType,
  entityId: string,
  operation: 'upsert' | 'delete'
): Promise<void> {
  const queueId = `${entityType}:${entityId}`;
  await localDb.syncQueue.put({
    id: queueId,
    entityType,
    entityId,
    operation,
    createdAt: new Date().toISOString(),
    attempts: 0,
  });
}

function buildDefaultTemplates(): LocalTemplateRecord[] {
  const now = new Date().toISOString();
  const refined = createRedesignedGrashyProject();
  const original = createOriginalGrashyProject();
  return [
    {
      id: 'template-grashy-refined',
      name: 'Grashy Editorial Quotation (Refined)',
      layoutMode: 'refined',
      pageSettings: refined.pageSettings,
      elements: refined.elements,
      isDefault: true,
      updatedAt: now,
      syncStatus: 'pending',
    },
    {
      id: 'template-grashy-original',
      name: 'Grashy Uploaded PDF Brand Layout',
      layoutMode: 'original',
      pageSettings: original.pageSettings,
      elements: original.elements,
      isDefault: true,
      updatedAt: now,
      syncStatus: 'pending',
    },
  ];
}

export async function ensureLocalDatabaseSeeded(
  initialProject: LetterheadProject
): Promise<{
  activeDocument: LocalDocumentRecord;
  documents: LocalDocumentRecord[];
  clients: LocalClientRecord[];
  templates: LocalTemplateRecord[];
  versions: DocumentVersionRecord[];
  pendingCount: number;
  conflictCount: number;
  lastSyncedAt: string | null;
}> {
  const now = new Date().toISOString();

  await localDb.transaction(
    'rw',
    [
      localDb.documents,
      localDb.clients,
      localDb.templates,
      localDb.documentVersions,
      localDb.syncQueue,
      localDb.meta,
    ],
    async () => {
      const existingTemplateCount = await localDb.templates.count();
      if (existingTemplateCount === 0) {
        const defaults = buildDefaultTemplates();
        await localDb.templates.bulkPut(defaults);
        for (const tpl of defaults) {
          await enqueueOperation('template', tpl.id, 'upsert');
        }
      }

      const existingDocCount = await localDb.documents.count();
      if (existingDocCount === 0) {
        const docId = initialProject.id || `grashy-doc-${Date.now()}`;
        const normalizedProject: LetterheadProject = {
          ...initialProject,
          id: docId,
          version: Math.max(1, initialProject.version || 1),
          updatedAt: initialProject.updatedAt || now,
        };

        const clientRec = extractClientFromFormData(normalizedProject.formData, undefined, now);
        if (clientRec) {
          await localDb.clients.put(clientRec);
          await enqueueOperation('client', clientRec.id, 'upsert');
        }

        const docRecord: LocalDocumentRecord = {
          id: docId,
          name: normalizedProject.name,
          clientId: clientRec?.id,
          templateId:
            normalizedProject.layoutMode === 'original'
              ? 'template-grashy-original'
              : 'template-grashy-refined',
          version: normalizedProject.version,
          baseCloudVersion: 0,
          updatedAt: normalizedProject.updatedAt,
          syncStatus: 'pending',
          isBootSeed: true,
          project: normalizedProject,
        };

        await localDb.documents.put(docRecord);
        await localDb.meta.put({ key: META_ACTIVE_DOC_ID, value: docId });
        await enqueueOperation('document', docId, 'upsert');

        const initialVer: DocumentVersionRecord = {
          id: `${docId}-v${docRecord.version}`,
          documentId: docId,
          version: docRecord.version,
          summary: 'Initial local document',
          createdAt: now,
          snapshot: normalizedProject,
          syncStatus: 'pending',
        };
        await localDb.documentVersions.put(initialVer);
        await enqueueOperation('version', initialVer.id, 'upsert');
      }
    }
  );

  return getWorkspaceSnapshotFromDb();
}

export async function getWorkspaceSnapshotFromDb(): Promise<{
  activeDocument: LocalDocumentRecord;
  documents: LocalDocumentRecord[];
  clients: LocalClientRecord[];
  templates: LocalTemplateRecord[];
  versions: DocumentVersionRecord[];
  pendingCount: number;
  conflictCount: number;
  lastSyncedAt: string | null;
}> {
  const allDocs = await localDb.documents.toArray();
  const documents = allDocs
    .filter((d) => !d.isDeleted)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));

  const activeMeta = await localDb.meta.get(META_ACTIVE_DOC_ID);
  let activeDocument =
    (activeMeta && documents.find((d) => d.id === activeMeta.value)) || documents[0];

  if (!activeDocument) {
    const fresh = createRedesignedGrashyProject();
    activeDocument = {
      id: fresh.id,
      name: fresh.name,
      templateId: 'template-grashy-refined',
      version: 1,
      baseCloudVersion: 0,
      updatedAt: new Date().toISOString(),
      syncStatus: 'pending',
      project: fresh,
    };
    await localDb.documents.put(activeDocument);
    await localDb.meta.put({ key: META_ACTIVE_DOC_ID, value: activeDocument.id });
    documents.unshift(activeDocument);
  }

  const clients = (await localDb.clients.toArray())
    .filter((c) => !c.isDeleted)
    .sort((a, b) => a.name.localeCompare(b.name));

  const templates = (await localDb.templates.toArray())
    .filter((t) => !t.isDeleted)
    .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.name.localeCompare(b.name));

  const versions = (
    await localDb.documentVersions.where('documentId').equals(activeDocument.id).toArray()
  ).sort((a, b) => b.version - a.version || b.createdAt.localeCompare(a.createdAt));

  const pendingCount = await localDb.syncQueue.count();
  const conflictCount = documents.filter((d) => d.syncStatus === 'conflict').length;
  const lastSyncedMeta = await localDb.meta.get(META_LAST_SYNCED_AT);

  return {
    activeDocument,
    documents,
    clients,
    templates,
    versions,
    pendingCount,
    conflictCount,
    lastSyncedAt: lastSyncedMeta?.value || null,
  };
}

export async function setActiveDocumentId(documentId: string): Promise<void> {
  await localDb.meta.put({ key: META_ACTIVE_DOC_ID, value: documentId });
}

export async function saveLocalDocumentWorkingCopy(
  project: LetterheadProject,
  options?: {
    recordVersion?: boolean;
    versionSummary?: string;
    templateId?: string;
  }
): Promise<LocalDocumentRecord> {
  const now = new Date().toISOString();
  const docId = project.id || `grashy-doc-${Date.now()}`;

  return localDb.transaction(
    'rw',
    [
      localDb.documents,
      localDb.clients,
      localDb.documentVersions,
      localDb.syncQueue,
      localDb.meta,
    ],
    async () => {
      const existing = await localDb.documents.get(docId);

      // Extract/update client directory record
      const clientCandidate = extractClientFromFormData(project.formData, undefined, now);
      let resolvedClientId: string | undefined;
      if (clientCandidate) {
        const existingClient = await localDb.clients.get(clientCandidate.id);
        const mergedClient = extractClientFromFormData(project.formData, existingClient, now);
        if (mergedClient) {
          resolvedClientId = mergedClient.id;
          await localDb.clients.put(mergedClient);
          await enqueueOperation('client', mergedClient.id, 'upsert');
        }
      }

      const prevVersion = existing?.version ?? Math.max(1, project.version || 1);
      // Only bump version when a milestone/version snapshot is created OR first time marking pending after sync
      const shouldBumpVersion =
        Boolean(options?.recordVersion) || existing?.syncStatus === 'synced';
      const nextVersion = shouldBumpVersion ? prevVersion + 1 : prevVersion;

      const updatedProject: LetterheadProject = {
        ...project,
        id: docId,
        version: nextVersion,
        updatedAt: now,
      };

      const nextDoc: LocalDocumentRecord = {
        id: docId,
        name: updatedProject.name || 'Untitled Letterhead',
        clientId: resolvedClientId,
        templateId: options?.templateId || existing?.templateId || 'template-grashy-refined',
        version: nextVersion,
        baseCloudVersion: existing?.baseCloudVersion ?? 0,
        updatedAt: now,
        syncedAt: existing?.syncedAt,
        // Preserve conflict status until explicitly resolved so local divergence isn't hidden
        syncStatus: existing?.syncStatus === 'conflict' ? 'conflict' : 'pending',
        isBootSeed: false,
        conflictRemote: existing?.conflictRemote,
        storageBackupPath: existing?.storageBackupPath,
        project: updatedProject,
      };

      await localDb.documents.put(nextDoc);
      await localDb.meta.put({ key: META_ACTIVE_DOC_ID, value: docId });
      await enqueueOperation('document', docId, 'upsert');

      if (options?.recordVersion) {
        const versionId = `${docId}-v${nextVersion}-${Date.now()}`;
        const versionRec: DocumentVersionRecord = {
          id: versionId,
          documentId: docId,
          version: nextVersion,
          summary:
            options.versionSummary ||
            (updatedProject.formData.clientName
              ? `Saved version for ${updatedProject.formData.clientName}`
              : `Saved document v${nextVersion}`),
          createdAt: now,
          snapshot: updatedProject,
          syncStatus: 'pending',
        };
        await localDb.documentVersions.put(versionRec);
        await enqueueOperation('version', versionId, 'upsert');
      }

      return nextDoc;
    }
  );
}

export async function createNewLocalDocument(
  baseProject?: Partial<LetterheadProject>,
  name?: string
): Promise<LocalDocumentRecord> {
  const now = new Date().toISOString();
  const template = createRedesignedGrashyProject();
  const newId = `grashy-doc-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
  const newProject: LetterheadProject = {
    ...template,
    ...baseProject,
    id: newId,
    name: name || baseProject?.name || `Quotation ${new Date().toLocaleDateString()}`,
    version: 1,
    updatedAt: now,
  };

  const saved = await saveLocalDocumentWorkingCopy(newProject, {
    recordVersion: true,
    versionSummary: 'Created new document',
  });
  return saved;
}

export async function deleteLocalDocumentById(documentId: string): Promise<void> {
  const now = new Date().toISOString();
  await localDb.transaction(
    'rw',
    [localDb.documents, localDb.syncQueue, localDb.meta],
    async () => {
      const existing = await localDb.documents.get(documentId);
      if (!existing) return;
      await localDb.documents.put({
        ...existing,
        isDeleted: true,
        updatedAt: now,
        syncStatus: 'pending',
      });
      await enqueueOperation('document', documentId, 'delete');

      const activeMeta = await localDb.meta.get(META_ACTIVE_DOC_ID);
      if (activeMeta?.value === documentId) {
        const remaining = (await localDb.documents.toArray()).filter((d) => !d.isDeleted);
        if (remaining.length > 0) {
          await localDb.meta.put({ key: META_ACTIVE_DOC_ID, value: remaining[0].id });
        }
      }
    }
  );
}

export async function saveTemplateFromProjectToDb(
  project: LetterheadProject,
  templateName: string
): Promise<LocalTemplateRecord> {
  const now = new Date().toISOString();
  const id = `tpl-${Date.now()}`;
  const rec: LocalTemplateRecord = {
    id,
    name: templateName.trim() || `${project.name} Template`,
    layoutMode: project.layoutMode,
    pageSettings: project.pageSettings,
    elements: project.elements,
    isDefault: false,
    updatedAt: now,
    syncStatus: 'pending',
  };
  await localDb.templates.put(rec);
  await enqueueOperation('template', id, 'upsert');
  return rec;
}

export async function resolveConflictInDb(
  documentId: string,
  strategy: ConflictStrategy
): Promise<LocalDocumentRecord | null> {
  const now = new Date().toISOString();
  return localDb.transaction(
    'rw',
    [localDb.documents, localDb.documentVersions, localDb.syncQueue],
    async () => {
      const existing = await localDb.documents.get(documentId);
      if (!existing) return null;

      const resolved = resolveDocumentConflict(existing, strategy, now);
      await localDb.documents.put(resolved.updatedPrimary);

      if (resolved.archivedVersion) {
        await localDb.documentVersions.put(resolved.archivedVersion);
        if (resolved.archivedVersion.syncStatus === 'pending') {
          await enqueueOperation('version', resolved.archivedVersion.id, 'upsert');
        }
      }

      if (resolved.duplicateCopy) {
        await localDb.documents.put(resolved.duplicateCopy);
        await enqueueOperation('document', resolved.duplicateCopy.id, 'upsert');
        if (resolved.duplicateCopy.clientId) {
          await enqueueOperation('client', resolved.duplicateCopy.clientId, 'upsert');
        }
        if (resolved.duplicateCopy.templateId) {
          await enqueueOperation('template', resolved.duplicateCopy.templateId, 'upsert');
        }
      }

      if (resolved.updatedPrimary.syncStatus === 'pending') {
        await enqueueOperation('document', resolved.updatedPrimary.id, 'upsert');
        if (resolved.updatedPrimary.clientId) {
          await enqueueOperation('client', resolved.updatedPrimary.clientId, 'upsert');
        }
        if (resolved.updatedPrimary.templateId) {
          await enqueueOperation('template', resolved.updatedPrimary.templateId, 'upsert');
        }
      } else {
        await localDb.syncQueue.delete(`document:${resolved.updatedPrimary.id}`);
      }

      return resolved.updatedPrimary;
    }
  );
}

/**
 * Repairs local pending/error documents whose client or template dependency
 * is missing locally or not yet queued, without modifying or deleting user documents.
 */
export async function repairPendingDocumentDependencies(): Promise<number> {
  const now = new Date().toISOString();
  let repairedCount = 0;

  await localDb.transaction(
    'rw',
    [
      localDb.documents,
      localDb.clients,
      localDb.templates,
      localDb.syncQueue,
    ],
    async () => {
      const [allDocs, allClients, allTemplates, existingQueue] = await Promise.all([
        localDb.documents.toArray(),
        localDb.clients.toArray(),
        localDb.templates.toArray(),
        localDb.syncQueue.toArray(),
      ]);

      const clientsById = new Map(allClients.map((c) => [c.id, c]));
      const templatesById = new Map(allTemplates.map((t) => [t.id, t]));
      const queuedIds = new Set(existingQueue.map((q) => q.id));

      for (const doc of allDocs) {
        if (doc.isDeleted) continue;
        const isQueued = queuedIds.has(`document:${doc.id}`);
        if (doc.syncStatus !== 'pending' && doc.syncStatus !== 'error' && !isQueued) {
          continue;
        }

        const clientResolution = resolveClientForDocument(doc, clientsById, now);
        if (clientResolution.needsLocalClientInsert && clientResolution.clientRecord) {
          clientsById.set(clientResolution.clientRecord.id, clientResolution.clientRecord);
          await localDb.clients.put(clientResolution.clientRecord);
          repairedCount += 1;
        }

        if (clientResolution.clientRecord) {
          const clientQId = `client:${clientResolution.clientRecord.id}`;
          if (clientResolution.clientRecord.syncStatus !== 'synced' && !queuedIds.has(clientQId)) {
            await enqueueOperation('client', clientResolution.clientRecord.id, 'upsert');
            queuedIds.add(clientQId);
            repairedCount += 1;
          }
        }

        let resolvedTemplateId = doc.templateId;
        if (resolvedTemplateId && !templatesById.has(resolvedTemplateId)) {
          resolvedTemplateId = undefined;
        }
        if (resolvedTemplateId) {
          const tpl = templatesById.get(resolvedTemplateId);
          const tplQId = `template:${resolvedTemplateId}`;
          if (tpl && tpl.syncStatus !== 'synced' && !queuedIds.has(tplQId)) {
            await enqueueOperation('template', resolvedTemplateId, 'upsert');
            queuedIds.add(tplQId);
            repairedCount += 1;
          }
        }

        if (clientResolution.needsLocalDocRepair || resolvedTemplateId !== doc.templateId) {
          await localDb.documents.update(doc.id, {
            clientId: clientResolution.resolvedClientId,
            templateId: resolvedTemplateId,
          });
          repairedCount += 1;
        }
      }
    }
  );

  return repairedCount;
}

export async function setLastSyncedAt(iso: string): Promise<void> {
  await localDb.meta.put({ key: META_LAST_SYNCED_AT, value: iso });
}
