-- GenZ Keyboard community storage.
--
-- Applied out-of-band on 2026-11-18 because the migration runner could not resolve the `storage`
-- schema (it reported `relation "storage" does not exist` while direct SQL against the same project
-- succeeded). This file makes that deployment reproducible and reviewable.
--
-- Every statement is idempotent: buckets use ON CONFLICT DO NOTHING and each policy is dropped
-- before being created, so re-running this file is safe.

insert into storage.buckets (id, name, public) values
  ('theme-previews',  'theme-previews',  true),
  ('theme-wallpapers','theme-wallpapers',true),
  ('post-images',     'post-images',     true)
on conflict (id) do nothing;

-- Server-side limits. The Android client already caps at 8 MB and validates file signatures, but a
-- client can bypass its own checks; only the bucket enforces them for everyone. Without these the
-- buckets accepted any size and any content type.
update storage.buckets
   set file_size_limit   = 8388608,
       allowed_mime_types = array['image/png','image/jpeg','image/webp']
 where id in ('theme-previews','theme-wallpapers','post-images');

-- Public read: these buckets hold theme previews and wallpapers, which are meant to be visible to
-- anyone browsing the marketplace. Nothing private is stored here — post images and wallpapers are
-- public by design, and the private data lives in the tables, guarded by their own RLS.
drop policy if exists "anyone reads theme previews" on storage.objects;
create policy "anyone reads theme previews" on storage.objects for select
  using (bucket_id in ('theme-previews','theme-wallpapers','post-images'));

-- Uploads must land in a folder named after the uploader, so a user cannot overwrite or plant files
-- in another user's directory.
drop policy if exists "authors upload to their folder" on storage.objects;
create policy "authors upload to their folder" on storage.objects for insert
  with check (
    bucket_id in ('theme-previews','theme-wallpapers','post-images')
    and auth.uid()::text = (storage.foldername(name))[1]
  );

drop policy if exists "authors delete their own uploads" on storage.objects;
create policy "authors delete their own uploads" on storage.objects for delete
  using (auth.uid()::text = (storage.foldername(name))[1]);

-- ---------------------------------------------------------------- private drafts bucket
-- Unpublished artwork must not be world-readable. This bucket is private: objects are reachable only
-- by their owner or through a signed URL the Storage API issues to an authorised caller.
insert into storage.buckets (id, name, public) values
  ('theme-drafts', 'theme-drafts', false)
on conflict (id) do nothing;

update storage.buckets
   set file_size_limit   = 8388608,
       allowed_mime_types = array['image/png','image/jpeg','image/webp']
 where id = 'theme-drafts';

drop policy if exists "owners read their drafts" on storage.objects;
create policy "owners read their drafts" on storage.objects for select
  using (bucket_id = 'theme-drafts' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "owners upload drafts" on storage.objects;
create policy "owners upload drafts" on storage.objects for insert
  with check (bucket_id = 'theme-drafts' and auth.uid()::text = (storage.foldername(name))[1]);

drop policy if exists "owners delete their drafts" on storage.objects;
create policy "owners delete their drafts" on storage.objects for delete
  using (bucket_id = 'theme-drafts' and auth.uid()::text = (storage.foldername(name))[1]);

-- Deliberately no UPDATE policy: a draft cannot be silently overwritten, so a failed publish can
-- never replace an existing file.
