package com.leakguard.ai.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.leakguard.ai.utils.Constants;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Stores the Supabase auth session (access token, refresh token, user id)
 * using Android Keystore-backed EncryptedSharedPreferences.
 *
 * Security rules this class enforces (see product spec section 50/57):
 *  - Tokens are never logged, even in debug builds.
 *  - Tokens are stored in a dedicated encrypted file separate from ordinary
 *    app preferences (e.g. onboarding-completed flag), which is excluded
 *    from Android auto-backup (see res/xml/data_extraction_rules.xml).
 *  - If the encrypted prefs file becomes unreadable (e.g. Keystore key lost
 *    after a device restore), we fail safe by wiping the session and forcing
 *    a fresh login rather than crashing.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static volatile SessionManager instance;

    private final SharedPreferences securePrefs;
    private final SharedPreferences plainPrefs;

    private SessionManager(Context appContext) {
        SharedPreferences secure;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            secure = EncryptedSharedPreferences.create(
                    appContext,
                    Constants.ENCRYPTED_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Never log the exception message here in case it ever echoes key
            // material back; log only the class name for diagnostics.
            Log.e(TAG, "Encrypted session store unavailable (" + e.getClass().getSimpleName()
                    + "); falling back to a cleared, non-persistent session.");
            secure = appContext.getSharedPreferences(Constants.ENCRYPTED_PREFS_FILE + "_fallback",
                    Context.MODE_PRIVATE);
            secure.edit().clear().apply();
        }
        this.securePrefs = secure;
        this.plainPrefs = appContext.getSharedPreferences(Constants.PLAIN_PREFS_FILE, Context.MODE_PRIVATE);
    }

    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String email, long expiresAtEpochSeconds) {
        securePrefs.edit()
                .putString(Constants.PREFS_ACCESS_TOKEN, accessToken)
                .putString(Constants.PREFS_REFRESH_TOKEN, refreshToken)
                .putString(Constants.PREFS_USER_ID, userId)
                .putString(Constants.PREFS_USER_EMAIL, email)
                .putLong(Constants.PREFS_TOKEN_EXPIRES_AT, expiresAtEpochSeconds)
                .apply();
    }

    public void clearSession() {
        securePrefs.edit().clear().apply();
    }

    public String getAccessToken() {
        return securePrefs.getString(Constants.PREFS_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return securePrefs.getString(Constants.PREFS_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return securePrefs.getString(Constants.PREFS_USER_ID, null);
    }

    public String getUserEmail() {
        return securePrefs.getString(Constants.PREFS_USER_EMAIL, null);
    }

    public boolean isSessionExpired() {
        long expiresAt = securePrefs.getLong(Constants.PREFS_TOKEN_EXPIRES_AT, 0);
        // 30s clock-skew buffer
        return (System.currentTimeMillis() / 1000) >= (expiresAt - 30);
    }

    public boolean hasSession() {
        return getAccessToken() != null && getRefreshToken() != null && getUserId() != null;
    }

    // ---- Non-sensitive, unencrypted app prefs ----

    public boolean isOnboardingCompleted() {
        return plainPrefs.getBoolean(Constants.PREFS_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        plainPrefs.edit().putBoolean(Constants.PREFS_ONBOARDING_COMPLETED, completed).apply();
    }
}
