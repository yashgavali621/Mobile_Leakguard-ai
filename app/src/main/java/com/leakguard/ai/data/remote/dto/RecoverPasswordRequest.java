package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST {SUPABASE_URL}/auth/v1/recover body - triggers Supabase's password reset email. */
public class RecoverPasswordRequest {

    @SerializedName("email")
    private final String email;

    public RecoverPasswordRequest(String email) {
        this.email = email;
    }
}
