# Email (Gmail) + SMS integration - what changed

No UI files were touched. New/changed files only.

## Setup (once)
1. Supabase SQL Editor: run `supabase/migrations/0003_email_sms_integration.sql`.
2. Google Cloud: enable **Gmail API**; OAuth consent screen -> add scope `gmail.readonly` + your test users;
   create a **Web** client (-> `GOOGLE_WEB_CLIENT_ID`, secret) and an **Android** client (package `com.leakguard.ai` + your SHA-1).
3. Put `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID` in `local.properties`.
4. Deploy the function:
   ```
   supabase secrets set GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... TOKEN_ENCRYPTION_SECRET=$(openssl rand -base64 32)
   supabase functions deploy gmail-sync
   ```
   (`SUPABASE_URL/ANON_KEY/SERVICE_ROLE_KEY` are injected automatically.)
5. Gradle sync (adds WorkManager + play-services-auth).

## How it works
**SMS** - on-device only. `SmsParser` drops OTPs, personal numbers, credits, promos; for bank/UPI debit, mandate,
renewal, refund and failed-payment SMS it uploads only amount/merchant/type/sender/time (never the body) to
`financial_events` + `sms_events`. `SmsReceiver` triggers a sync on new SMS; `IntegrationSyncWorker` re-scans every 6 h.
Dedupe: SHA-256 of the SMS = `source_event_id` (unique per user).

**Gmail** - app gets a one-time server auth code -> `gmail-sync` Edge Function exchanges it, stores the refresh token
AES-GCM-encrypted in `gmail_tokens` (no client access), scans financial mail, classifies it and writes `email_events` +
`financial_events` (sender/subject/merchant/amount only; no bodies). Also updates `connected_sources`.

## Hooks for the existing screens (call these from your current buttons)
```java
// SMS: after the user grants READ_SMS + RECEIVE_SMS
new SmsRepository(ctx).enable(callback);      // imports last 90 days, starts background sync
new SmsRepository(ctx).disable(callback);

// Gmail
startActivityForResult(GmailAuthHelper.signInIntent(this), 1001);
// onActivityResult:
new GmailRepository(this).connect(GmailAuthHelper.extractServerAuthCode(data), callback);
new GmailRepository(this).syncNow(callback);  // manual sync
new GmailRepository(this).disconnect(callback);
```
All callbacks are `RepositoryCallback` on the main thread.

## Caveats
- Not built in Android Studio here (no SDK). `SmsParser` was compiled and tested on sample bank SMS; the rest was reviewed by hand.
- `gmail.readonly` is a Google *restricted* scope: fine for test users; public release needs Google verification.
  `READ_SMS` is restricted on Google Play; fine for sideloading/dev.
