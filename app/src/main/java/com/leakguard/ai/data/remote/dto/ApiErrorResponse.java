package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Supabase error responses aren't perfectly consistent between GoTrue
 * ("msg"/"error_description") and PostgREST ("message"). This DTO captures
 * every field we've seen so ApiErrorParser (see RetrofitClient) can fall
 * back gracefully instead of surfacing a raw stack trace to the user.
 */
public class ApiErrorResponse {

    @SerializedName("msg")
    private String msg;

    @SerializedName("error_description")
    private String errorDescription;

    @SerializedName("error")
    private String error;

    @SerializedName("message")
    private String message;

    public String getBestMessage() {
        if (msg != null) return msg;
        if (errorDescription != null) return errorDescription;
        if (message != null) return message;
        if (error != null) return error;
        return null;
    }
}
