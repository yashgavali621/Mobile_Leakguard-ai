package com.leakguard.ai.presentation.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.leakguard.ai.data.repository.AuthRepository;
import com.leakguard.ai.domain.model.User;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;
import com.leakguard.ai.utils.ValidationUtils;

/**
 * Backs LoginActivity, RegisterActivity and ForgotPasswordActivity. Kept as
 * a single ViewModel since the three screens share one AuthRepository and
 * the same "loading / success / error" shape - splitting them would just
 * duplicate boilerplate for no benefit at this size.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<Resource<User>> loginState = new MutableLiveData<>();
    private final MutableLiveData<Resource<AuthRepository.SignUpResult>> registerState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> passwordResetState = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
    }

    public LiveData<Resource<User>> getLoginState() {
        return loginState;
    }

    public LiveData<Resource<AuthRepository.SignUpResult>> getRegisterState() {
        return registerState;
    }

    public LiveData<Resource<Void>> getPasswordResetState() {
        return passwordResetState;
    }

    public void login(String email, String password) {
        if (!ValidationUtils.isValidEmail(email)) {
            loginState.setValue(Resource.error("Enter a valid email address", Resource.ErrorType.VALIDATION));
            return;
        }
        if (!ValidationUtils.isNonEmpty(password)) {
            loginState.setValue(Resource.error("Enter your password", Resource.ErrorType.VALIDATION));
            return;
        }

        loginState.setValue(Resource.loading());
        authRepository.signIn(email.trim(), password, new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User data) {
                loginState.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                loginState.setValue(Resource.error(message, errorType));
            }
        });
    }

    public void register(String fullName, String email, String password, String confirmPassword) {
        if (!ValidationUtils.isNonEmpty(fullName)) {
            registerState.setValue(Resource.error("Enter your full name", Resource.ErrorType.VALIDATION));
            return;
        }
        if (!ValidationUtils.isValidEmail(email)) {
            registerState.setValue(Resource.error("Enter a valid email address", Resource.ErrorType.VALIDATION));
            return;
        }
        if (!ValidationUtils.isValidPassword(password)) {
            registerState.setValue(Resource.error("Password must be at least 8 characters", Resource.ErrorType.VALIDATION));
            return;
        }
        if (!ValidationUtils.passwordsMatch(password, confirmPassword)) {
            registerState.setValue(Resource.error("Passwords don't match", Resource.ErrorType.VALIDATION));
            return;
        }

        registerState.setValue(Resource.loading());
        authRepository.signUp(email.trim(), password, fullName.trim(),
                new RepositoryCallback<AuthRepository.SignUpResult>() {
                    @Override
                    public void onSuccess(AuthRepository.SignUpResult data) {
                        registerState.setValue(Resource.success(data));
                    }

                    @Override
                    public void onError(String message, Resource.ErrorType errorType) {
                        registerState.setValue(Resource.error(message, errorType));
                    }
                });
    }

    public void sendPasswordReset(String email) {
        if (!ValidationUtils.isValidEmail(email)) {
            passwordResetState.setValue(Resource.error("Enter a valid email address", Resource.ErrorType.VALIDATION));
            return;
        }
        passwordResetState.setValue(Resource.loading());
        authRepository.sendPasswordReset(email.trim(), new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                passwordResetState.setValue(Resource.success(null));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                passwordResetState.setValue(Resource.error(message, errorType));
            }
        });
    }
}
