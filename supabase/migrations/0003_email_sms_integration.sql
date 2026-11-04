-- =====================================================================
-- LeakGuard AI - Email (Gmail) + SMS integration
-- Run in the Supabase SQL Editor AFTER 0001 and 0002.
-- =====================================================================

-- Encrypted Gmail refresh tokens. Server-only: RLS on + NO policies + no grants,
-- so neither the anon key nor a user JWT can ever read it. Only the
-- gmail-sync Edge Function (service_role) touches this table.
create table if not exists public.gmail_tokens (
    user_id            uuid primary key references auth.users(id) on delete cascade,
    refresh_token_enc  text not null,
    google_email       text,
    last_sync_at       timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create trigger trg_gmail_tokens_updated_at
    before update on public.gmail_tokens
    for each row execute function public.set_updated_at();

alter table public.gmail_tokens enable row level security;
revoke all on public.gmail_tokens from anon, authenticated;

-- De-duplication key so re-scanning the SMS inbox / Gmail never double-counts.
-- (source_event_id = SHA-256 of the SMS, or the Gmail message id.)
alter table public.financial_events
    add constraint financial_events_user_source_event_uniq
    unique (user_id, source, source_event_id);
