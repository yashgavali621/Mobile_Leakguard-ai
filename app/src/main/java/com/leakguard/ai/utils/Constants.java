package com.leakguard.ai.utils;

public final class Constants {

    private Constants() {
        // no instances
    }

    // SharedPreferences (encrypted) file + keys - see data/local/SessionManager
    public static final String ENCRYPTED_PREFS_FILE = "leakguard_secure_session";
    public static final String PREFS_ACCESS_TOKEN = "access_token";
    public static final String PREFS_REFRESH_TOKEN = "refresh_token";
    public static final String PREFS_USER_ID = "user_id";
    public static final String PREFS_USER_EMAIL = "user_email";
    public static final String PREFS_TOKEN_EXPIRES_AT = "token_expires_at";

    // Plain (non-sensitive) prefs - first-run / onboarding flags only
    public static final String PLAIN_PREFS_FILE = "leakguard_app_prefs";
    public static final String PREFS_ONBOARDING_COMPLETED = "onboarding_completed";

    // Intent extras
    public static final String EXTRA_EMAIL = "extra_email";

    public static final String DEFAULT_CURRENCY = "INR";

    // Supabase REST conventions
    public static final String SUPABASE_REST_PATH = "rest/v1/";
    public static final String SUPABASE_AUTH_PATH = "auth/v1/";
}
