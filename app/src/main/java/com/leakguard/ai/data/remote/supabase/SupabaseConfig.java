package com.leakguard.ai.data.remote.supabase;

import com.leakguard.ai.BuildConfig;

import okhttp3.HttpUrl;

/**
 * Reads the Supabase connection details injected at build time from
 * local.properties (see app/build.gradle buildConfigField calls).
 *
 * IMPORTANT (crash fix): these getters used to throw IllegalStateException
 * when local.properties was missing. Because AuthRepository is constructed in
 * SplashActivity.onCreate() and in AuthViewModel's constructor, that threw
 * during Activity creation and the app died on launch / on every auth screen -
 * which is why "everything had the same problem". They now degrade gracefully
 * and callers check isConfigured() so the user sees a readable message.
 */
public final class SupabaseConfig {

    /** Syntactically valid placeholder so Retrofit can still be built. */
    private static final String PLACEHOLDER_URL = "https://not-configured.invalid/";

    public static final String NOT_CONFIGURED_MESSAGE =
            "LeakGuard isn't connected to its server yet. Copy local.properties.example to "
                    + "local.properties and fill in SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY, "
                    + "then rebuild the app.";

    private SupabaseConfig() {
    }

    /**
     * Returns the project origin only - scheme://host[:port]/ - with any path,
     * query or fragment stripped.
     *
     * Retrofit resolves each endpoint's relative path ("auth/v1/signup",
     * "rest/v1/profiles") against this base URL, so ANY path left on the base
     * URL gets prepended to every call. Pasting the functions URL or a
     * /rest/v1 URL into SUPABASE_URL therefore produced requests like
     * https://xxx.supabase.co/functions/v1/auth/v1/signup, which Supabase's
     * gateway rejects with "Invalid path specified in request URL". Normalising
     * here makes the app tolerant of every common paste mistake.
     */
    public static String getBaseUrl() {
        String raw = BuildConfig.SUPABASE_URL;
        if (raw == null || raw.trim().isEmpty()) {
            return PLACEHOLDER_URL;
        }
        raw = raw.trim();

        HttpUrl parsed = HttpUrl.parse(raw);
        if (parsed == null) {
            // Tolerate a URL pasted without its scheme, e.g. "xxx.supabase.co".
            parsed = HttpUrl.parse("https://" + raw);
        }
        if (parsed == null) {
            return PLACEHOLDER_URL;
        }

        return new HttpUrl.Builder()
                .scheme(parsed.scheme())
                .host(parsed.host())
                .port(parsed.port())
                .build()
                .toString();   // always ends with "/"
    }

    public static String getPublishableKey() {
        String key = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
        return (key == null) ? "" : key.trim();
    }

    public static String getBackendBaseUrl() {
        return BuildConfig.BACKEND_BASE_URL;
    }

    public static boolean isConfigured() {
        String key = BuildConfig.SUPABASE_PUBLISHABLE_KEY;
        return !PLACEHOLDER_URL.equals(getBaseUrl())
                && key != null && !key.trim().isEmpty();
    }

    /**
     * Host + a masked fragment of the key, safe to log or show on a debug
     * screen. Never returns enough of the key to be usable.
     */
    public static String describeForDiagnostics() {
        String key = getPublishableKey();
        String maskedKey;
        if (key.isEmpty()) {
            maskedKey = "(missing)";
        } else if (key.length() < 14) {
            maskedKey = "(suspiciously short: " + key.length() + " chars)";
        } else {
            maskedKey = key.substring(0, 6) + "..." + key.substring(key.length() - 4)
                    + " (" + key.length() + " chars)";
        }
        return "base=" + getBaseUrl() + " key=" + maskedKey;
    }
}
