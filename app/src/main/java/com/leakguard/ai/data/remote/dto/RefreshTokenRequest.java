package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST {SUPABASE_URL}/auth/v1/token?grant_type=refresh_token body. */
public class RefreshTokenRequest {

    @SerializedName("refresh_token")
    private final String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
