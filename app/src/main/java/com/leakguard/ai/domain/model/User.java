package com.leakguard.ai.domain.model;

/**
 * Authenticated Supabase user (auth.users) - the minimal identity the
 * Android app is allowed to hold about the signed-in account.
 */
public class User {

    private final String id;
    private final String email;
    private final boolean emailConfirmed;

    public User(String id, String email, boolean emailConfirmed) {
        this.id = id;
        this.email = email;
        this.emailConfirmed = emailConfirmed;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailConfirmed() {
        return emailConfirmed;
    }
}
