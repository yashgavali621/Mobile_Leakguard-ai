package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST {SUPABASE_URL}/auth/v1/token?grant_type=password body. */
public class SignInRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("password")
    private final String password;

    public SignInRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
