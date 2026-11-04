package com.leakguard.ai.data.remote.supabase;

import com.google.gson.Gson;
import com.leakguard.ai.data.remote.dto.ApiErrorResponse;

import retrofit2.Response;

/**
 * Turns a failed Retrofit Response into a short, user-safe message. Never
 * surfaces raw stack traces or server internals (product spec section 53).
 */
public final class ApiErrorParser {

    private static final Gson GSON = new Gson();

    private ApiErrorParser() {
    }

    public static String parse(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String raw = response.errorBody().string();
                ApiErrorResponse parsed = GSON.fromJson(raw, ApiErrorResponse.class);
                if (parsed != null && parsed.getBestMessage() != null) {
                    return parsed.getBestMessage();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic status-code based message below.
        }
        return genericMessageForCode(response.code());
    }

    public static String genericMessageForCode(int code) {
        switch (code) {
            case 400:
                return "That request wasn't valid. Double-check the details and try again.";
            case 401:
                return "Your session has expired. Please log in again.";
            case 403:
                return "You don't have permission to do that.";
            case 404:
                return "We couldn't find what you were looking for.";
            case 409:
                return "An account with that email already exists.";
            case 422:
                return "Please check the information you entered.";
            case 429:
                return "Too many attempts. Please wait a moment and try again.";
            default:
                if (code >= 500) {
                    return "Our server is having trouble right now. Please try again shortly.";
                }
                return "Something went wrong. Please try again.";
        }
    }
}
