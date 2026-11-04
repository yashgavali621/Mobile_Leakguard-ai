-- =====================================================================
-- LeakGuard AI - Storage setup (Supabase Storage)
-- =====================================================================
-- Run this AFTER 0001_init_schema.sql. Creates the private bucket used by
-- the Bank Statement module (Phase 3) and the Reports PDF export (Phase 11),
-- with RLS-equivalent storage policies so a user can only read/write
-- objects under their own user_id folder.
--
-- Path convention (product spec section 68):
--   statements/{user_id}/{statement_id}.pdf
--   reports/{user_id}/{report_id}.pdf
--
-- storage.objects.name for a file uploaded to "statements/<uid>/<file>"
-- is "statements/<uid>/<file>" - policies below use
-- storage.foldername(name) to pull out the path segments and compare the
-- 2nd segment (the user_id folder) against auth.uid().
-- =====================================================================

insert into storage.buckets (id, name, public)
values ('leakguard-files', 'leakguard-files', false)
on conflict (id) do nothing;

create policy "leakguard_files_select_own"
    on storage.objects for select
    using (
        bucket_id = 'leakguard-files'
        and (storage.foldername(name))[2] = auth.uid()::text
    );

create policy "leakguard_files_insert_own"
    on storage.objects for insert
    with check (
        bucket_id = 'leakguard-files'
        and (storage.foldername(name))[2] = auth.uid()::text
    );

create policy "leakguard_files_update_own"
    on storage.objects for update
    using (
        bucket_id = 'leakguard-files'
        and (storage.foldername(name))[2] = auth.uid()::text
    );

create policy "leakguard_files_delete_own"
    on storage.objects for delete
    using (
        bucket_id = 'leakguard-files'
        and (storage.foldername(name))[2] = auth.uid()::text
    );

-- Sanity check once you're uploading files (Phase 3):
--   [ ] User A cannot list/read/delete anything under statements/<User B's id>/...
--   [ ] Uploading to statements/<some other id>/... as User A is rejected.
