package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response shape returned by Supabase GoTrue for /signup, /token?grant_type=password
 * and /token?grant_type=refresh_token.
 *
 * IMPORTANT: when email confirmation is required by the Supabase project's Auth
 * settings, POST /signup returns 200 with a "user" object but NO access_token
 * (session is null until the user clicks the confirmation link). AuthRepository
 * treats that case as "verification required", not an error.
 */
public class AuthResponse {

    @SerializedName("access_token")
    private String accessToken;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("expires_in")
    private Long expiresIn;

    @SerializedName("token_type")
    private String tokenType;

    @SerializedName("user")
    private GoTrueUser user;

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public GoTrueUser getUser() {
        return user;
    }

    public boolean hasSession() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public static class GoTrueUser {
        @SerializedName("id")
        private String id;

        @SerializedName("email")
        private String email;

        @SerializedName("email_confirmed_at")
        private String emailConfirmedAt;

        public String getId() {
            return id;
        }

        public String getEmail() {
            return email;
        }

        public boolean isEmailConfirmed() {
            return emailConfirmedAt != null;
        }
    }
}
