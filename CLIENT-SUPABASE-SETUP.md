# Snaps Photobooth: Set up your own photo storage

## What you'll need and what this does

This sets up **your own free photo storage**, so guests can scan the QR code on their print and download a digital copy of their photos.

- You need a Gmail account (or any email), the booth tablet, and about 10 minutes.
- Everything here uses the Supabase **free plan**. You don't need a credit card.
- You make **your own** Supabase account. Photos are stored there, not with the seller, and only you control them.
- Photos delete themselves after **3 hours**, so your storage never fills up.

## Step 1: Create your Supabase account and project

1. Go to **supabase.com** and tap **Start your project**.
2. Tap **Continue with Google** and sign in with your Gmail.
3. If it asks you to create an organization, type your business name, choose the **Free** plan, and tap **Create organization**.
4. Tap **New project** and fill in:
   - **Project name:** for example `snaps-booth`
   - **Database password:** tap **Generate a password**, then **save it somewhere safe**. The booth doesn't use it, but keep it private.
   - **Region:** **Southeast Asia (Singapore)**. It's closest to the Philippines, so downloads are fastest.
5. Tap **Create new project**. Wait 1–2 minutes for the dashboard to finish loading.

## Step 2: Create the photo storage (copy and paste once)

1. In your project, tap **SQL Editor** in the left menu (the `>_` icon).
2. Tap **+ New query**.
3. **Copy everything** in the box below and **paste** it into the editor:

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', true, 10485760, array['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
on conflict (id) do update
  set public = true,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "booth uploads strips" on storage.objects;
create policy "booth uploads strips"
  on storage.objects for insert
  to anon, authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = 'p');

drop policy if exists "booth lists expired strips" on storage.objects;
create policy "booth lists expired strips"
  on storage.objects for select
  to anon, authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = 'p'
         and created_at < now() - interval '1 hour');

drop policy if exists "booth deletes expired strips" on storage.objects;
create policy "booth deletes expired strips"
  on storage.objects for delete
  to anon, authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = 'p'
         and created_at < now() - interval '1 hour');

create or replace function public.snaps_ping()
returns text
language sql
stable
as $$ select 'ok'::text $$;
revoke all on function public.snaps_ping() from public;
grant execute on function public.snaps_ping() to anon, authenticated;

select 'photos bucket ready' as status;
```

4. Tap **Run** (bottom right).
5. At the bottom you should see **photos bucket ready** ✅. That's it, and you only do this once.

> If it asks *"This query has destructive operations, run anyway?"*, tap **Run this query**. It only replaces old booth rules and doesn't delete any photos.

## Step 3: Copy your two codes

The booth needs **two codes** from Supabase. Copy them into your tablet's notes.

**A. Project reference**
1. Tap the ⚙️ **Project Settings** icon (bottom of the left menu), then **General**.
2. Copy the **Project ID**. It's about 20 lowercase letters, like `abcdefghijklmnopqrst`.
   - It's also the part of your project URL before `.supabase.co`.

**B. Publishable key**
1. Still in Project Settings, tap **API Keys**.
2. Copy the **Publishable key**. It starts with `sb_publishable_`.

> ⚠️ **Never put the secret key in the booth.** It starts with `sb_secret_`. Never share it with anyone either. The booth only needs the publishable key.

## Step 4: Paste the codes into the booth app

1. Open **Snaps Photobooth** on the tablet.
2. Tap the **⋮** menu (top corner) and enter your **admin PIN**.
3. Go to **Settings → Advanced → Digital copies**.
4. Paste your codes:
   - **Supabase project reference** → the Project ID from Step 3A
   - **Publishable key** → the `sb_publishable_…` key from Step 3B
5. Tap **Save**.
6. Tap **Test digital copies**. It should say **Working** ✅.
7. Set **Keep photos online for** to **3 hours**.
8. *(Optional)* Turn on **Download shots** to also save every photo to the tablet's Photos app (album **Snaps Photobooth**, one folder per day).
9. Take a test photo, print it, and scan the QR code with your phone. Your photo should open. 🎉

## Daily use and troubleshooting

**Every event**
- The booth needs **internet** (Wi‑Fi or a hotspot) for QR downloads. Printing works without it.
- Guests have **3 hours** to download. After that, the photo deletes itself.

**Keep private**
- Your **secret key** and **database password**. The seller never needs them.

**If something goes wrong**

| What you see | What to do |
|---|---|
| Test says the project is **paused** | Free projects pause after about a week without use. Open supabase.com → your project → **Restore project**, wait 2 minutes, then test again. |
| App says **"run the latest setup.sql"** | Repeat **Step 2**. It's safe to run more than once. |
| QR page says **"on its way"** | The tablet isn't online yet. The photo uploads as soon as it reconnects. |
| Guest says **link expired** | It's been more than 3 hours. If **Download shots** is on, the photo is still in the tablet's Photos → **Snaps Photobooth** album. |
| Test says **key not accepted** | Make sure you pasted the **publishable** key (not the secret one), with no extra spaces. |
