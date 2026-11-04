package com.leakguard.ai.data.repository;

import android.content.Context;

import com.google.gson.JsonElement;
import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.remote.dto.DataDtos;
import com.leakguard.ai.data.remote.dto.IntegrationDtos.FinancialEvent;
import com.leakguard.ai.data.remote.supabase.ApiErrorParser;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.data.remote.supabase.SupabaseDatabaseApi;
import com.leakguard.ai.utils.AsyncExec;
import com.leakguard.ai.utils.Constants;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Loads the signed-in user's rows (RLS-scoped) for every tab in one go.
 * Uses the real tables (subscriptions / alerts / ai_insights) when they have rows; when they are
 * empty it derives the same views from financial_events, which is what the SMS + Gmail sync writes.
 */
public class DataRepository {

    public static final int TONE_NEUTRAL = 0, TONE_INFO = 1, TONE_SUCCESS = 2, TONE_WARNING = 3, TONE_DANGER = 4;

    public static class Row {
        public final String title, subtitle, trailing, badge;
        public final int tone;

        Row(String title, String subtitle, String trailing, String badge, int tone) {
            this.title = title;
            this.subtitle = subtitle;
            this.trailing = trailing;
            this.badge = badge;
            this.tone = tone;
        }
    }

    public static class Snapshot {
        public final List<Row> subscriptions = new ArrayList<>();
        public final List<Row> alerts = new ArrayList<>();
        public final List<Row> insights = new ArrayList<>();
        public final List<Row> activity = new ArrayList<>();
        public double spend30d;
        public String currency = Constants.DEFAULT_CURRENCY;
        public String error; // non-null if some tables could not be read
    }

    private static final Set<String> SPEND_TYPES = new HashSet<>(Arrays.asList("PAYMENT", "PURCHASE", "SUBSCRIPTION", "RENEWAL", "BILL"));
    private static final Set<String> ALERT_TYPES = new HashSet<>(Arrays.asList(
            "RENEWAL", "PRICE_CHANGE", "FAILED_PAYMENT", "LARGE_TRANSACTION", "UNUSUAL_TRANSACTION", "REFUND", "BILL"));

    private final Context app;

    public DataRepository(Context context) {
        this.app = context.getApplicationContext();
    }

    public void load(RepositoryCallback<Snapshot> callback) {
        AsyncExec.run(this::loadBlocking, callback);
    }

    // ------------------------------------------------------------------ load

    private Snapshot loadBlocking() throws Exception {
        if (!SyncSession.ensure(app)) {
            throw new AsyncExec.Failure("Your session has expired. Please sign in again.", Resource.ErrorType.UNAUTHORIZED);
        }
        String uid = "eq." + SessionManager.getInstance(app).getUserId();
        SupabaseDatabaseApi api = RetrofitClient.databaseApi(app);
        List<String> errs = new ArrayList<>();

        List<DataDtos.Subscription> subs = fetch(api.getSubscriptions(uid, "*", "created_at.desc", 200), "subscriptions", errs);
        List<DataDtos.Alert> alerts = fetch(api.getAlerts(uid, "*", "occurred_at.desc", 100), "alerts", errs);
        List<DataDtos.Insight> insights = fetch(api.getInsights(uid, "*", "created_at.desc", 30), "insights", errs);
        List<FinancialEvent> events = fetch(api.getFinancialEvents(uid, "*",
                "transaction_date.desc.nullslast,created_at.desc", 500), "events", errs);

        if (errs.size() == 4) {
            throw new AsyncExec.Failure(errs.get(0), Resource.ErrorType.SERVER);
        }

        Snapshot s = new Snapshot();
        if (!errs.isEmpty()) s.error = errs.get(0);

        for (FinancialEvent e : events) {
            if (notBlank(e.currency)) {
                s.currency = e.currency;
                break;
            }
        }

        // Subscriptions
        if (!subs.isEmpty()) {
            for (DataDtos.Subscription d : subs) {
                String name = notBlank(d.subscriptionName) ? d.subscriptionName : d.merchant;
                String freq = notBlank(d.frequency) ? label(d.frequency) : "Unknown frequency";
                String next = notBlank(d.nextRenewalDate) ? "Renews " + day(d.nextRenewalDate) : "Renewal date unknown";
                String status = notBlank(d.status) ? d.status : "UNKNOWN";
                int tone = "ACTIVE".equals(status) ? TONE_SUCCESS : "PAUSED".equals(status) ? TONE_WARNING : TONE_NEUTRAL;
                s.subscriptions.add(new Row(name, freq + " · " + next, money(d.amount, d.currency != null ? d.currency : s.currency),
                        label(status), tone));
            }
        } else {
            deriveSubscriptions(events, s);
        }

        // Alerts
        if (!alerts.isEmpty()) {
            for (DataDtos.Alert d : alerts) {
                String sev = notBlank(d.severity) ? d.severity : "INFO";
                int tone = "HIGH".equals(sev) ? TONE_DANGER : "MEDIUM".equals(sev) ? TONE_WARNING : "LOW".equals(sev) ? TONE_INFO : TONE_NEUTRAL;
                String sub = notBlank(d.description) ? d.description : label(d.alertType);
                if (notBlank(d.occurredAt)) sub += " · " + day(d.occurredAt);
                s.alerts.add(new Row(d.title, sub, d.amount != null ? money(d.amount, s.currency) : "", label(sev), tone));
            }
        } else {
            for (FinancialEvent e : events) {
                if (e.eventType == null || !ALERT_TYPES.contains(e.eventType) || s.alerts.size() >= 50) continue;
                s.alerts.add(new Row(label(e.eventType) + " · " + merchant(e), eventSubtitle(e),
                        e.amount != null ? money(e.amount, e.currency != null ? e.currency : s.currency) : "",
                        label(e.eventType), alertTone(e.eventType)));
            }
        }

        // Recent activity (Home)
        for (FinancialEvent e : events) {
            if (s.activity.size() >= 10) break;
            s.activity.add(new Row(merchant(e), eventSubtitle(e),
                    e.amount != null ? money(e.amount, e.currency != null ? e.currency : s.currency) : "",
                    e.source != null ? label(e.source) : "", TONE_NEUTRAL));
        }

        // 30-day spend
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -30);
        String cutoff = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
        int spendCount = 0;
        String topMerchant = null;
        double topAmount = 0;
        for (FinancialEvent e : events) {
            if (e.amount == null || e.transactionDate == null || e.eventType == null) continue;
            if (!SPEND_TYPES.contains(e.eventType) || e.transactionDate.compareTo(cutoff) < 0) continue;
            s.spend30d += e.amount;
            spendCount++;
            if (e.amount > topAmount) {
                topAmount = e.amount;
                topMerchant = merchant(e);
            }
        }

        // Insights
        if (!insights.isEmpty()) {
            for (DataDtos.Insight d : insights) {
                StringBuilder body = new StringBuilder(d.insightText != null ? d.insightText : "");
                JsonElement r = d.recommendations;
                if (r != null && r.isJsonArray()) {
                    for (JsonElement el : r.getAsJsonArray()) {
                        if (el.isJsonPrimitive()) body.append("\n• ").append(el.getAsString());
                    }
                }
                String sev = notBlank(d.severity) ? d.severity : "INFO";
                int tone = "HIGH".equals(sev) ? TONE_DANGER : "MEDIUM".equals(sev) ? TONE_WARNING : "LOW".equals(sev) ? TONE_INFO : TONE_NEUTRAL;
                s.insights.add(new Row(notBlank(d.category) ? label(d.category) : "Insight", body.toString(), "", label(sev), tone));
            }
        } else if (!events.isEmpty()) {
            s.insights.add(new Row(events.size() + " financial events tracked",
                    "Built from your connected SMS / Gmail sources.", "", "On-device", TONE_INFO));
            if (!s.subscriptions.isEmpty()) {
                s.insights.add(new Row(s.subscriptions.size() + " recurring payment(s) detected",
                        "Review them in the Subscriptions tab and cancel anything you no longer use.", "", "On-device", TONE_INFO));
            }
            if (spendCount > 0) {
                s.insights.add(new Row(money(s.spend30d, s.currency) + " spent in the last 30 days",
                        "Across " + spendCount + " payments. Largest: " + topMerchant + " (" + money(topAmount, s.currency) + ").",
                        "", "On-device", TONE_INFO));
            }
            int failed = 0;
            for (FinancialEvent e : events) if ("FAILED_PAYMENT".equals(e.eventType)) failed++;
            if (failed > 0) {
                s.insights.add(new Row(failed + " failed payment(s)", "Check the Alerts tab for the merchants involved.",
                        "", "On-device", TONE_WARNING));
            }
        }
        return s;
    }

    // --------------------------------------------------------------- helpers

    private static <T> List<T> fetch(Call<List<T>> call, String name, List<String> errs) {
        try {
            Response<List<T>> r = call.execute();
            if (r.isSuccessful() && r.body() != null) return r.body();
            errs.add("Couldn't load " + name + ": " + ApiErrorParser.parse(r));
        } catch (IOException e) {
            errs.add("Couldn't reach the server. Check your connection and try again.");
        } catch (RuntimeException e) {
            errs.add("Couldn't read " + name + " (unexpected data format).");
        }
        return new ArrayList<>();
    }

    private static void deriveSubscriptions(List<FinancialEvent> events, Snapshot s) {
        Map<String, Object[]> agg = new LinkedHashMap<>(); // key -> {name, amount, currency, date, count}
        for (FinancialEvent e : events) { // events arrive newest-first
            String t = e.eventType;
            if ("FAILED_PAYMENT".equals(t) || "REFUND".equals(t)) continue;
            boolean rec = Boolean.TRUE.equals(e.isRecurring) || "SUBSCRIPTION".equals(t) || "RENEWAL".equals(t);
            if (!rec) continue;
            String name = merchant(e);
            if ("Unknown merchant".equals(name)) continue;
            String key = notBlank(e.normalizedMerchant) ? e.normalizedMerchant.toLowerCase(Locale.ROOT) : name.toLowerCase(Locale.ROOT);
            Object[] a = agg.get(key);
            if (a == null) {
                agg.put(key, new Object[]{name, e.amount, e.currency, e.transactionDate, 1});
            } else {
                a[4] = (Integer) a[4] + 1;
                if (a[1] == null) a[1] = e.amount;
            }
        }
        for (Object[] a : agg.values()) {
            int n = (Integer) a[4];
            String sub = n + (n == 1 ? " payment" : " payments") + " detected" + (a[3] != null ? " · last " + a[3] : "");
            String cur = a[2] != null ? (String) a[2] : s.currency;
            s.subscriptions.add(new Row((String) a[0], sub, a[1] != null ? money((Double) a[1], cur) : "", "Detected", TONE_INFO));
        }
    }

    private static String eventSubtitle(FinancialEvent e) {
        StringBuilder b = new StringBuilder(e.eventType != null ? label(e.eventType) : "Event");
        if (notBlank(e.transactionDate)) b.append(" · ").append(e.transactionDate);
        if (notBlank(e.source)) b.append(" · ").append(label(e.source));
        return b.toString();
    }

    private static int alertTone(String type) {
        switch (type) {
            case "FAILED_PAYMENT": return TONE_DANGER;
            case "LARGE_TRANSACTION":
            case "UNUSUAL_TRANSACTION":
            case "PRICE_CHANGE": return TONE_WARNING;
            case "REFUND": return TONE_SUCCESS;
            default: return TONE_INFO;
        }
    }

    private static String merchant(FinancialEvent e) {
        if (notBlank(e.merchant)) return e.merchant;
        if (notBlank(e.normalizedMerchant)) return e.normalizedMerchant;
        return "Unknown merchant";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String day(String s) {
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** "FAILED_PAYMENT" -> "Failed payment". */
    private static String label(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    public static String money(Double amount, String currency) {
        if (amount == null) return "";
        String sym;
        if (currency == null || "INR".equals(currency)) sym = "₹";
        else if ("USD".equals(currency)) sym = "$";
        else if ("EUR".equals(currency)) sym = "€";
        else if ("GBP".equals(currency)) sym = "£";
        else sym = currency + " ";
        boolean whole = amount == Math.floor(amount);
        return String.format(Locale.US, whole ? "%s%,.0f" : "%s%,.2f", sym, amount);
    }
}
