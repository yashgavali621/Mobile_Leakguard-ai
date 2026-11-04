-- =====================================================================
-- LeakGuard AI - Initial schema
-- =====================================================================
-- Run this in the Supabase SQL Editor (or via `supabase db push` if you're
-- using the Supabase CLI) on a fresh project, in order, top to bottom.
--
-- Design rules this file follows throughout (see product spec sections
-- 10 and 58):
--   - Every user-owned table has a `user_id uuid references auth.users(id)`
--     column, Row Level Security enabled, and four policies (select/
--     insert/update/delete) that all reduce to `auth.uid() = user_id`.
--   - UUID primary keys via gen_random_uuid() (pgcrypto, enabled below).
--   - `created_at` / `updated_at` on every table where it's meaningful,
--     with `updated_at` kept current by a shared trigger function.
--   - Indexes on user_id, transaction_date, merchant, event_type,
--     subscription_id and alert read-status, per spec section 10.
--   - No service-role-only logic lives here; the ANDROID APP only ever
--     authenticates as a normal user via the publishable/anon key plus
--     the user's own JWT, so RLS is the only thing standing between one
--     user's financial data and another's. Treat these policies as load-
--     bearing, not decorative.
-- =====================================================================

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------
-- Shared trigger: keep updated_at current on every UPDATE
-- ---------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

-- =====================================================================
-- 1. profiles
-- =====================================================================
create table public.profiles (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null unique references auth.users(id) on delete cascade,
    full_name              text,
    email                  text,
    avatar_url             text,
    currency               text not null default 'INR',
    monthly_budget         numeric(12,2),
    onboarding_completed   boolean not null default false,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now()
);

create index idx_profiles_user_id on public.profiles(user_id);

create trigger trg_profiles_updated_at
    before update on public.profiles
    for each row execute function public.set_updated_at();

alter table public.profiles enable row level security;

create policy "profiles_select_own" on public.profiles
    for select using (auth.uid() = user_id);
create policy "profiles_insert_own" on public.profiles
    for insert with check (auth.uid() = user_id);
create policy "profiles_update_own" on public.profiles
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "profiles_delete_own" on public.profiles
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 2. connected_sources
-- =====================================================================
create table public.connected_sources (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid not null references auth.users(id) on delete cascade,
    source_type    text not null check (source_type in ('BANK_STATEMENT','GMAIL','NOTIFICATION','SMS')),
    status         text not null default 'DISCONNECTED'
                       check (status in ('CONNECTED','DISCONNECTED','REQUIRES_PERMISSION','ERROR')),
    last_sync_at   timestamptz,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    unique (user_id, source_type)
);

create index idx_connected_sources_user_id on public.connected_sources(user_id);

create trigger trg_connected_sources_updated_at
    before update on public.connected_sources
    for each row execute function public.set_updated_at();

alter table public.connected_sources enable row level security;

create policy "connected_sources_select_own" on public.connected_sources
    for select using (auth.uid() = user_id);
create policy "connected_sources_insert_own" on public.connected_sources
    for insert with check (auth.uid() = user_id);
create policy "connected_sources_update_own" on public.connected_sources
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "connected_sources_delete_own" on public.connected_sources
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 3. bank_statements
-- =====================================================================
create table public.bank_statements (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references auth.users(id) on delete cascade,
    file_name           text not null,
    file_type           text not null check (file_type in ('PDF','CSV')),
    file_size_bytes     bigint,
    -- Supabase Storage path, ALWAYS user-scoped: statements/{user_id}/{id}.ext
    storage_path        text,
    status              text not null default 'UPLOADED'
                            check (status in ('UPLOADED','PARSING','PARSED','FAILED')),
    transaction_count   integer not null default 0,
    period_start        date,
    period_end          date,
    error_message       text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index idx_bank_statements_user_id on public.bank_statements(user_id);

create trigger trg_bank_statements_updated_at
    before update on public.bank_statements
    for each row execute function public.set_updated_at();

alter table public.bank_statements enable row level security;

create policy "bank_statements_select_own" on public.bank_statements
    for select using (auth.uid() = user_id);
create policy "bank_statements_insert_own" on public.bank_statements
    for insert with check (auth.uid() = user_id);
create policy "bank_statements_update_own" on public.bank_statements
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "bank_statements_delete_own" on public.bank_statements
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 4. subscriptions
-- (created before transactions/financial_events so they can FK to it)
-- =====================================================================
create table public.subscriptions (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references auth.users(id) on delete cascade,
    subscription_name     text not null,
    merchant              text not null,
    normalized_merchant   text,
    amount                numeric(12,2) not null,
    currency              text not null default 'INR',
    frequency             text not null default 'UNKNOWN'
                              check (frequency in ('WEEKLY','MONTHLY','QUARTERLY','YEARLY','UNKNOWN')),
    next_renewal_date     date,
    status                text not null default 'ACTIVE'
                              check (status in ('ACTIVE','PAUSED','CANCELLED','UNKNOWN')),
    confidence            numeric(5,2) check (confidence is null or (confidence >= 0 and confidence <= 100)),
    category              text,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

create index idx_subscriptions_user_id on public.subscriptions(user_id);
create index idx_subscriptions_merchant on public.subscriptions(normalized_merchant);

create trigger trg_subscriptions_updated_at
    before update on public.subscriptions
    for each row execute function public.set_updated_at();

alter table public.subscriptions enable row level security;

create policy "subscriptions_select_own" on public.subscriptions
    for select using (auth.uid() = user_id);
create policy "subscriptions_insert_own" on public.subscriptions
    for insert with check (auth.uid() = user_id);
create policy "subscriptions_update_own" on public.subscriptions
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "subscriptions_delete_own" on public.subscriptions
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 5. transactions
-- =====================================================================
create table public.transactions (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null references auth.users(id) on delete cascade,
    source                 text not null check (source in ('BANK_STATEMENT','GMAIL','NOTIFICATION','SMS','MANUAL')),
    bank_statement_id      uuid references public.bank_statements(id) on delete set null,
    raw_merchant           text,
    normalized_merchant    text,
    amount                 numeric(12,2) not null,
    currency               text not null default 'INR',
    transaction_date       date not null,
    description            text,
    category               text,
    is_recurring           boolean not null default false,
    subscription_id        uuid references public.subscriptions(id) on delete set null,
    confidence             numeric(5,2) check (confidence is null or (confidence >= 0 and confidence <= 100)),
    matched_source_count   integer not null default 1,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now()
);

create index idx_transactions_user_id on public.transactions(user_id);
create index idx_transactions_transaction_date on public.transactions(transaction_date);
create index idx_transactions_merchant on public.transactions(normalized_merchant);
create index idx_transactions_subscription_id on public.transactions(subscription_id);

create trigger trg_transactions_updated_at
    before update on public.transactions
    for each row execute function public.set_updated_at();

alter table public.transactions enable row level security;

create policy "transactions_select_own" on public.transactions
    for select using (auth.uid() = user_id);
create policy "transactions_insert_own" on public.transactions
    for insert with check (auth.uid() = user_id);
create policy "transactions_update_own" on public.transactions
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "transactions_delete_own" on public.transactions
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 6. subscription_price_history
-- =====================================================================
create table public.subscription_price_history (
    id                   uuid primary key default gen_random_uuid(),
    user_id              uuid not null references auth.users(id) on delete cascade,
    subscription_id      uuid not null references public.subscriptions(id) on delete cascade,
    previous_amount      numeric(12,2),
    new_amount           numeric(12,2) not null,
    change_percentage    numeric(6,2),
    effective_date       date not null,
    detected_from        text,  -- e.g. 'GMAIL', 'BANK_STATEMENT', 'TRANSACTION_HISTORY'
    created_at           timestamptz not null default now()
);

create index idx_price_history_user_id on public.subscription_price_history(user_id);
create index idx_price_history_subscription_id on public.subscription_price_history(subscription_id);

alter table public.subscription_price_history enable row level security;

create policy "price_history_select_own" on public.subscription_price_history
    for select using (auth.uid() = user_id);
create policy "price_history_insert_own" on public.subscription_price_history
    for insert with check (auth.uid() = user_id);
create policy "price_history_update_own" on public.subscription_price_history
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "price_history_delete_own" on public.subscription_price_history
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 7. financial_events (the core normalized model - see FinancialEvent.java
--    once Phase 7 lands)
-- =====================================================================
create table public.financial_events (
    id                     uuid primary key default gen_random_uuid(),
    user_id                uuid not null references auth.users(id) on delete cascade,
    source                 text not null check (source in ('BANK_STATEMENT','GMAIL','NOTIFICATION','SMS')),
    source_event_id        text,
    event_type             text not null check (event_type in (
                                'PAYMENT','RENEWAL','SUBSCRIPTION','PRICE_CHANGE','BILL','REFUND',
                                'FAILED_PAYMENT','LARGE_TRANSACTION','UNUSUAL_TRANSACTION','PURCHASE','OTHER')),
    merchant               text,
    normalized_merchant    text,
    amount                 numeric(12,2),
    currency               text not null default 'INR',
    transaction_date       date,
    description            text,
    category               text,
    confidence             numeric(5,2) check (confidence is null or (confidence >= 0 and confidence <= 100)),
    is_recurring           boolean not null default false,
    subscription_id        uuid references public.subscriptions(id) on delete set null,
    transaction_id         uuid references public.transactions(id) on delete set null,
    -- Pointer to source-specific detail (e.g. email_events.id / notification_events.id).
    -- Never raw email/SMS/notification body content - see product spec section 50.
    raw_reference          text,
    created_at             timestamptz not null default now()
);

create index idx_financial_events_user_id on public.financial_events(user_id);
create index idx_financial_events_event_type on public.financial_events(event_type);
create index idx_financial_events_transaction_date on public.financial_events(transaction_date);
create index idx_financial_events_merchant on public.financial_events(normalized_merchant);
create index idx_financial_events_subscription_id on public.financial_events(subscription_id);

alter table public.financial_events enable row level security;

create policy "financial_events_select_own" on public.financial_events
    for select using (auth.uid() = user_id);
create policy "financial_events_insert_own" on public.financial_events
    for insert with check (auth.uid() = user_id);
create policy "financial_events_update_own" on public.financial_events
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "financial_events_delete_own" on public.financial_events
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 8. alerts
-- =====================================================================
create table public.alerts (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null references auth.users(id) on delete cascade,
    alert_type        text not null check (alert_type in (
                           'RENEWAL','PRICE_CHANGE','UNUSUAL_TRANSACTION','LARGE_TRANSACTION',
                           'PAYMENT_FAILED','BILL_DUE','NEW_SUBSCRIPTION','DUPLICATE_SERVICE','POTENTIAL_SAVING')),
    title             text not null,
    description       text,
    severity          text not null default 'INFO' check (severity in ('INFO','LOW','MEDIUM','HIGH')),
    amount            numeric(12,2),
    event_id          uuid references public.financial_events(id) on delete set null,
    subscription_id   uuid references public.subscriptions(id) on delete set null,
    transaction_id    uuid references public.transactions(id) on delete set null,
    is_read           boolean not null default false,
    occurred_at       timestamptz not null default now(),
    created_at        timestamptz not null default now()
);

create index idx_alerts_user_id on public.alerts(user_id);
create index idx_alerts_status on public.alerts(user_id, is_read);
create index idx_alerts_subscription_id on public.alerts(subscription_id);

alter table public.alerts enable row level security;

create policy "alerts_select_own" on public.alerts
    for select using (auth.uid() = user_id);
create policy "alerts_insert_own" on public.alerts
    for insert with check (auth.uid() = user_id);
create policy "alerts_update_own" on public.alerts
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "alerts_delete_own" on public.alerts
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 9. ai_insights
-- =====================================================================
create table public.ai_insights (
    id                        uuid primary key default gen_random_uuid(),
    user_id                   uuid not null references auth.users(id) on delete cascade,
    insight_text              text not null,
    category                  text,
    severity                  text default 'INFO' check (severity in ('INFO','LOW','MEDIUM','HIGH')),
    recommendations           jsonb,  -- json array of short recommendation strings
    related_subscription_id   uuid references public.subscriptions(id) on delete set null,
    related_alert_id          uuid references public.alerts(id) on delete set null,
    -- The structured (already-normalized) data actually sent to Gemini, kept
    -- for auditability. Never the raw email/SMS/notification content itself.
    source_data_snapshot      jsonb,
    model_version             text,
    created_at                timestamptz not null default now()
);

create index idx_ai_insights_user_id on public.ai_insights(user_id);

alter table public.ai_insights enable row level security;

create policy "ai_insights_select_own" on public.ai_insights
    for select using (auth.uid() = user_id);
create policy "ai_insights_insert_own" on public.ai_insights
    for insert with check (auth.uid() = user_id);
create policy "ai_insights_update_own" on public.ai_insights
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "ai_insights_delete_own" on public.ai_insights
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 10. spending_categories
-- Global reference/lookup table (NOT user-owned) - every signed-in user
-- can read it, nobody can write to it from the client. The service role
-- (used only by trusted backend/Edge Function code, never the Android
-- app) is the only writer, via `insert ... on conflict do nothing` below.
-- =====================================================================
create table public.spending_categories (
    id           uuid primary key default gen_random_uuid(),
    name         text not null unique,
    icon         text,
    color        text,
    is_system    boolean not null default true,
    created_at   timestamptz not null default now()
);

alter table public.spending_categories enable row level security;

create policy "spending_categories_select_all_authenticated" on public.spending_categories
    for select using (auth.role() = 'authenticated');
-- Intentionally no insert/update/delete policy for regular users - only the
-- service role (which bypasses RLS entirely) manages this table.

insert into public.spending_categories (name, icon, color, is_system) values
    ('Food', 'ic_category_food', '#B8791A', true),
    ('Shopping', 'ic_category_shopping', '#4A6FA5', true),
    ('Entertainment', 'ic_category_entertainment', '#2A5C99', true),
    ('Bills', 'ic_category_bills', '#B3311E', true),
    ('Travel', 'ic_category_travel', '#1E7A4C', true),
    ('Subscriptions', 'ic_category_subscriptions', '#0F2A47', true),
    ('Utilities', 'ic_category_utilities', '#6B7280', true),
    ('Other', 'ic_category_other', '#9CA3AF', true)
on conflict (name) do nothing;

-- =====================================================================
-- 11. budgets
-- =====================================================================
create table public.budgets (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references auth.users(id) on delete cascade,
    month               date not null,  -- first-of-month marker, e.g. 2026-09-01
    total_budget        numeric(12,2) not null,
    alert_threshold_1   integer not null default 80,
    alert_threshold_2   integer not null default 90,
    alert_threshold_3   integer not null default 100,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    unique (user_id, month)
);

create index idx_budgets_user_id on public.budgets(user_id);

create trigger trg_budgets_updated_at
    before update on public.budgets
    for each row execute function public.set_updated_at();

alter table public.budgets enable row level security;

create policy "budgets_select_own" on public.budgets
    for select using (auth.uid() = user_id);
create policy "budgets_insert_own" on public.budgets
    for insert with check (auth.uid() = user_id);
create policy "budgets_update_own" on public.budgets
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "budgets_delete_own" on public.budgets
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 12. budget_categories
-- =====================================================================
create table public.budget_categories (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null references auth.users(id) on delete cascade,
    budget_id          uuid not null references public.budgets(id) on delete cascade,
    category           text not null,
    allocated_amount   numeric(12,2) not null,
    spent_amount       numeric(12,2) not null default 0,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create index idx_budget_categories_user_id on public.budget_categories(user_id);
create index idx_budget_categories_budget_id on public.budget_categories(budget_id);

create trigger trg_budget_categories_updated_at
    before update on public.budget_categories
    for each row execute function public.set_updated_at();

alter table public.budget_categories enable row level security;

create policy "budget_categories_select_own" on public.budget_categories
    for select using (auth.uid() = user_id);
create policy "budget_categories_insert_own" on public.budget_categories
    for insert with check (auth.uid() = user_id);
create policy "budget_categories_update_own" on public.budget_categories
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "budget_categories_delete_own" on public.budget_categories
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 13. financial_goals
-- =====================================================================
create table public.financial_goals (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references auth.users(id) on delete cascade,
    goal_name        text not null,
    target_amount    numeric(12,2) not null,
    current_amount   numeric(12,2) not null default 0,
    target_date      date,
    status           text not null default 'ACTIVE' check (status in ('ACTIVE','COMPLETED','ABANDONED')),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

create index idx_financial_goals_user_id on public.financial_goals(user_id);

create trigger trg_financial_goals_updated_at
    before update on public.financial_goals
    for each row execute function public.set_updated_at();

alter table public.financial_goals enable row level security;

create policy "financial_goals_select_own" on public.financial_goals
    for select using (auth.uid() = user_id);
create policy "financial_goals_insert_own" on public.financial_goals
    for insert with check (auth.uid() = user_id);
create policy "financial_goals_update_own" on public.financial_goals
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "financial_goals_delete_own" on public.financial_goals
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 14. renewal_events
-- =====================================================================
create table public.renewal_events (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null references auth.users(id) on delete cascade,
    subscription_id   uuid not null references public.subscriptions(id) on delete cascade,
    renewal_date      date not null,
    amount            numeric(12,2),
    status            text not null default 'UPCOMING' check (status in ('UPCOMING','DUE_TODAY','OVERDUE','COMPLETED')),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index idx_renewal_events_user_id on public.renewal_events(user_id);
create index idx_renewal_events_subscription_id on public.renewal_events(subscription_id);
create index idx_renewal_events_renewal_date on public.renewal_events(renewal_date);

create trigger trg_renewal_events_updated_at
    before update on public.renewal_events
    for each row execute function public.set_updated_at();

alter table public.renewal_events enable row level security;

create policy "renewal_events_select_own" on public.renewal_events
    for select using (auth.uid() = user_id);
create policy "renewal_events_insert_own" on public.renewal_events
    for insert with check (auth.uid() = user_id);
create policy "renewal_events_update_own" on public.renewal_events
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "renewal_events_delete_own" on public.renewal_events
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 15. reports
-- =====================================================================
create table public.reports (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references auth.users(id) on delete cascade,
    report_month          date not null,
    total_spending        numeric(12,2),
    recurring_spending    numeric(12,2),
    subscription_count    integer,
    price_change_count    integer,
    alert_count           integer,
    potential_savings     numeric(12,2),
    leak_score            integer check (leak_score is null or (leak_score >= 0 and leak_score <= 100)),
    top_categories        jsonb,
    -- Supabase Storage path for the exported PDF, e.g. reports/{user_id}/{id}.pdf
    pdf_storage_path      text,
    created_at            timestamptz not null default now()
);

create index idx_reports_user_id on public.reports(user_id);

alter table public.reports enable row level security;

create policy "reports_select_own" on public.reports
    for select using (auth.uid() = user_id);
create policy "reports_insert_own" on public.reports
    for insert with check (auth.uid() = user_id);
create policy "reports_update_own" on public.reports
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "reports_delete_own" on public.reports
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 16. notification_events
-- Only the fields needed for financial analysis are stored - never the
-- full notification text (product spec section 19/50).
-- =====================================================================
create table public.notification_events (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references auth.users(id) on delete cascade,
    package_name          text not null,
    title                 text,
    amount                numeric(12,2),
    merchant              text,
    event_type            text,
    posted_at             timestamptz not null,
    processed             boolean not null default false,
    financial_event_id    uuid references public.financial_events(id) on delete set null,
    created_at            timestamptz not null default now()
);

create index idx_notification_events_user_id on public.notification_events(user_id);
create index idx_notification_events_processed on public.notification_events(user_id, processed);

alter table public.notification_events enable row level security;

create policy "notification_events_select_own" on public.notification_events
    for select using (auth.uid() = user_id);
create policy "notification_events_insert_own" on public.notification_events
    for insert with check (auth.uid() = user_id);
create policy "notification_events_update_own" on public.notification_events
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "notification_events_delete_own" on public.notification_events
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 17. email_events
-- Populated by the gmail-sync Edge Function (backend-only - see
-- supabase/functions/gmail-sync). Stores extracted fields only, never the
-- full email body (product spec section 17/18).
-- =====================================================================
create table public.email_events (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references auth.users(id) on delete cascade,
    gmail_message_id      text not null,
    sender                text,
    subject               text,
    classification        text check (classification in (
                               'SUBSCRIPTION','RENEWAL','PAYMENT','PRICE_CHANGE','BILL',
                               'REFUND','PAYMENT_FAILED','PURCHASE','OTHER')),
    merchant              text,
    amount                numeric(12,2),
    email_date            timestamptz,
    processed             boolean not null default false,
    financial_event_id    uuid references public.financial_events(id) on delete set null,
    created_at            timestamptz not null default now(),
    unique (user_id, gmail_message_id)
);

create index idx_email_events_user_id on public.email_events(user_id);
create index idx_email_events_processed on public.email_events(user_id, processed);

alter table public.email_events enable row level security;

-- Regular users may read/delete their own email_events, but INSERT/UPDATE
-- is intentionally left to the service role only (the gmail-sync Edge
-- Function), since these rows are meant to be an audit trail of what the
-- backend actually processed, not something the client can fabricate.
create policy "email_events_select_own" on public.email_events
    for select using (auth.uid() = user_id);
create policy "email_events_delete_own" on public.email_events
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 18. sms_events
-- Populated on-device only when SMS access is available and the user has
-- explicitly granted it (product spec section 20). Stores extracted
-- fields only, never the full SMS body.
-- =====================================================================
create table public.sms_events (
    id                    uuid primary key default gen_random_uuid(),
    user_id               uuid not null references auth.users(id) on delete cascade,
    sender                text,
    merchant              text,
    amount                numeric(12,2),
    event_type            text,
    received_at           timestamptz not null,
    processed             boolean not null default false,
    financial_event_id    uuid references public.financial_events(id) on delete set null,
    created_at            timestamptz not null default now()
);

create index idx_sms_events_user_id on public.sms_events(user_id);
create index idx_sms_events_processed on public.sms_events(user_id, processed);

alter table public.sms_events enable row level security;

create policy "sms_events_select_own" on public.sms_events
    for select using (auth.uid() = user_id);
create policy "sms_events_insert_own" on public.sms_events
    for insert with check (auth.uid() = user_id);
create policy "sms_events_update_own" on public.sms_events
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "sms_events_delete_own" on public.sms_events
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 19. analysis_runs
-- =====================================================================
create table public.analysis_runs (
    id                   uuid primary key default gen_random_uuid(),
    user_id              uuid not null references auth.users(id) on delete cascade,
    run_type             text not null check (run_type in (
                              'BANK_STATEMENT','GMAIL_SYNC','NOTIFICATION_SYNC','SMS_SYNC','FULL_ANALYSIS')),
    status               text not null default 'RUNNING' check (status in ('RUNNING','COMPLETED','FAILED')),
    events_processed     integer default 0,
    alerts_generated     integer default 0,
    error_message        text,
    started_at           timestamptz not null default now(),
    completed_at         timestamptz
);

create index idx_analysis_runs_user_id on public.analysis_runs(user_id);

alter table public.analysis_runs enable row level security;

create policy "analysis_runs_select_own" on public.analysis_runs
    for select using (auth.uid() = user_id);
create policy "analysis_runs_insert_own" on public.analysis_runs
    for insert with check (auth.uid() = user_id);
create policy "analysis_runs_update_own" on public.analysis_runs
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "analysis_runs_delete_own" on public.analysis_runs
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- 20. user_preferences
-- =====================================================================
create table public.user_preferences (
    id                            uuid primary key default gen_random_uuid(),
    user_id                       uuid not null unique references auth.users(id) on delete cascade,
    notify_important_alerts       boolean not null default true,
    notify_renewals               boolean not null default true,
    notify_insights               boolean not null default true,
    notify_budget                 boolean not null default true,
    notification_sources          jsonb,
    onboarding_reset_requested    boolean not null default false,
    created_at                    timestamptz not null default now(),
    updated_at                    timestamptz not null default now()
);

create index idx_user_preferences_user_id on public.user_preferences(user_id);

create trigger trg_user_preferences_updated_at
    before update on public.user_preferences
    for each row execute function public.set_updated_at();

alter table public.user_preferences enable row level security;

create policy "user_preferences_select_own" on public.user_preferences
    for select using (auth.uid() = user_id);
create policy "user_preferences_insert_own" on public.user_preferences
    for insert with check (auth.uid() = user_id);
create policy "user_preferences_update_own" on public.user_preferences
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "user_preferences_delete_own" on public.user_preferences
    for delete using (auth.uid() = user_id);

-- =====================================================================
-- End of 0001_init_schema.sql
--
-- Sanity checklist before you consider this migration "done" (see product
-- spec section 58, "Test RLS explicitly"):
--   [ ] As User A, SELECT from every table above returns only User A's rows.
--   [ ] As User A, attempting to SELECT/UPDATE/DELETE a row you know
--       belongs to User B (by id) returns zero rows / is rejected.
--   [ ] Inserting a row with user_id set to someone else's id is rejected
--       by the WITH CHECK clause.
--   [ ] spending_categories is readable by any authenticated user and NOT
--       writable by any of them (service role only).
--   [ ] email_events is not directly insertable/updatable by a normal user
--       (only the gmail-sync Edge Function, via the service role, may).
-- =====================================================================
