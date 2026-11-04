package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST {SUPABASE_URL}/auth/v1/signup body. */
public class SignUpRequest {

    @SerializedName("email")
    private final String email;

    @SerializedName("password")
    private final String password;

    public SignUpRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }
}
