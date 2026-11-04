package com.leakguard.ai.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.leakguard.ai.utils.Constants;

/** Non-sensitive per-user integration flags (no tokens, no message content). */
public final class IntegrationPrefs {

    private final SharedPreferences prefs;
    private final SessionManager session;

    public IntegrationPrefs(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(Constants.PLAIN_PREFS_FILE, Context.MODE_PRIVATE);
        this.session = SessionManager.getInstance(app);
    }

    private String k(String base) {
        String uid = session.getUserId();
        return base + "_" + (uid == null ? "none" : uid);
    }

    public boolean isSmsEnabled() { return prefs.getBoolean(k("sms_enabled"), false); }
    public void setSmsEnabled(boolean v) { prefs.edit().putBoolean(k("sms_enabled"), v).apply(); }

    public boolean isGmailConnected() { return prefs.getBoolean(k("gmail_connected"), false); }
    public void setGmailConnected(boolean v) { prefs.edit().putBoolean(k("gmail_connected"), v).apply(); }

    /** Epoch millis of the last completed SMS inbox import (0 = never). */
    public long getLastSmsSync() { return prefs.getLong(k("last_sms_sync"), 0L); }
    public void setLastSmsSync(long ms) { prefs.edit().putLong(k("last_sms_sync"), ms).apply(); }
}
