package com.leakguard.ai.data.repository;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.leakguard.ai.data.local.IntegrationPrefs;
import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.remote.dto.IntegrationDtos;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.data.remote.supabase.SupabaseDatabaseApi;
import com.leakguard.ai.service.IntegrationSyncWorker;
import com.leakguard.ai.utils.AsyncExec;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;
import com.leakguard.ai.utils.SmsParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import retrofit2.Response;

/**
 * SMS integration. Messages are read and classified ON DEVICE (SmsParser);
 * only amount / merchant / event type / sender / time are uploaded - never the
 * SMS body. Dedupe key = SHA-256(sender|time|body) stored as source_event_id.
 */
public class SmsRepository {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_INBOX_ROWS = 1500;
    private static final int BATCH = 100;

    public static final class RawSms {
        public final String sender;
        public final String body;
        public final long timestampMs;

        public RawSms(String sender, String body, long timestampMs) {
            this.sender = sender;
            this.body = body;
            this.timestampMs = timestampMs;
        }
    }

    private final Context ctx;
    private final SupabaseDatabaseApi db;
    private final SessionManager session;
    private final IntegrationPrefs prefs;

    public SmsRepository(Context context) {
        this.ctx = context.getApplicationContext();
        this.db = RetrofitClient.databaseApi(ctx);
        this.session = SessionManager.getInstance(ctx);
        this.prefs = new IntegrationPrefs(ctx);
    }

    public static boolean hasSmsPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Call AFTER the user granted READ_SMS + RECEIVE_SMS. Imports the last 90 days and starts background sync. */
    public void enable(@NonNull RepositoryCallback<Integer> callback) {
        AsyncExec.run(() -> {
            if (!hasSmsPermission(ctx)) {
                throw new AsyncExec.Failure("SMS permission was not granted.", Resource.ErrorType.VALIDATION);
            }
            if (!SyncSession.ensure(ctx)) {
                throw new AsyncExec.Failure("Please log in again.", Resource.ErrorType.UNAUTHORIZED);
            }
            long started = System.currentTimeMillis();
            int n = importInbox(started - 90 * DAY_MS);
            if (n < 0) {
                throw new AsyncExec.Failure("Couldn't reach the server. Check your connection and try again.",
                        Resource.ErrorType.NETWORK);
            }
            prefs.setSmsEnabled(true);
            prefs.setLastSmsSync(started);
            markSource("CONNECTED");
            IntegrationSyncWorker.schedule(ctx);
            return n;
        }, callback);
    }

    public void disable(@NonNull RepositoryCallback<Void> callback) {
        AsyncExec.run(() -> {
            prefs.setSmsEnabled(false);
            if (SyncSession.ensure(ctx)) markSource("DISCONNECTED");
            return null;
        }, callback);
    }

    /** Blocking. Reads the SMS inbox since sinceMs and uploads new financial events. @return new events, or -1 on failure. */
    public int importInbox(long sinceMs) {
        if (!hasSmsPermission(ctx)) return -1;
        List<RawSms> raws = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                Telephony.Sms.DATE + " >= ?", new String[]{String.valueOf(sinceMs)},
                Telephony.Sms.DATE + " DESC")) {
            if (c != null) {
                while (c.moveToNext() && raws.size() < MAX_INBOX_ROWS) {
                    String sender = c.getString(0);
                    String body = c.getString(1);
                    // Cheap local pre-filter so non-financial SMS are dropped immediately.
                    if (SmsParser.parse(sender, body) != null) raws.add(new RawSms(sender, body, c.getLong(2)));
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return -1;
        }
        return process(raws);
    }

    /** Blocking. @return number of NEW events stored, or -1 on failure. */
    public int process(List<RawSms> raws) {
        String userId = session.getUserId();
        if (userId == null) return -1;

        List<IntegrationDtos.FinancialEvent> events = new ArrayList<>();
        Map<String, IntegrationDtos.SmsEvent> smsByHash = new HashMap<>();
        for (RawSms raw : raws) {
            SmsParser.Result r = SmsParser.parse(raw.sender, raw.body);
            if (r == null) continue;
            String hash = sha256(raw.sender + "|" + raw.timestampMs + "|" + raw.body);
            if (smsByHash.containsKey(hash)) continue;

            IntegrationDtos.FinancialEvent fe = new IntegrationDtos.FinancialEvent();
            fe.userId = userId;
            fe.source = "SMS";
            fe.sourceEventId = hash;
            fe.eventType = r.eventType;
            fe.merchant = r.merchant;
            fe.normalizedMerchant = r.normalizedMerchant;
            fe.amount = r.amount;
            fe.currency = "INR";
            fe.transactionDate = fmt("yyyy-MM-dd", raw.timestampMs);
            fe.confidence = r.merchant != null ? 75.0 : 60.0;
            fe.isRecurring = r.recurring;
            events.add(fe);

            IntegrationDtos.SmsEvent se = new IntegrationDtos.SmsEvent();
            se.userId = userId;
            se.sender = raw.sender;
            se.merchant = r.merchant;
            se.amount = r.amount;
            se.eventType = r.eventType;
            se.receivedAt = fmt("yyyy-MM-dd'T'HH:mm:ss'Z'", raw.timestampMs);
            se.processed = true;
            smsByHash.put(hash, se);
        }

        int stored = 0;
        try {
            for (int i = 0; i < events.size(); i += BATCH) {
                List<IntegrationDtos.FinancialEvent> chunk = events.subList(i, Math.min(i + BATCH, events.size()));
                Response<List<IntegrationDtos.FinancialEvent>> resp = db.upsertFinancialEvents(new ArrayList<>(chunk)).execute();
                if (!resp.isSuccessful() || resp.body() == null) return -1;

                List<IntegrationDtos.SmsEvent> smsRows = new ArrayList<>();
                for (IntegrationDtos.FinancialEvent inserted : resp.body()) { // duplicates are not returned
                    IntegrationDtos.SmsEvent se = smsByHash.get(inserted.sourceEventId);
                    if (se != null) {
                        se.financialEventId = inserted.id;
                        smsRows.add(se);
                    }
                }
                if (!smsRows.isEmpty()) {
                    Response<Void> sr = db.insertSmsEvents(smsRows).execute();
                    if (!sr.isSuccessful()) return -1;
                    stored += smsRows.size();
                }
            }
        } catch (IOException e) {
            return -1;
        }
        return stored;
    }

    private void markSource(String status) {
        String userId = session.getUserId();
        if (userId == null) return;
        try {
            db.upsertConnectedSource(new IntegrationDtos.ConnectedSource(userId, "SMS", status,
                    fmt("yyyy-MM-dd'T'HH:mm:ss'Z'", System.currentTimeMillis()))).execute();
        } catch (IOException ignored) {
            // status row is informational; the import itself already succeeded
        }
    }

    private static String fmt(String pattern, long ms) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
