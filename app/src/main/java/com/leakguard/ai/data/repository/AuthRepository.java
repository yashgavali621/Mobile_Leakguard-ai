package com.leakguard.ai.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.remote.dto.AuthResponse;
import com.leakguard.ai.data.remote.dto.RecoverPasswordRequest;
import com.leakguard.ai.data.remote.dto.RefreshTokenRequest;
import com.leakguard.ai.data.remote.dto.SignInRequest;
import com.leakguard.ai.data.remote.dto.SignUpRequest;
import com.leakguard.ai.data.remote.supabase.ApiErrorParser;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.data.remote.supabase.SupabaseConfig;
import com.leakguard.ai.data.remote.supabase.SupabaseAuthApi;
import com.leakguard.ai.domain.model.User;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Owns every call to Supabase Auth (GoTrue). Activities/Fragments never talk
 * to SupabaseAuthApi directly - only through this repository and its
 * ViewModel (product spec section 64/65).
 */
public class AuthRepository {

    private final SupabaseAuthApi authApi;
    private final SessionManager sessionManager;
    private final ProfileRepository profileRepository;

    public AuthRepository(Context context) {
        this.authApi = RetrofitClient.authApi(context);
        this.sessionManager = SessionManager.getInstance(context);
        this.profileRepository = new ProfileRepository(context);
    }

    /** True when SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY were supplied at build time. */
    private boolean notConfigured(RepositoryCallback<?> callback) {
        if (SupabaseConfig.isConfigured()) {
            return false;
        }
        callback.onError(SupabaseConfig.NOT_CONFIGURED_MESSAGE, Resource.ErrorType.SERVER);
        return true;
    }

    public boolean isLoggedIn() {
        return sessionManager.hasSession();
    }

    /** Result of a sign-up attempt. Distinguishes "logged in immediately" from
     *  "check your email" so LoginActivity/RegisterActivity can react correctly. */
    public static class SignUpResult {
        public final boolean requiresEmailVerification;
        public final User user;

        SignUpResult(boolean requiresEmailVerification, User user) {
            this.requiresEmailVerification = requiresEmailVerification;
            this.user = user;
        }
    }

    public void signUp(@NonNull String email, @NonNull String password, @NonNull String fullName,
                        @NonNull RepositoryCallback<SignUpResult> callback) {
        if (notConfigured(callback)) return;
        authApi.signUp(new SignUpRequest(email, password)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError(ApiErrorParser.parse(response), Resource.ErrorType.SERVER);
                    return;
                }
                AuthResponse body = response.body();
                AuthResponse.GoTrueUser gotrueUser = body.getUser();
                User user = gotrueUser != null
                        ? new User(gotrueUser.getId(), gotrueUser.getEmail(), gotrueUser.isEmailConfirmed())
                        : null;

                if (body.hasSession() && user != null) {
                    persistSession(body, user);
                    // Create the profile row now that we have an authenticated session
                    // (RLS requires auth.uid() = user_id, so this can't happen before login).
                    profileRepository.createInitialProfile(user.getId(), fullName, email,
                            new RepositoryCallback<Void>() {
                                @Override
                                public void onSuccess(Void data) {
                                    callback.onSuccess(new SignUpResult(false, user));
                                }

                                @Override
                                public void onError(String message, Resource.ErrorType errorType) {
                                    // The auth account exists even if the profile insert failed;
                                    // don't block the user - DashboardViewModel can retry profile
                                    // creation lazily. Still report success for the sign-up step.
                                    callback.onSuccess(new SignUpResult(false, user));
                                }
                            });
                } else {
                    // Email confirmation required before a session is issued.
                    callback.onSuccess(new SignUpResult(true, user));
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    public void signIn(@NonNull String email, @NonNull String password,
                        @NonNull RepositoryCallback<User> callback) {
        if (notConfigured(callback)) return;
        authApi.signIn("password", new SignInRequest(email, password)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                if (!response.isSuccessful() || response.body() == null || !response.body().hasSession()) {
                    String message = ApiErrorParser.parse(response);
                    boolean looksUnconfirmed = message != null && message.toLowerCase().contains("confirm");
                    if (!looksUnconfirmed) {
                        // A stale session left over from a previous install/expiry would
                        // otherwise keep poisoning every later request.
                        sessionManager.clearSession();
                    }
                    callback.onError(message, looksUnconfirmed
                            ? Resource.ErrorType.EMAIL_NOT_VERIFIED
                            : Resource.ErrorType.UNAUTHORIZED);
                    return;
                }
                AuthResponse body = response.body();
                AuthResponse.GoTrueUser gotrueUser = body.getUser();
                if (gotrueUser == null) {
                    callback.onError("Login succeeded but no user was returned. Please try again.",
                            Resource.ErrorType.UNKNOWN);
                    return;
                }
                User user = new User(gotrueUser.getId(), gotrueUser.getEmail(), gotrueUser.isEmailConfirmed());
                persistSession(body, user);
                callback.onSuccess(user);
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    public void sendPasswordReset(@NonNull String email, @NonNull RepositoryCallback<Void> callback) {
        if (notConfigured(callback)) return;
        authApi.recoverPassword(new RecoverPasswordRequest(email)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(ApiErrorParser.parse(response), Resource.ErrorType.SERVER);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError(networkErrorMessage(t), classifyThrowable(t));
            }
        });
    }

    /** Refresh the access token using the stored refresh token. Call this
     *  proactively (e.g. from SplashActivity) or reactively after a 401. */
    public void refreshSessionIfNeeded(@NonNull RepositoryCallback<Void> callback) {
        if (notConfigured(callback)) return;
        if (!sessionManager.hasSession()) {
            callback.onError("Not logged in", Resource.ErrorType.UNAUTHORIZED);
            return;
        }
        if (!sessionManager.isSessionExpired()) {
            callback.onSuccess(null);
            return;
        }
        authApi.refreshSession("refresh_token", new RefreshTokenRequest(sessionManager.getRefreshToken()))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                        if (!response.isSuccessful() || response.body() == null || !response.body().hasSession()) {
                            // Refresh token is invalid/expired - the user must log in again.
                            sessionManager.clearSession();
                            callback.onError("Your session has expired. Please log in again.",
                                    Resource.ErrorType.UNAUTHORIZED);
                            return;
                        }
                        AuthResponse body = response.body();
                        AuthResponse.GoTrueUser gotrueUser = body.getUser();
                        User user = gotrueUser != null
                                ? new User(gotrueUser.getId(), gotrueUser.getEmail(), gotrueUser.isEmailConfirmed())
                                : new User(sessionManager.getUserId(), sessionManager.getUserEmail(), true);
                        persistSession(body, user);
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                        callback.onError(networkErrorMessage(t), classifyThrowable(t));
                    }
                });
    }

    public void signOut(@NonNull RepositoryCallback<Void> callback) {
        authApi.logout().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // Whether or not the server-side logout call succeeds, always clear
                // the local session so the device stops being "logged in".
                sessionManager.clearSession();
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                sessionManager.clearSession();
                callback.onSuccess(null);
            }
        });
    }

    private void persistSession(AuthResponse body, User user) {
        long expiresAt = (System.currentTimeMillis() / 1000L) + (body.getExpiresIn() != null ? body.getExpiresIn() : 3600L);
        sessionManager.saveSession(body.getAccessToken(), body.getRefreshToken(), user.getId(), user.getEmail(), expiresAt);
    }

    private static String networkErrorMessage(Throwable t) {
        if (t instanceof IOException) {
            return "Couldn't reach the server. Check your connection and try again.";
        }
        return "Something went wrong. Please try again.";
    }

    private static Resource.ErrorType classifyThrowable(Throwable t) {
        return (t instanceof IOException) ? Resource.ErrorType.NETWORK : Resource.ErrorType.UNKNOWN;
    }
}
