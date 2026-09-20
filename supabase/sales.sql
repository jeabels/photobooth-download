-- =============================================================================
--  Snaps Photobooth — app sales (run once, in YOUR Supabase project only)
--
--  Supabase → SQL Editor → New query → paste this whole file → Run.
--  Safe to run again. Your buyers never need this file.
--
--  It creates:
--    1. "releases" storage bucket — PRIVATE; holds SnapsPhotobooth.apk
--    2. "download_requests" table — one row per customer who asks for the app,
--       including which Supabase project their booth's photos go to
--  Only the download-gate function (with your secret key) can read either.
-- =============================================================================

-- 1. Private bucket for the app file. No policies: nobody can list or open it
--    directly. The download-gate function hands out 15-minute links instead.
insert into storage.buckets (id, name, public, file_size_limit)
values ('releases', 'releases', false, 209715200)
on conflict (id) do update set public = false;

-- 2. Download requests.
create table if not exists public.download_requests (
  id                uuid primary key,
  created_at        timestamptz not null default now(),
  name              text not null,
  contact           text not null,
  booth             text,
  note              text,
  code_hash         text not null,
  expires_at        timestamptz not null,
  attempts          int not null default 0,
  downloads         int not null default 0,
  last_download_at  timestamptz,
  ip_hash           text
);

-- Added later: digital copies for each buyer's booth. Leave cloud_ref and
-- cloud_key empty to let the booth use your project; fill them in (Table
-- Editor → download_requests → the buyer's row) to give a buyer their own.
alter table public.download_requests add column if not exists cloud_ref text;
alter table public.download_requests add column if not exists cloud_key text;
alter table public.download_requests add column if not exists cloud_bucket text;
alter table public.download_requests add column if not exists activations int not null default 0;
alter table public.download_requests add column if not exists last_activated_at timestamptz;

create index if not exists download_requests_ip_idx on public.download_requests (ip_hash, created_at desc);
create index if not exists download_requests_code_idx on public.download_requests (code_hash);

-- Wrong-code log, so nobody can guess codes by trying many numbers.
create table if not exists public.download_failures (
  ip_hash  text not null,
  at       timestamptz not null default now()
);
create index if not exists download_failures_ip_idx on public.download_failures (ip_hash, at desc);
alter table public.download_failures enable row level security;
revoke all on public.download_failures from anon, authenticated;

-- Locked: no policies, so the public keys cannot read or write it.
alter table public.download_requests enable row level security;
revoke all on public.download_requests from anon, authenticated;

-- Handy view for you in the Table Editor: who asked, and whether they downloaded.
-- (Run this select any time.)
-- select created_at, name, contact, booth, downloads, last_download_at
-- from public.download_requests order by created_at desc;
