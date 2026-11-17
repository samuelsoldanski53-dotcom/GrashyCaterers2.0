import { createClient, Session, SupabaseClient } from '@supabase/supabase-js';
import {
  enqueueOperation,
  localDb,
  repairPendingDocumentDependencies,
  setLastSyncedAt,
} from '../db/localDb';
import { LetterheadProject } from '../types/letterhead';
import {
  CloudUserSession,
  DocumentVersionRecord,
  LocalClientRecord,
  LocalDocumentRecord,
  LocalTemplateRecord,
} from '../types/sync';
import {
  detectDocumentConflict,
  extractPaymentInfo,
  fromCloudId,
  resolveClientForDocument,
  sortSyncQueueByDependencies,
  toCloudId,
} from '../utils/syncEngine';

const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const SUPABASE_ANON_KEY = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;
const BACKUP_BUCKET = 'letterhead-backups';

let cachedClient: SupabaseClient | null = null;

export function isSupabaseConfigured(): boolean {
  return Boolean(
    SUPABASE_URL &&
      SUPABASE_ANON_KEY &&
      SUPABASE_URL.startsWith('http') &&
      SUPABASE_ANON_KEY.length > 10
  );
}

export function getSupabaseClient(): SupabaseClient | null {
  if (!isSupabaseConfigured()) return null;
  if (!cachedClient) {
    cachedClient = createClient(SUPABASE_URL!, SUPABASE_ANON_KEY!, {
      auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: true,
      },
    });
  }
  return cachedClient;
}

export function mapSessionToCloudUser(session: Session | null): CloudUserSession | null {
  if (!session?.user) return null;
  return {
    userId: session.user.id,
    email: session.user.email || 'Authenticated User',
  };
}

export async function signInCloud(email: string, password: string): Promise<CloudUserSession> {
  const client = getSupabaseClient();
  if (!client) {
    throw new Error('Supabase environment variables are not configured.');
  }
  const { data, error } = await client.auth.signInWithPassword({ email, password });
  if (error || !data.session) {
    throw new Error(error?.message || 'Sign in failed');
  }
  return mapSessionToCloudUser(data.session)!;
}

export async function signUpCloud(email: string, password: string): Promise<{
  session: CloudUserSession | null;
  needsEmailConfirmation: boolean;
}> {
  const client = getSupabaseClient();
  if (!client) {
    throw new Error('Supabase environment variables are not configured.');
  }
  const { data, error } = await client.auth.signUp({ email, password });
  if (error) {
    throw new Error(error.message);
  }
  return {
    session: mapSessionToCloudUser(data.session),
    needsEmailConfirmation: !data.session,
  };
}

export async function signOutCloud(): Promise<void> {
  const client = getSupabaseClient();
  if (!client) return;
  await client.auth.signOut();
}

interface RemoteDocumentRow {
  id: string;
  user_id: string;
  client_id: string | null;
  template_id: string | null;
  name: string;
  version: number;
  layout_mode: 'original' | 'refined' | 'custom';
  page_count: number;
  form_data: LetterheadProject['formData'];
  payment_info: Record<string, string>;
  document_settings: LetterheadProject['pageSettings'];
  elements: LetterheadProject['elements'];
  storage_backup_path: string | null;
  updated_at: string;
  is_deleted: boolean;
}

interface RemoteClientRow {
  id: string;
  user_id: string;
  name: string;
  venue: string;
  default_pax: string;
  contact_person: string;
  phone_number: string;
  email: string;
  updated_at: string;
  is_deleted: boolean;
}

interface RemoteTemplateRow {
  id: string;
  user_id: string;
  name: string;
  layout_mode: 'original' | 'refined' | 'custom';
  page_settings: LetterheadProject['pageSettings'];
  elements: LetterheadProject['elements'];
  is_default: boolean;
  updated_at: string;
  is_deleted: boolean;
}

interface RemoteVersionRow {
  id: string;
  user_id: string;
  document_id: string;
  version: number;
  summary: string;
  snapshot: LetterheadProject;
  created_at: string;
}

async function uploadDocumentBackup(
  supabase: SupabaseClient,
  userId: string,
  doc: LocalDocumentRecord
): Promise<string | undefined> {
  try {
    const path = `${userId}/${doc.id}/v${doc.version}.json`;
    const payload = new Blob([JSON.stringify(doc.project, null, 2)], {
      type: 'application/json',
    });
    const { error } = await supabase.storage
      .from(BACKUP_BUCKET)
      .upload(path, payload, { upsert: true, contentType: 'application/json' });
    if (error) return doc.storageBackupPath;
    return path;
  } catch {
    return doc.storageBackupPath;
  }
}

export interface SyncRunResult {
  uploadedCount: number;
  pulledCount: number;
  conflictCount: number;
  syncedAt: string;
}

async function upsertClientToSupabase(
  supabase: SupabaseClient,
  userId: string,
  rec: LocalClientRecord,
  isDeletedOverride: boolean = false
): Promise<string> {
  const cloudId = toCloudId(userId, rec.id);
  const { error } = await supabase.from('clients').upsert(
    {
      id: cloudId,
      user_id: userId,
      name: rec.name,
      venue: rec.venue,
      default_pax: rec.defaultPax,
      contact_person: rec.contactPerson,
      phone_number: rec.phoneNumber,
      email: rec.email,
      updated_at: rec.updatedAt,
      is_deleted: isDeletedOverride || Boolean(rec.isDeleted),
    },
    { onConflict: 'id' }
  );
  if (error) throw new Error(error.message);
  await localDb.clients.update(rec.id, { syncStatus: 'synced' });
  await localDb.syncQueue.delete(`client:${rec.id}`);
  return cloudId;
}

async function upsertTemplateToSupabase(
  supabase: SupabaseClient,
  userId: string,
  rec: LocalTemplateRecord,
  isDeletedOverride: boolean = false
): Promise<string> {
  const cloudId = toCloudId(userId, rec.id);
  const { error } = await supabase.from('letterhead_templates').upsert(
    {
      id: cloudId,
      user_id: userId,
      name: rec.name,
      layout_mode: rec.layoutMode,
      page_settings: rec.pageSettings,
      elements: rec.elements,
      is_default: rec.isDefault,
      updated_at: rec.updatedAt,
      is_deleted: isDeletedOverride || Boolean(rec.isDeleted),
    },
    { onConflict: 'id' }
  );
  if (error) throw new Error(error.message);
  await localDb.templates.update(rec.id, { syncStatus: 'synced' });
  await localDb.syncQueue.delete(`template:${rec.id}`);
  return cloudId;
}

async function ensureRemoteClientForDocument(
  supabase: SupabaseClient,
  userId: string,
  doc: LocalDocumentRecord,
  syncedClientIds: Set<string>,
  nowIso: string
): Promise<{ cloudClientId: string | null; uploadedClient: boolean }> {
  const allClients = await localDb.clients.toArray();
  const clientsById = new Map(allClients.map((c) => [c.id, c]));
  const resolution = resolveClientForDocument(doc, clientsById, nowIso);

  if (resolution.needsLocalClientInsert && resolution.clientRecord) {
    await localDb.clients.put(resolution.clientRecord);
  }
  if (resolution.needsLocalDocRepair) {
    await localDb.documents.update(doc.id, {
      clientId: resolution.resolvedClientId,
    });
  }

  if (!resolution.clientRecord) {
    return { cloudClientId: null, uploadedClient: false };
  }

  const clientRec = resolution.clientRecord;
  const cloudClientId = toCloudId(userId, clientRec.id);
  if (syncedClientIds.has(cloudClientId)) {
    return { cloudClientId, uploadedClient: false };
  }

  const { data: remoteClient, error: checkClientErr } = await supabase
    .from('clients')
    .select('id')
    .eq('id', cloudClientId)
    .eq('user_id', userId)
    .maybeSingle();

  if (checkClientErr) {
    await enqueueOperation('client', clientRec.id, 'upsert');
    throw new Error(checkClientErr.message);
  }

  if (!remoteClient) {
    try {
      await upsertClientToSupabase(supabase, userId, clientRec);
      syncedClientIds.add(cloudClientId);
      return { cloudClientId, uploadedClient: true };
    } catch (err) {
      await enqueueOperation('client', clientRec.id, 'upsert');
      throw err;
    }
  }

  syncedClientIds.add(cloudClientId);
  return { cloudClientId, uploadedClient: false };
}

async function ensureRemoteTemplateForDocument(
  supabase: SupabaseClient,
  userId: string,
  doc: LocalDocumentRecord,
  syncedTemplateIds: Set<string>
): Promise<{ cloudTemplateId: string | null; uploadedTemplate: boolean }> {
  if (!doc.templateId) {
    return { cloudTemplateId: null, uploadedTemplate: false };
  }

  const cloudTemplateId = toCloudId(userId, doc.templateId);
  if (syncedTemplateIds.has(cloudTemplateId)) {
    return { cloudTemplateId, uploadedTemplate: false };
  }

  const { data: remoteTpl, error: checkTplErr } = await supabase
    .from('letterhead_templates')
    .select('id')
    .eq('id', cloudTemplateId)
    .eq('user_id', userId)
    .maybeSingle();

  if (checkTplErr) {
    await enqueueOperation('template', doc.templateId, 'upsert');
    throw new Error(checkTplErr.message);
  }

  if (!remoteTpl) {
    const localTpl = await localDb.templates.get(doc.templateId);
    if (!localTpl) {
      await localDb.documents.update(doc.id, { templateId: undefined });
      return { cloudTemplateId: null, uploadedTemplate: false };
    }
    try {
      await upsertTemplateToSupabase(supabase, userId, localTpl);
      syncedTemplateIds.add(cloudTemplateId);
      return { cloudTemplateId, uploadedTemplate: true };
    } catch (err) {
      await enqueueOperation('template', localTpl.id, 'upsert');
      throw err;
    }
  }

  syncedTemplateIds.add(cloudTemplateId);
  return { cloudTemplateId, uploadedTemplate: false };
}

export async function synchronizeLocalAndSupabase(userId: string): Promise<SyncRunResult> {
  const supabase = getSupabaseClient();
  if (!supabase) {
    throw new Error('Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY.');
  }

  let uploadedCount = 0;
  let pulledCount = 0;
  const nowIso = new Date().toISOString();

  // Repair any pending/failed documents whose client or template reference is missing locally or unqueued
  await repairPendingDocumentDependencies();

  // 1. Push pending queue items in strict dependency order (clients & templates -> documents -> versions)
  const rawQueue = await localDb.syncQueue.toArray();
  const queue = sortSyncQueueByDependencies(rawQueue);

  const skippedBootDocIds = new Set<string>();
  const syncedClientIds = new Set<string>();
  const syncedTemplateIds = new Set<string>();
  const syncedDocumentIds = new Set<string>();

  for (const item of queue) {
    try {
      if (item.entityType === 'client') {
        const rec = await localDb.clients.get(item.entityId);
        if (!rec) {
          await localDb.syncQueue.delete(item.id);
          continue;
        }
        const cloudId = await upsertClientToSupabase(
          supabase,
          userId,
          rec,
          item.operation === 'delete'
        );
        syncedClientIds.add(cloudId);
        uploadedCount += 1;
      } else if (item.entityType === 'template') {
        const rec = await localDb.templates.get(item.entityId);
        if (!rec) {
          await localDb.syncQueue.delete(item.id);
          continue;
        }
        const cloudId = await upsertTemplateToSupabase(
          supabase,
          userId,
          rec,
          item.operation === 'delete'
        );
        syncedTemplateIds.add(cloudId);
        uploadedCount += 1;
      } else if (item.entityType === 'document') {
        const rec = await localDb.documents.get(item.entityId);
        if (!rec) {
          await localDb.syncQueue.delete(item.id);
          continue;
        }
        if (rec.syncStatus === 'conflict') {
          // Never auto-push unresolved conflicts
          continue;
        }

        const cloudDocId = toCloudId(userId, rec.id);

        // Check remote version first before overwriting!
        const { data: existingRemote, error: checkErr } = await supabase
          .from('documents')
          .select('*')
          .eq('id', cloudDocId)
          .eq('user_id', userId)
          .maybeSingle<RemoteDocumentRow>();
        if (checkErr) throw new Error(checkErr.message);

        if (existingRemote) {
          if (rec.isBootSeed) {
            // Fresh device with untouched factory seed: skip overwriting cloud and pull remote instead
            skippedBootDocIds.add(rec.id);
            await localDb.syncQueue.delete(item.id);
            continue;
          }

          const action = detectDocumentConflict(rec, {
            version: existingRemote.version,
            updatedAt: existingRemote.updated_at,
            isDeleted: existingRemote.is_deleted,
          });
          if (action === 'conflict') {
            const remoteProject: LetterheadProject = {
              id: rec.id,
              name: existingRemote.name,
              version: existingRemote.version,
              updatedAt: existingRemote.updated_at,
              pageCount: existingRemote.page_count,
              layoutMode: existingRemote.layout_mode,
              formData: existingRemote.form_data,
              pageSettings: existingRemote.document_settings,
              elements: existingRemote.elements,
            };
            await localDb.documents.update(rec.id, {
              syncStatus: 'conflict',
              conflictRemote: {
                version: existingRemote.version,
                updatedAt: existingRemote.updated_at,
                name: existingRemote.name,
                project: remoteProject,
              },
            });
            continue;
          }
        }

        // Verify foreign-key dependencies (client_id and template_id) exist in Supabase first
        const clientDep = await ensureRemoteClientForDocument(
          supabase,
          userId,
          rec,
          syncedClientIds,
          nowIso
        );
        if (clientDep.uploadedClient) uploadedCount += 1;

        const templateDep = await ensureRemoteTemplateForDocument(
          supabase,
          userId,
          rec,
          syncedTemplateIds
        );
        if (templateDep.uploadedTemplate) uploadedCount += 1;

        const backupPath =
          item.operation === 'delete' || rec.isDeleted
            ? rec.storageBackupPath
            : await uploadDocumentBackup(supabase, userId, rec);

        const { error: upsertErr } = await supabase.from('documents').upsert(
          {
            id: cloudDocId,
            user_id: userId,
            client_id: clientDep.cloudClientId,
            template_id: templateDep.cloudTemplateId,
            name: rec.name,
            version: rec.version,
            layout_mode: rec.project.layoutMode,
            page_count: rec.project.pageCount,
            form_data: rec.project.formData,
            payment_info: extractPaymentInfo(rec.project.formData),
            document_settings: rec.project.pageSettings,
            elements: rec.project.elements,
            storage_backup_path: backupPath || null,
            updated_at: rec.updatedAt,
            is_deleted: item.operation === 'delete' || Boolean(rec.isDeleted),
          },
          { onConflict: 'id' }
        );
        if (upsertErr) throw new Error(upsertErr.message);

        await localDb.documents.update(rec.id, {
          clientId: clientDep.cloudClientId
            ? fromCloudId(userId, clientDep.cloudClientId)
            : undefined,
          templateId: templateDep.cloudTemplateId
            ? fromCloudId(userId, templateDep.cloudTemplateId)
            : undefined,
          syncStatus: 'synced',
          isBootSeed: false,
          baseCloudVersion: rec.version,
          syncedAt: nowIso,
          storageBackupPath: backupPath,
          lastError: undefined,
        });
        await localDb.syncQueue.delete(item.id);
        syncedDocumentIds.add(cloudDocId);
        uploadedCount += 1;
      } else if (item.entityType === 'version') {
        const ver = await localDb.documentVersions.get(item.entityId);
        if (!ver) {
          await localDb.syncQueue.delete(item.id);
          continue;
        }
        if (skippedBootDocIds.has(ver.documentId)) {
          await localDb.syncQueue.delete(item.id);
          continue;
        }

        const cloudDocId = toCloudId(userId, ver.documentId);
        if (!syncedDocumentIds.has(cloudDocId)) {
          const { data: remoteDoc, error: docCheckErr } = await supabase
            .from('documents')
            .select('id')
            .eq('id', cloudDocId)
            .eq('user_id', userId)
            .maybeSingle();
          if (docCheckErr) throw new Error(docCheckErr.message);

          if (!remoteDoc) {
            const parentDoc = await localDb.documents.get(ver.documentId);
            if (!parentDoc) {
              // Parent document does not exist locally or remotely; skip without deleting local history
              continue;
            }
            const clientDep = await ensureRemoteClientForDocument(
              supabase,
              userId,
              parentDoc,
              syncedClientIds,
              nowIso
            );
            if (clientDep.uploadedClient) uploadedCount += 1;
            const templateDep = await ensureRemoteTemplateForDocument(
              supabase,
              userId,
              parentDoc,
              syncedTemplateIds
            );
            if (templateDep.uploadedTemplate) uploadedCount += 1;

            const backupPath = parentDoc.isDeleted
              ? parentDoc.storageBackupPath
              : await uploadDocumentBackup(supabase, userId, parentDoc);

            const { error: parentUpErr } = await supabase.from('documents').upsert(
              {
                id: cloudDocId,
                user_id: userId,
                client_id: clientDep.cloudClientId,
                template_id: templateDep.cloudTemplateId,
                name: parentDoc.name,
                version: parentDoc.version,
                layout_mode: parentDoc.project.layoutMode,
                page_count: parentDoc.project.pageCount,
                form_data: parentDoc.project.formData,
                payment_info: extractPaymentInfo(parentDoc.project.formData),
                document_settings: parentDoc.project.pageSettings,
                elements: parentDoc.project.elements,
                storage_backup_path: backupPath || null,
                updated_at: parentDoc.updatedAt,
                is_deleted: Boolean(parentDoc.isDeleted),
              },
              { onConflict: 'id' }
            );
            if (parentUpErr) {
              await enqueueOperation('document', parentDoc.id, 'upsert');
              throw new Error(parentUpErr.message);
            }
            await localDb.documents.update(parentDoc.id, {
              syncStatus: 'synced',
              isBootSeed: false,
              baseCloudVersion: parentDoc.version,
              syncedAt: nowIso,
              storageBackupPath: backupPath,
              lastError: undefined,
            });
            await localDb.syncQueue.delete(`document:${parentDoc.id}`);
            uploadedCount += 1;
          }
          syncedDocumentIds.add(cloudDocId);
        }

        const { error } = await supabase.from('document_versions').upsert(
          {
            id: toCloudId(userId, ver.id),
            user_id: userId,
            document_id: cloudDocId,
            version: ver.version,
            summary: ver.summary,
            snapshot: ver.snapshot,
            created_at: ver.createdAt,
          },
          { onConflict: 'id' }
        );
        if (error) throw new Error(error.message);
        await localDb.documentVersions.update(ver.id, { syncStatus: 'synced' });
        await localDb.syncQueue.delete(item.id);
        uploadedCount += 1;
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Sync failed';
      await localDb.syncQueue.update(item.id, {
        attempts: (item.attempts || 0) + 1,
        lastError: msg,
      });
      throw new Error(msg);
    }
  }

  // 2. Pull cloud updates and reconcile safely
  const [clientsRes, templatesRes, docsRes, versionsRes] = await Promise.all([
    supabase.from('clients').select('*').eq('user_id', userId).returns<RemoteClientRow[]>(),
    supabase
      .from('letterhead_templates')
      .select('*')
      .eq('user_id', userId)
      .returns<RemoteTemplateRow[]>(),
    supabase.from('documents').select('*').eq('user_id', userId).returns<RemoteDocumentRow[]>(),
    supabase
      .from('document_versions')
      .select('*')
      .eq('user_id', userId)
      .returns<RemoteVersionRow[]>(),
  ]);

  if (clientsRes.error) throw new Error(clientsRes.error.message);
  if (templatesRes.error) throw new Error(templatesRes.error.message);
  if (docsRes.error) throw new Error(docsRes.error.message);
  if (versionsRes.error) throw new Error(versionsRes.error.message);

  for (const rc of clientsRes.data || []) {
    const localId = fromCloudId(userId, rc.id);
    const local = await localDb.clients.get(localId);
    if (!local || (local.syncStatus === 'synced' && rc.updated_at > local.updatedAt)) {
      const rec: LocalClientRecord = {
        id: localId,
        name: rc.name,
        venue: rc.venue,
        defaultPax: rc.default_pax,
        contactPerson: rc.contact_person,
        phoneNumber: rc.phone_number,
        email: rc.email,
        updatedAt: rc.updated_at,
        syncStatus: 'synced',
        isDeleted: rc.is_deleted,
      };
      await localDb.clients.put(rec);
      pulledCount += 1;
    }
  }

  for (const rt of templatesRes.data || []) {
    const localId = fromCloudId(userId, rt.id);
    const local = await localDb.templates.get(localId);
    if (!local || (local.syncStatus === 'synced' && rt.updated_at > local.updatedAt)) {
      const rec: LocalTemplateRecord = {
        id: localId,
        name: rt.name,
        layoutMode: rt.layout_mode,
        pageSettings: rt.page_settings,
        elements: rt.elements,
        isDefault: rt.is_default,
        updatedAt: rt.updated_at,
        syncStatus: 'synced',
        isDeleted: rt.is_deleted,
      };
      await localDb.templates.put(rec);
      pulledCount += 1;
    }
  }

  for (const rd of docsRes.data || []) {
    const localId = fromCloudId(userId, rd.id);
    const local = await localDb.documents.get(localId);
    const action = local?.isBootSeed
      ? 'pull_remote'
      : detectDocumentConflict(local, {
          version: rd.version,
          updatedAt: rd.updated_at,
          isDeleted: rd.is_deleted,
        });

    const remoteProject: LetterheadProject = {
      id: localId,
      name: rd.name,
      version: rd.version,
      updatedAt: rd.updated_at,
      pageCount: rd.page_count,
      layoutMode: rd.layout_mode,
      formData: rd.form_data,
      pageSettings: rd.document_settings,
      elements: rd.elements,
    };

    if (action === 'pull_remote') {
      if (local && !local.isBootSeed && local.version < rd.version) {
        // Archive previous local state before advancing to cloud version
        const snap: DocumentVersionRecord = {
          id: `${local.id}-prepull-v${local.version}-${Date.now()}`,
          documentId: local.id,
          version: local.version,
          summary: `Local snapshot before pulling cloud v${rd.version}`,
          createdAt: local.updatedAt,
          snapshot: local.project,
          syncStatus: 'synced',
        };
        await localDb.documentVersions.put(snap);
      }

      await localDb.documents.put({
        id: localId,
        name: rd.name,
        clientId: rd.client_id ? fromCloudId(userId, rd.client_id) : undefined,
        templateId: rd.template_id ? fromCloudId(userId, rd.template_id) : undefined,
        version: rd.version,
        baseCloudVersion: rd.version,
        updatedAt: rd.updated_at,
        syncedAt: nowIso,
        syncStatus: 'synced',
        isBootSeed: false,
        isDeleted: rd.is_deleted,
        storageBackupPath: rd.storage_backup_path || undefined,
        project: remoteProject,
      });
      pulledCount += 1;
    } else if (action === 'conflict' && local) {
      await localDb.documents.update(local.id, {
        syncStatus: 'conflict',
        conflictRemote: {
          version: rd.version,
          updatedAt: rd.updated_at,
          name: rd.name,
          project: remoteProject,
        },
      });
    }
  }

  for (const rv of versionsRes.data || []) {
    const localId = fromCloudId(userId, rv.id);
    const localDocId = fromCloudId(userId, rv.document_id);
    const existingVer = await localDb.documentVersions.get(localId);
    if (!existingVer) {
      await localDb.documentVersions.put({
        id: localId,
        documentId: localDocId,
        version: rv.version,
        summary: rv.summary,
        createdAt: rv.created_at,
        snapshot: rv.snapshot,
        syncStatus: 'synced',
      });
    }
  }

  const allDocsAfter = await localDb.documents.toArray();
  const conflictCount = allDocsAfter.filter((d) => !d.isDeleted && d.syncStatus === 'conflict').length;
  await setLastSyncedAt(nowIso);

  return {
    uploadedCount,
    pulledCount,
    conflictCount,
    syncedAt: nowIso,
  };
}
