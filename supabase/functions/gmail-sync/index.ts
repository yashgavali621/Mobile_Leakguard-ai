// gmail-sync - connects a user's Gmail (read-only), and imports financial emails.
// Actions (POST JSON, user JWT required):
//   {action:"connect", code}  exchange Android serverAuthCode -> refresh token (stored AES-GCM encrypted), then first sync
//   {action:"sync"}           import new financial emails since last sync
//   {action:"disconnect"}     revoke + delete token
// Privacy: only sender / subject / classification / merchant / amount / date are stored.
// Email bodies and snippets are read in memory for classification and never persisted.
import { createClient } from "npm:@supabase/supabase-js@2";

const env = (k: string) => Deno.env.get(k) ?? "";
const SUPABASE_URL = env("SUPABASE_URL");
const ANON = env("SUPABASE_ANON_KEY");
const SERVICE = env("SUPABASE_SERVICE_ROLE_KEY");
const G_ID = env("GOOGLE_CLIENT_ID");
const G_SECRET = env("GOOGLE_CLIENT_SECRET");
const TOKEN_SECRET = env("TOKEN_ENCRYPTION_SECRET");

class HttpError extends Error {
  constructor(public status: number, msg: string) { super(msg); }
}
const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { "Content-Type": "application/json" } });

// ---------- token encryption (AES-GCM, key = base64 32 bytes) ----------
const b64 = (u: Uint8Array) => btoa(String.fromCharCode(...u));
const unb64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const aesKey = () => crypto.subtle.importKey("raw", unb64(TOKEN_SECRET), "AES-GCM", false, ["encrypt", "decrypt"]);
async function encrypt(text: string) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await aesKey(), new TextEncoder().encode(text)));
  return `${b64(iv)}.${b64(ct)}`;
}
async function decrypt(s: string) {
  const [iv, ct] = s.split(".");
  const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64(iv) }, await aesKey(), unb64(ct));
  return new TextDecoder().decode(pt);
}

// ---------- classification ----------
type Cls = "SUBSCRIPTION" | "RENEWAL" | "PAYMENT" | "PRICE_CHANGE" | "BILL" | "REFUND" | "PAYMENT_FAILED" | "PURCHASE";
const EVENT_TYPE: Record<Cls, string> = {
  SUBSCRIPTION: "SUBSCRIPTION", RENEWAL: "RENEWAL", PAYMENT: "PAYMENT", PRICE_CHANGE: "PRICE_CHANGE",
  BILL: "BILL", REFUND: "REFUND", PAYMENT_FAILED: "FAILED_PAYMENT", PURCHASE: "PURCHASE",
};
const PROMO = /\b(\d+% off|flash sale|limited[- ]time offer|save up to|extra \d+%)\b/i;

function classify(text: string): Cls | null {
  const t = text.toLowerCase();
  if (PROMO.test(t) && !/(receipt|invoice|payment (received|successful)|has been (charged|renewed))/.test(t)) return null;
  if (/(payment (failed|declined|unsuccessful)|failed payment|could(n't| not) (process|charge)|card (was )?declined|unable to (process|charge))/.test(t)) return "PAYMENT_FAILED";
  if (/\brefund(ed)?\b/.test(t)) return "REFUND";
  if (/(price (change|increase|hike)|new price|prices? (are|is) (changing|going up)|increase in (your )?(price|rate))/.test(t)) return "PRICE_CHANGE";
  if (/(auto-?renew|will renew|has renewed|been renewed|renewal|renews on)/.test(t)) return "RENEWAL";
  if (/(subscription|membership|trial (ends|will end)|welcome to .*plus|you('| a)re now subscribed)/.test(t)) return "SUBSCRIPTION";
  if (/(\bbill\b|statement is ready|amount due|due date)/.test(t)) return "BILL";
  if (/(receipt|invoice|payment (received|successful|confirmation)|thank you for your payment|has been charged)/.test(t)) return "PAYMENT";
  if (/(your order|order (confirmation|confirmed)|purchase)/.test(t)) return "PURCHASE";
  return null;
}

const AMOUNT = /(rs\.?|inr|\u20B9|\$|usd|eur|\u20AC|\u00A3|gbp)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)/i;
function extractAmount(text: string): { amount: number; currency: string } | null {
  const m = AMOUNT.exec(text);
  if (!m) return null;
  const amount = parseFloat(m[2].replace(/,/g, ""));
  if (!isFinite(amount) || amount <= 0 || amount >= 10_000_000) return null;
  const s = m[1].toLowerCase();
  const currency = s === "$" || s === "usd" ? "USD" : s === "eur" || s === "\u20AC" ? "EUR" : s === "\u00A3" || s === "gbp" ? "GBP" : "INR";
  return { amount, currency };
}

function extractMerchant(from: string): string | null {
  const m = /^\s*"?([^"<]*?)"?\s*<([^>]+)>/.exec(from);
  let name = (m ? m[1] : "").replace(/\b(billing|support|team|payments?|no-?reply|receipts?|invoices?|noreply|notifications?)\b/gi, "").replace(/[-_.]+$/g, "").trim();
  if (!name) {
    const addr = (m ? m[2] : from).split("@")[1] ?? "";
    const parts = addr.replace(/[>\s]/g, "").split(".");
    name = parts.length >= 2 ? parts[parts.length - 2] : "";
  }
  return name ? name.slice(0, 60) : null;
}
const normalize = (s: string | null) => (s ?? "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim() || null;

// ---------- Google helpers ----------
async function tokenRequest(params: Record<string, string>) {
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: G_ID, client_secret: G_SECRET, ...params }),
  });
  return { ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) };
}

async function mapLimit<T, R>(items: T[], limit: number, fn: (x: T) => Promise<R>): Promise<R[]> {
  const out: R[] = [];
  for (let i = 0; i < items.length; i += limit) out.push(...await Promise.all(items.slice(i, i + limit).map(fn)));
  return out;
}

// ---------- main ----------
Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  if (!G_ID || !G_SECRET || !TOKEN_SECRET) return json({ error: "Server is missing Google/encryption secrets." }, 500);

  const userClient = createClient(SUPABASE_URL, ANON, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
  const { data: u, error: ue } = await userClient.auth.getUser();
  if (ue || !u.user) return json({ error: "Unauthorized" }, 401);
  const userId = u.user.id;
  const admin = createClient(SUPABASE_URL, SERVICE);

  const setSource = (status: string, extra: Record<string, unknown> = {}) =>
    admin.from("connected_sources").upsert({ user_id: userId, source_type: "GMAIL", status, ...extra }, { onConflict: "user_id,source_type" });

  let body: { action?: string; code?: string };
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }

  try {
    if (body.action === "connect") {
      if (!body.code) throw new HttpError(400, "Missing authorization code.");
      const t = await tokenRequest({ code: body.code, grant_type: "authorization_code", redirect_uri: "" });
      if (!t.ok) throw new HttpError(400, `Google rejected the sign-in (${t.body.error ?? t.status}).`);
      if (!t.body.refresh_token) throw new HttpError(400, "Google gave no offline access. Remove LeakGuard at myaccount.google.com/permissions and connect again.");
      if (!String(t.body.scope ?? "").includes("gmail.readonly")) throw new HttpError(400, "Gmail read permission was not granted.");
      let email: string | null = null;
      try { email = JSON.parse(atob(t.body.id_token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))).email ?? null; } catch { /* optional */ }
      const { error } = await admin.from("gmail_tokens").upsert({ user_id: userId, refresh_token_enc: await encrypt(t.body.refresh_token), google_email: email, last_sync_at: null });
      if (error) throw new HttpError(500, "Could not store Gmail connection.");
      await setSource("CONNECTED");
      const r = await syncUser();
      return json({ ok: true, connected: true, ...r });
    }
    if (body.action === "sync") return json({ ok: true, connected: true, ...(await syncUser()) });
    if (body.action === "disconnect") {
      const { data: row } = await admin.from("gmail_tokens").select("refresh_token_enc").eq("user_id", userId).maybeSingle();
      if (row) {
        try { await fetch(`https://oauth2.googleapis.com/revoke?token=${encodeURIComponent(await decrypt(row.refresh_token_enc))}`, { method: "POST" }); } catch { /* best effort */ }
        await admin.from("gmail_tokens").delete().eq("user_id", userId);
      }
      await setSource("DISCONNECTED");
      return json({ ok: true, connected: false });
    }
    throw new HttpError(400, "Unknown action.");
  } catch (e) {
    if (e instanceof HttpError) return json({ error: e.message }, e.status);
    console.error("gmail-sync failed:", (e as Error).message);
    return json({ error: "Gmail sync failed. Please try again." }, 500);
  }

  async function syncUser() {
    const { data: row } = await admin.from("gmail_tokens").select("refresh_token_enc,last_sync_at").eq("user_id", userId).maybeSingle();
    if (!row) throw new HttpError(409, "Gmail isn't connected.");
    const tr = await tokenRequest({ refresh_token: await decrypt(row.refresh_token_enc), grant_type: "refresh_token" });
    if (!tr.ok) {
      if (tr.body.error === "invalid_grant") {
        await admin.from("gmail_tokens").delete().eq("user_id", userId);
        await setSource("REQUIRES_PERMISSION");
        throw new HttpError(401, "Google access was revoked. Please reconnect Gmail.");
      }
      throw new HttpError(502, "Could not refresh Gmail access.");
    }
    const gh = { Authorization: `Bearer ${tr.body.access_token}` };
    const startedAt = new Date();
    const afterSec = row.last_sync_at ? Math.floor(new Date(row.last_sync_at).getTime() / 1000) - 3600 : Math.floor(Date.now() / 1000) - 90 * 86400;
    const q = `after:${afterSec} (subscription OR renewal OR renewed OR receipt OR invoice OR payment OR billing OR "your order" OR refund OR "price change" OR "auto-renew" OR "payment failed")`;
    const lr = await fetch(`https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=200&q=${encodeURIComponent(q)}`, { headers: gh });
    if (!lr.ok) throw new HttpError(502, "Gmail rejected the request (" + lr.status + ").");
    const ids: string[] = ((await lr.json()).messages ?? []).map((m: { id: string }) => m.id);

    let imported = 0;
    if (ids.length) {
      const { data: seen } = await userClient.from("email_events").select("gmail_message_id").in("gmail_message_id", ids);
      const seenSet = new Set((seen ?? []).map((r: { gmail_message_id: string }) => r.gmail_message_id));
      const fresh = ids.filter((id) => !seenSet.has(id));

      const parsed = (await mapLimit(fresh, 10, async (id) => {
        const r = await fetch(`https://gmail.googleapis.com/gmail/v1/users/me/messages/${id}?format=metadata&metadataHeaders=From&metadataHeaders=Subject`, { headers: gh });
        if (!r.ok) return null;
        const m = await r.json();
        const h = (n: string) => (m.payload?.headers ?? []).find((x: { name: string }) => x.name.toLowerCase() === n)?.value ?? "";
        const from = h("from"), subject = h("subject"), snippet = m.snippet ?? "";
        const cls = classify(`${subject} ${snippet}`);
        if (!cls) return null;
        const amt = extractAmount(`${subject} ${snippet}`);
        if (!amt && ["PAYMENT", "PURCHASE", "BILL", "REFUND"].includes(cls)) return null;
        const merchant = extractMerchant(from);
        return { id, cls, from, subject: subject.slice(0, 200), merchant, amt, date: new Date(Number(m.internalDate) || Date.now()) };
      })).filter((x): x is NonNullable<typeof x> => x !== null);

      if (parsed.length) {
        const { data: fes, error: fe } = await userClient.from("financial_events").upsert(
          parsed.map((p) => ({
            user_id: userId, source: "GMAIL", source_event_id: p.id, event_type: EVENT_TYPE[p.cls],
            merchant: p.merchant, normalized_merchant: normalize(p.merchant), amount: p.amt?.amount ?? null,
            currency: p.amt?.currency ?? "INR", transaction_date: p.date.toISOString().slice(0, 10),
            confidence: p.amt ? 70 : 55, is_recurring: p.cls === "SUBSCRIPTION" || p.cls === "RENEWAL", raw_reference: `gmail:${p.id}`,
          })),
          { onConflict: "user_id,source,source_event_id", ignoreDuplicates: true },
        ).select("id,source_event_id");
        if (fe) throw new HttpError(500, "Could not save financial events.");
        const feMap = new Map((fes ?? []).map((f: { id: string; source_event_id: string }) => [f.source_event_id, f.id]));
        const { error: ee } = await userClient.from("email_events").upsert(
          parsed.map((p) => ({
            user_id: userId, gmail_message_id: p.id, sender: p.from.slice(0, 200), subject: p.subject, classification: p.cls,
            merchant: p.merchant, amount: p.amt?.amount ?? null, email_date: p.date.toISOString(), processed: true,
            financial_event_id: feMap.get(p.id) ?? null,
          })),
          { onConflict: "user_id,gmail_message_id", ignoreDuplicates: true },
        );
        if (ee) throw new HttpError(500, "Could not save email events.");
        imported = parsed.length;
      }
    }
    await admin.from("gmail_tokens").update({ last_sync_at: startedAt.toISOString() }).eq("user_id", userId);
    await setSource("CONNECTED", { last_sync_at: startedAt.toISOString() });
    return { scanned: ids.length, imported };
  }
});
