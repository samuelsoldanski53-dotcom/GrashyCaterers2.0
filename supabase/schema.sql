-- Grashy Studio — Supabase PostgreSQL Schema, RLS Policies & Storage Bucket
-- Apply this in your Supabase SQL Editor before enabling cloud synchronization.

-- 1. Clients directory
create table if not exists public.clients (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  venue text not null default '',
  default_pax text not null default '',
  contact_person text not null default '',
  phone_number text not null default '',
  email text not null default '',
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

-- 2. Reusable letterhead templates
create table if not exists public.letterhead_templates (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  layout_mode text not null default 'refined',
  page_settings jsonb not null,
  elements jsonb not null,
  is_default boolean not null default false,
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

-- 3. Documents (includes client link, payment info, form_data, settings, elements & version)
create table if not exists public.documents (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  client_id text references public.clients(id) on delete set null,
  template_id text references public.letterhead_templates(id) on delete set null,
  name text not null,
  version integer not null default 1,
  layout_mode text not null default 'refined',
  page_count integer not null default 1,
  form_data jsonb not null,
  payment_info jsonb not null,
  document_settings jsonb not null,
  elements jsonb not null,
  storage_backup_path text,
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

-- 4. Immutable document version history
create table if not exists public.document_versions (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  document_id text not null references public.documents(id) on delete cascade,
  version integer not null,
  summary text not null default '',
  snapshot jsonb not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_clients_user_updated on public.clients (user_id, updated_at desc);
create index if not exists idx_templates_user_updated on public.letterhead_templates (user_id, updated_at desc);
create index if not exists idx_documents_user_updated on public.documents (user_id, updated_at desc);
create index if not exists idx_versions_doc on public.document_versions (user_id, document_id, version desc);

-- Enable Row-Level Security (RLS) on every table
alter table public.clients enable row level security;
alter table public.letterhead_templates enable row level security;
alter table public.documents enable row level security;
alter table public.document_versions enable row level security;

-- Authenticated user-owned RLS policies
drop policy if exists "clients_owner_all" on public.clients;
create policy "clients_owner_all" on public.clients
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "templates_owner_all" on public.letterhead_templates;
create policy "templates_owner_all" on public.letterhead_templates
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "documents_owner_all" on public.documents;
create policy "documents_owner_all" on public.documents
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "versions_owner_all" on public.document_versions;
create policy "versions_owner_all" on public.document_versions
  for all to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- 5. Storage bucket for JSON/PDF document backups
insert into storage.buckets (id, name, public)
values ('letterhead-backups', 'letterhead-backups', false)
on conflict (id) do nothing;

drop policy if exists "backups_owner_read" on storage.objects;
create policy "backups_owner_read" on storage.objects
  for select to authenticated
  using (bucket_id = 'letterhead-backups' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "backups_owner_insert" on storage.objects;
create policy "backups_owner_insert" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'letterhead-backups' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "backups_owner_update" on storage.objects;
create policy "backups_owner_update" on storage.objects
  for update to authenticated
  using (bucket_id = 'letterhead-backups' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "backups_owner_delete" on storage.objects;
create policy "backups_owner_delete" on storage.objects
  for delete to authenticated
  using (bucket_id = 'letterhead-backups' and (storage.foldername(name))[1] = auth.uid()::text);

-- Refresh PostgREST schema cache immediately
notify pgrst, 'reload schema';
