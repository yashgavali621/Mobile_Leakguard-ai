package com.leakguard.ai.domain.model;

/**
 * Mirrors a row of public.profiles. See supabase/migrations/0001_init_schema.sql.
 */
public class Profile {

    private String id;
    private String userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String currency;
    private Double monthlyBudget;
    private boolean onboardingCompleted;

    public Profile() {
        // Gson needs a no-arg constructor
    }

    public Profile(String userId, String fullName, String email, String currency) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.currency = currency;
        this.onboardingCompleted = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(Double monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }
}
