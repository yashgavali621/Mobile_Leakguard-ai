# LeakGuard AI

**Know Where Your Money Leaks.**

A privacy-focused financial intelligence Android app (Java + XML, MVVM,
Supabase backend) that detects hidden subscriptions, recurring payments,
price hikes and unusual transactions across bank statements, Gmail, phone
notifications and (where policy-compliant) SMS.

---

## 0. Where this build actually is right now

This is a large, multi-phase product (24 screens, a full Supabase backend,
Gmail OAuth via a server, Gemini via Edge Functions, NotificationListenerService,
SMS handling, Room, WorkManager...). It is being built **phase by phase**,
each phase fully real - no fake buttons, no mock "connected" states, no
hardcoded dashboard numbers - rather than dropped in all at once as hollow
scaffolding.

**Phase 1 (this build) is done:**
- Project skeleton, Gradle config, `.gitignore`, environment examples
- Full design system (`colors.xml`, `dimens.xml`, `strings.xml`,
  `themes.xml`, `styles.xml`) matching the "professional fintech, not
  AI-template" visual direction
- 5-tab bottom navigation (`MainActivity`) that preserves fragment state
  when switching tabs and handles back-press correctly
- **Real** Supabase Auth: Splash -> Onboarding (3 pages) -> Register / Login
  / Forgot Password, all calling the actual Supabase GoTrue REST API
  (sign up, sign in, session refresh, logout, password recovery), with
  session tokens stored in Keystore-backed `EncryptedSharedPreferences`
- A real `profiles` row created on signup, read/updated via PostgREST
- Dashboard / Subscriptions / Alerts / AI tabs show **honest empty states**
  (no data pipeline exists yet, so there is nothing to fabricate) instead of
  placeholder or fake content
- Profile tab shows real account info and a fully working logout
- The **complete Supabase database schema**: all 20 tables from the product
  spec, UUID primary keys, `created_at`/`updated_at`, indexes on `user_id`,
  `transaction_date`, merchant, `event_type`, `subscription_id` and alert
  read-status, and Row Level Security policies on every user-owned table
- Storage bucket + RLS-equivalent storage policies for the future Bank
  Statement / Reports PDF upload paths

**Not built yet** (each is a real phase, not a stub, tracked below):
Permission Center, Bank Statement upload/parsing, Gmail integration,
NotificationListenerService, SMS abstraction, the Financial Event engine
(recurring/subscription/price-hike/duplicate detection), the Alert engine
and Android notification channels wiring, the Gemini Edge Function, Spending
Analytics/Savings/Renewal Calendar/Budget/Goals/Financial Health/Reports
screens, Room local cache, WorkManager background sync, and the full test
suite.

### A note on "compiles cleanly"

This project was authored and carefully reviewed by hand (package/directory
consistency, ViewBinding field names cross-checked against every layout's
`android:id`s, XML well-formedness and Java brace/paren balance all
verified programmatically) in a sandboxed environment **without an Android
SDK**, so `./gradlew build` could not actually be run here. Open the
project in Android Studio, let it sync, and if anything doesn't compile,
report the exact error - the codebase is small enough right now to fix
quickly, and this README will be updated as later phases land.

---

## 1. Architecture

```
com.leakguard.ai
├── data/
│   ├── local/          SessionManager (EncryptedSharedPreferences).
│   │                   Room cache lands in Phase 7.
│   ├── remote/
│   │   ├── supabase/    Retrofit client, Auth/Database API interfaces,
│   │   │                AuthInterceptor, error parsing
│   │   └── dto/          Wire-format classes (Gson)
│   └── repository/      AuthRepository, ProfileRepository - the ONLY
│                         classes allowed to call the network APIs
├── domain/
│   └── model/            Plain domain models (User, Profile, ...)
├── presentation/
│   ├── splash/ onboarding/ auth/ main/
│   ├── dashboard/ subscriptions/ alerts/ insights/ profile/
│   └── common/           Reusable components (EmptyStateView, ...)
├── utils/                 Resource<T>, Constants, ValidationUtils, ...
└── LeakGuardApplication.java
```

Strict layering: Activities/Fragments never call Retrofit directly - only
through a Repository, exposed to the UI via a `ViewModel` + `LiveData`
(`Resource<T>` wraps Loading/Success/Error so every screen renders all
three states, never a raw exception).

---

## 2. Prerequisites

- Android Studio (Koala/2024.1 or newer recommended)
- JDK 17 (bundled with recent Android Studio)
- A free [Supabase](https://supabase.com) project
- (Later phases) A Google Cloud project for Gmail OAuth, and a Gemini API key

---

## 3. Supabase setup

1. Create a project at [supabase.com](https://supabase.com).
2. In the Supabase Dashboard, open **SQL Editor** and run, in order:
   - `supabase/migrations/0001_init_schema.sql` - creates all 20 tables,
     indexes, triggers and RLS policies.
   - `supabase/migrations/0002_storage_setup.sql` - creates the
     `leakguard-files` private bucket and its storage RLS policies (used
     starting Phase 3, safe to run now).
3. Go to **Project Settings -> Data API** (or **API** on older dashboards)
   and copy:
   - **Project URL** -> `SUPABASE_URL`
   - **anon / publishable key** -> `SUPABASE_PUBLISHABLE_KEY`

   Never copy the **service_role** key into anything client-side - it
   bypasses Row Level Security entirely and belongs only in the (future)
   Edge Function secrets, per `.env.example`.
4. Under **Authentication -> Providers -> Email**, decide whether you want
   "Confirm email" on or off while testing. The app handles both: if it's
   on, Register shows a "check your email" dialog instead of logging the
   user straight in.
5. (Optional, recommended) Verify RLS is actually doing its job using the
   checklist at the bottom of `0001_init_schema.sql`.

---

## 4. Android app setup

1. Copy `local.properties.example` to `local.properties` (same folder,
   already git-ignored) and fill in:
   ```properties
   sdk.dir=/path/to/Android/sdk
   SUPABASE_URL=https://YOUR-PROJECT.supabase.co
   SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_ANON_KEY
   ```
   (`GOOGLE_WEB_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID` and
   `BACKEND_BASE_URL` aren't used until the Gmail phase - leave the
   placeholders for now.)
2. Open the project root folder in Android Studio and let it sync. The
   supplied archive does not include the Gradle wrapper scripts or wrapper
   JAR, so use Android Studio's bundled Gradle to sync/build, or install
   Gradle locally and run `gradle wrapper` from the project root to generate
   wrapper files before using `./gradlew`.
3. Run the `app` module on an emulator or device (minSdk 26 / Android 8.0+).
4. You should land on the Splash screen, then Onboarding (first run), then
   Login/Register. Create an account, and you should land on the Dashboard
   tab showing an honest empty state.

---

## 5. Environment variables reference

**Android (`local.properties`, git-ignored):**

| Key | Used for | Needed in Phase 1? |
|---|---|---|
| `SUPABASE_URL` | Supabase REST/Auth base URL | Yes |
| `SUPABASE_PUBLISHABLE_KEY` | Supabase anon key | Yes |
| `GOOGLE_WEB_CLIENT_ID` | Gmail OAuth (server-side code exchange) | Not yet |
| `GOOGLE_ANDROID_CLIENT_ID` | Gmail OAuth (Android client registration) | Not yet |
| `BACKEND_BASE_URL` | Edge Functions base URL | Not yet |

**Backend / Edge Functions (`.env`, git-ignored):**

| Key | Used for | Needed in Phase 1? |
|---|---|---|
| `SUPABASE_URL` | Server-side Supabase access | Not yet (no functions deployed) |
| `SUPABASE_SERVICE_ROLE_KEY` | Bypasses RLS - server-only | Not yet |
| `GEMINI_API_KEY` | `financial-ai` Edge Function | Not yet (Phase 10) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `gmail-sync` Edge Function | Not yet (Phase 4) |
| `TOKEN_ENCRYPTION_SECRET` | Encrypting stored Gmail refresh tokens | Not yet (Phase 4) |

---

## 6. Build / test checklist for this phase

- [ ] `local.properties` created with real Supabase URL + anon key
- [ ] Both SQL migrations run successfully in the Supabase SQL Editor
- [ ] Gradle sync succeeds in Android Studio
- [ ] App launches to Splash -> Onboarding (first run only)
- [ ] Register creates a Supabase Auth user AND a `profiles` row (check the
      **Table Editor** in Supabase)
- [ ] Login works with the account just created
- [ ] Logout clears the session and returns to Login
- [ ] Force-closing and reopening the app after logging in goes straight to
      the Dashboard (session persistence/restoration)
- [ ] Airplane mode + Login attempt shows a "check your connection" message,
      not a crash
- [ ] Switching between the 5 bottom-nav tabs doesn't reset scroll position
      or reload data unnecessarily
- [ ] Back button on any non-Home tab returns to Home before exiting the app

---

## 7. Troubleshooting

- **"LeakGuard isn't connected to its server yet" message on every auth
  screen** - you haven't created `local.properties` from the example, or
  Android Studio hasn't re-synced since you added it. Sync Gradle again.
  (Earlier builds crashed outright here instead of showing this message -
  fixed in `SupabaseConfig`.)
- **"Invalid API key"** - almost always a copy/paste issue in
  `local.properties`: no quotes around the value, the anon/publishable key
  (never `service_role`), and the key must belong to the *same* project as
  `SUPABASE_URL`. Run `./gradlew clean assembleDebug` after editing -
  `BuildConfig` is generated at build time and a plain re-run can reuse a
  stale value.
- **"Invalid path specified in request URL"** - `SUPABASE_URL` has a path on
  it (e.g. pasted the Functions URL, or `/rest/v1`). It must be the origin
  only: `https://xxxxxxxx.supabase.co`, nothing after the host.
  `SupabaseConfig.getBaseUrl()` now strips any path defensively, but keep it
  clean in `local.properties` regardless.
- **401 on every request** - double check you copied the **anon/publishable**
  key, not the service_role key (the app should never hold that one, but
  if it did, RLS-scoped `Authorization: Bearer <anon key>` calls for
  anonymous endpoints would still fail if the wrong key is pasted).
- **Sign-up succeeds but no `profiles` row appears** - check Supabase's
  **Authentication -> Providers -> Email -> Confirm email** setting; if
  it's on, the profile is created immediately after signup regardless, but
  if you're testing against a project where `0001_init_schema.sql` wasn't
  fully applied, the `profiles` insert will fail RLS or schema checks -
  re-run the migration and check the SQL Editor's error output.
- **Gradle can't find `gradle-wrapper.jar`** - see step 2 in Android setup
  above.

---

## 8. Full build roadmap (matches the product spec's own phase plan)

| Phase | Scope |
|---|---|
| 1 (done) | Project setup, theme, architecture, navigation, auth |
| 2 | Full DB schema (done early), Profile screen enrichment |
| 3 | Bank Statement upload (PDF/CSV), parsing, transaction import |
| 4 | Gmail integration (OAuth + `gmail-sync` Edge Function) |
| 5 | NotificationListenerService + Permission Center |
| 6 | SMS policy-compliant integration / fallback |
| 7 | Financial Event engine + Room local cache |
| 8 | Subscriptions, recurring detection, price-change detection, duplicate matching |
| 9 | Alert engine + Android notification channels + WorkManager |
| 10 | Gemini AI (`financial-ai` Edge Function) + AI Insights screen |
| 11 | Spending Analytics, Savings Center, Renewal Calendar, Budget Planner, Financial Goals, Financial Health/Leak Score, Reports |
| 12 | Security hardening, full unit/instrumented test suite, performance pass, final QA |

---

## 9. Security notes (already enforced in Phase 1)

- No secrets are hardcoded; everything flows from `local.properties` /
  `.env` (both git-ignored) into `BuildConfig` fields or Edge Function
  environment variables.
- Session tokens live only in Android Keystore-backed
  `EncryptedSharedPreferences`, excluded from Android auto-backup
  (`res/xml/data_extraction_rules.xml` / `backup_rules.xml`).
- `AuthInterceptor` only attaches Supabase headers to requests whose host
  actually matches the configured Supabase URL, so a token can't leak to
  an unrelated domain even if one is added to the shared OkHttp client later.
- HTTP logging is capped at `BASIC` (method/URL/status/timing) and fully
  disabled in release builds - request/response bodies (which can contain
  tokens or, later, financial data) are never logged.
- Every table in the schema has Row Level Security enabled with
  `auth.uid() = user_id` policies; the service_role key never appears
  anywhere in the Android app.
#   M o b i l e _ L e a k g u a r d - a i  
 