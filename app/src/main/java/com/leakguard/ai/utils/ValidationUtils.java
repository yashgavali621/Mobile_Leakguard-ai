package com.leakguard.ai.utils;

import android.util.Patterns;

/**
 * Shared form-validation rules (section 56). Kept deliberately dependency-free
 * so it's trivial to unit test without an Android instrumentation target -
 * note Patterns.EMAIL_ADDRESS requires the Android framework, so validation
 * tests for this class run as instrumented tests, not plain JVM unit tests.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    public static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean passwordsMatch(String password, String confirmPassword) {
        return password != null && password.equals(confirmPassword);
    }

    /** @return true if the amount string parses to a positive finite number. */
    public static boolean isValidPositiveAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return false;
        }
        try {
            double value = Double.parseDouble(amount.trim());
            return value > 0 && !Double.isInfinite(value) && !Double.isNaN(value);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
