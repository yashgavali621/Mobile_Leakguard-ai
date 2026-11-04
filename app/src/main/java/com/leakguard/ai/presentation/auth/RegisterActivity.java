package com.leakguard.ai.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.leakguard.ai.R;
import com.leakguard.ai.data.repository.AuthRepository;
import com.leakguard.ai.databinding.ActivityRegisterBinding;
import com.leakguard.ai.presentation.main.MainActivity;
import com.leakguard.ai.utils.Resource;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.buttonRegister.setOnClickListener(v -> attemptRegister());
        binding.textGoToLogin.setOnClickListener(v -> finish());

        viewModel.getRegisterState().observe(this, this::render);
    }

    private void attemptRegister() {
        binding.layoutFullName.setError(null);
        binding.layoutEmail.setError(null);
        binding.layoutPassword.setError(null);
        binding.layoutConfirmPassword.setError(null);

        String fullName = text(binding.inputFullName);
        String email = text(binding.inputEmail);
        String password = text(binding.inputPassword);
        String confirmPassword = text(binding.inputConfirmPassword);
        viewModel.register(fullName, email, password, confirmPassword);
    }

    private void render(Resource<AuthRepository.SignUpResult> state) {
        switch (state.getStatus()) {
            case LOADING:
                setLoading(true);
                binding.textError.setVisibility(View.GONE);
                break;
            case SUCCESS:
                setLoading(false);
                AuthRepository.SignUpResult result = state.getData();
                if (result != null && result.requiresEmailVerification) {
                    showVerificationDialog();
                } else {
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                }
                break;
            case ERROR:
                setLoading(false);
                String message = state.getMessage();
                if (state.getErrorType() == Resource.ErrorType.VALIDATION && message != null) {
                    routeValidationError(message);
                } else {
                    binding.textError.setText(message);
                    binding.textError.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void routeValidationError(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("name")) {
            binding.layoutFullName.setError(message);
        } else if (lower.contains("email")) {
            binding.layoutEmail.setError(message);
        } else if (lower.contains("match")) {
            binding.layoutConfirmPassword.setError(message);
        } else {
            binding.layoutPassword.setError(message);
        }
    }

    private void showVerificationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Check your email")
                .setMessage(getString(R.string.msg_check_email_verification))
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void setLoading(boolean loading) {
        binding.progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.buttonRegister.setEnabled(!loading);
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
