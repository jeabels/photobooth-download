-- =============================================================================
--  Snaps Photobooth — Supabase setup for digital copies
--
--  Only needed when a booth should keep its photos in its OWN Supabase project.
--  Run it once in that project:
--    Supabase dashboard → SQL Editor → New query → paste this whole file → Run.
--  It is safe to run again; it only adds what is missing.
--
--  It creates the "photos" storage bucket that holds the strips guests
--  download with the QR code.
-- =============================================================================

-- Public read, so a guest's phone can open the photo from the QR.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', true, 10485760, array['image/jpeg', 'image/png'])
on conflict (id) do update
  set public = true,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Booths may add new files under p/ only. They cannot list, change or delete.
drop policy if exists "booth uploads strips" on storage.objects;
create policy "booth uploads strips"
  on storage.objects for insert
  to anon, authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = 'p');

-- Done. Copy the project reference and publishable key from
-- Project Settings → API Keys.
select 'photos bucket ready' as status;
