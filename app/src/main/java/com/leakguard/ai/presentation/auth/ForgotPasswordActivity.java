package com.leakguard.ai.presentation.auth;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.leakguard.ai.R;
import com.leakguard.ai.databinding.ActivityForgotPasswordBinding;
import com.leakguard.ai.utils.Resource;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.buttonSendReset.setOnClickListener(v -> {
            binding.layoutEmail.setError(null);
            String email = binding.inputEmail.getText() != null ? binding.inputEmail.getText().toString() : "";
            viewModel.sendPasswordReset(email);
        });

        viewModel.getPasswordResetState().observe(this, this::render);
    }

    private void render(Resource<Void> state) {
        switch (state.getStatus()) {
            case LOADING:
                binding.progressReset.setVisibility(View.VISIBLE);
                binding.buttonSendReset.setEnabled(false);
                binding.textStatus.setVisibility(View.GONE);
                break;
            case SUCCESS:
                binding.progressReset.setVisibility(View.GONE);
                binding.buttonSendReset.setEnabled(true);
                binding.textStatus.setText(R.string.msg_check_email_verification);
                binding.textStatus.setTextColor(getColor(R.color.semantic_success));
                binding.textStatus.setVisibility(View.VISIBLE);
                break;
            case ERROR:
                binding.progressReset.setVisibility(View.GONE);
                binding.buttonSendReset.setEnabled(true);
                if (state.getErrorType() == Resource.ErrorType.VALIDATION) {
                    binding.layoutEmail.setError(state.getMessage());
                } else {
                    binding.textStatus.setText(state.getMessage());
                    binding.textStatus.setTextColor(getColor(R.color.semantic_danger));
                    binding.textStatus.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
