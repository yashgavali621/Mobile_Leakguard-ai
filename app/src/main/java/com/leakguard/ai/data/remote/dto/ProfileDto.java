package com.leakguard.ai.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Wire format for public.profiles rows via PostgREST
 * ({SUPABASE_URL}/rest/v1/profiles). Field names match the SQL column names
 * exactly (see supabase/migrations/0001_init_schema.sql).
 */
public class ProfileDto {

    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("email")
    private String email;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("currency")
    private String currency;

    @SerializedName("monthly_budget")
    private Double monthlyBudget;

    @SerializedName("onboarding_completed")
    private Boolean onboardingCompleted;

    public ProfileDto() {
    }

    public ProfileDto(String userId, String fullName, String email, String currency) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.currency = currency;
        this.onboardingCompleted = false;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public Double getMonthlyBudget() {
        return monthlyBudget;
    }

    public Boolean getOnboardingCompleted() {
        return onboardingCompleted;
    }
}
