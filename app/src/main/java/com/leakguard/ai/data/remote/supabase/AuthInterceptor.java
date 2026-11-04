package com.leakguard.ai.data.remote.supabase;

import androidx.annotation.NonNull;

import com.leakguard.ai.data.local.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the Supabase "apikey" header plus an "Authorization: Bearer ..."
 * header to every request that targets the configured Supabase host.
 *
 * IMPORTANT (login bug fix):
 * GoTrue's *unauthenticated* endpoints - /auth/v1/signup, /auth/v1/token,
 * /auth/v1/recover, /auth/v1/verify, /auth/v1/resend - must be called with the
 * project's anon/publishable key as the bearer token. If a stale or expired
 * user access token is sent instead, GoTrue rejects the call with 401
 * "invalid JWT" and the user can never log back in without clearing app data.
 * Previously this interceptor always preferred the stored token, which is why
 * login, register and password reset all failed with the same error.
 */
public class AuthInterceptor implements Interceptor {

    /** Paths that must always be called anonymously, with the anon key. */
    private static final String[] ANONYMOUS_PATHS = {
            "/auth/v1/signup",
            "/auth/v1/token",
            "/auth/v1/recover",
            "/auth/v1/verify",
            "/auth/v1/resend",
            "/auth/v1/otp",
    };

    private final SessionManager sessionManager;
    private final String supabaseHost;

    public AuthInterceptor(SessionManager sessionManager, String supabaseBaseUrl) {
        this.sessionManager = sessionManager;
        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(supabaseBaseUrl);
        this.supabaseHost = parsed != null ? parsed.host() : null;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        if (supabaseHost == null || !original.url().host().equals(supabaseHost)) {
            // Not talking to Supabase - never leak a session token to another host.
            return chain.proceed(original);
        }

        String anonKey = SupabaseConfig.getPublishableKey();
        String bearer = anonKey;

        if (!isAnonymousEndpoint(original.url().encodedPath())) {
            String userToken = sessionManager.getAccessToken();
            if (userToken != null && !userToken.isEmpty()) {
                bearer = userToken;
            }
        }

        Request.Builder builder = original.newBuilder()
                .header("apikey", anonKey)
                .header("Authorization", "Bearer " + bearer);

        if (original.body() != null && original.header("Content-Type") == null) {
            builder.header("Content-Type", "application/json");
        }

        return chain.proceed(builder.build());
    }

    private static boolean isAnonymousEndpoint(String encodedPath) {
        for (String path : ANONYMOUS_PATHS) {
            if (encodedPath.endsWith(path) || encodedPath.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
