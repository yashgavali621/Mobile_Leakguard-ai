package com.leakguard.ai.data.repository;

import android.content.Context;
import android.content.Intent;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.leakguard.ai.BuildConfig;

/**
 * Google Sign-In wrapper that requests read-only Gmail access plus an offline
 * server auth code (exchanged by the gmail-sync Edge Function - the client
 * secret never touches the app).
 *
 * Usage from an Activity:
 *   startActivityForResult(GmailAuthHelper.signInIntent(this), REQ);
 *   onActivityResult -> new GmailRepository(this).connect(GmailAuthHelper.extractServerAuthCode(data), cb);
 */
public final class GmailAuthHelper {

    public static final String GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";

    private GmailAuthHelper() {
    }

    public static boolean isConfigured() {
        String id = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        return id != null && !id.isEmpty() && !id.startsWith("YOUR_");
    }

    private static GoogleSignInClient client(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READONLY))
                .requestServerAuthCode(BuildConfig.GOOGLE_WEB_CLIENT_ID, true) // true = force a refresh token
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    public static Intent signInIntent(Context context) {
        return client(context).getSignInIntent();
    }

    /** @throws ApiException if the user cancelled or Google Sign-In failed (getStatusCode() has details). */
    public static String extractServerAuthCode(Intent data) throws ApiException {
        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
        return account.getServerAuthCode();
    }

    public static void signOut(Context context) {
        client(context).signOut();
    }
}
