package com.leakguard.ai.presentation.splash;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.repository.AuthRepository;
import com.leakguard.ai.databinding.ActivitySplashBinding;
import com.leakguard.ai.presentation.auth.LoginActivity;
import com.leakguard.ai.presentation.main.MainActivity;
import com.leakguard.ai.presentation.onboarding.OnboardingActivity;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

/**
 * Startup sequence (product spec section 94):
 *   restore session -> check onboarding -> check auth -> route.
 * No artificial delay is added; the only "wait" is the real work of
 * checking/refreshing the session, which is already fast.
 */
public class SplashActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = SessionManager.getInstance(this);
        authRepository = new AuthRepository(this);

        route();
    }

    private void route() {
        if (!sessionManager.isOnboardingCompleted()) {
            goTo(OnboardingActivity.class);
            return;
        }

        if (!authRepository.isLoggedIn()) {
            goTo(LoginActivity.class);
            return;
        }

        // We have a stored session - make sure it's still valid (refreshing
        // it if the access token has expired) before dropping the user into
        // the Dashboard. If the refresh token itself is dead, fall back to
        // Login rather than showing a broken authenticated screen.
        authRepository.refreshSessionIfNeeded(new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                goTo(MainActivity.class);
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                goTo(LoginActivity.class);
            }
        });
    }

    private void goTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
        finish();
    }
}
