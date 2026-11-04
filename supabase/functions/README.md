# Supabase Edge Functions - roadmap

No Edge Functions are implemented yet - Phase 1 only needs Supabase Auth +
PostgREST, both called directly from the Android app. This folder is a
placeholder for what lands in later phases, so the intended shape is clear
up front:

| Function        | Lands in | Purpose                                                                 |
|------------------|----------|--------------------------------------------------------------------------|
| `gmail-sync`     | Phase 4  | Exchanges the Android-forwarded OAuth auth code for a refresh token, stores it encrypted, and periodically pulls + classifies financial Gmail messages into `email_events` / `financial_events`. |
| `financial-ai`   | Phase 10 | Receives structured (already-normalized) financial data, calls Gemini, validates the JSON response, and writes to `ai_insights`. Never receives raw email/SMS/notification content. |
| `reports`        | Phase 11 | Server-side assembly of the monthly report data backing the Reports screen and PDF export. |

Each function will get its own subfolder here (`gmail-sync/index.ts`,
`financial-ai/index.ts`, `reports/index.ts`) with its own `deno.json` when
implemented, following the Supabase Edge Functions (Deno) conventions:
https://supabase.com/docs/guides/functions
