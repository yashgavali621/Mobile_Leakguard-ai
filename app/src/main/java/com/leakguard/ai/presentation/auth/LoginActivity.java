package com.leakguard.ai.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.leakguard.ai.databinding.ActivityLoginBinding;
import com.leakguard.ai.presentation.main.MainActivity;
import com.leakguard.ai.utils.Resource;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.buttonLogin.setOnClickListener(v -> attemptLogin());
        binding.inputPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        binding.textForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
        binding.textGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        viewModel.getLoginState().observe(this, this::render);
    }

    private void attemptLogin() {
        hideKeyboard();
        binding.textError.setVisibility(View.GONE);
        binding.layoutEmail.setError(null);
        binding.layoutPassword.setError(null);
        String email = text(binding.inputEmail);
        String password = text(binding.inputPassword);
        viewModel.login(email, password);
    }

    private void render(Resource<com.leakguard.ai.domain.model.User> state) {
        switch (state.getStatus()) {
            case LOADING:
                setLoading(true);
                binding.textError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                setLoading(false);
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
                break;
            case ERROR:
                setLoading(false);
                if (state.getErrorType() == Resource.ErrorType.VALIDATION) {
                    if (state.getMessage() != null && state.getMessage().toLowerCase().contains("email")) {
                        binding.layoutEmail.setError(state.getMessage());
                    } else {
                        binding.layoutPassword.setError(state.getMessage());
                    }
                } else {
                    binding.textError.setText(state.getMessage());
                    binding.textError.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        }
    }

    private void setLoading(boolean loading) {
        binding.progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.buttonLogin.setEnabled(!loading);
    }

    private static String text(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
