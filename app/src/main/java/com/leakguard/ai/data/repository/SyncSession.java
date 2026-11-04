package com.leakguard.ai.data.repository;

import android.content.Context;

import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.remote.dto.AuthResponse;
import com.leakguard.ai.data.remote.dto.RefreshTokenRequest;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.data.remote.supabase.SupabaseConfig;

import java.io.IOException;

import retrofit2.Response;

/** Blocking session refresh for background threads (WorkManager / AsyncExec). */
public final class SyncSession {

    private SyncSession() {
    }

    /** @return true if a valid (possibly just-refreshed) session exists. Never call on the main thread. */
    public static boolean ensure(Context context) {
        Context app = context.getApplicationContext();
        SessionManager sm = SessionManager.getInstance(app);
        if (!SupabaseConfig.isConfigured() || !sm.hasSession()) return false;
        if (!sm.isSessionExpired()) return true;
        try {
            Response<AuthResponse> r = RetrofitClient.authApi(app)
                    .refreshSession("refresh_token", new RefreshTokenRequest(sm.getRefreshToken())).execute();
            AuthResponse b = r.body();
            if (!r.isSuccessful() || b == null || !b.hasSession()) {
                if (r.code() == 400 || r.code() == 401 || r.code() == 403) sm.clearSession();
                return false;
            }
            AuthResponse.GoTrueUser u = b.getUser();
            long exp = System.currentTimeMillis() / 1000L + (b.getExpiresIn() != null ? b.getExpiresIn() : 3600L);
            sm.saveSession(b.getAccessToken(), b.getRefreshToken(),
                    u != null ? u.getId() : sm.getUserId(),
                    u != null ? u.getEmail() : sm.getUserEmail(), exp);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
