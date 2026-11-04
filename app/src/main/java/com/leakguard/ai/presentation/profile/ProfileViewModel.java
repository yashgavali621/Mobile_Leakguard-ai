package com.leakguard.ai.presentation.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.repository.AuthRepository;
import com.leakguard.ai.data.repository.ProfileRepository;
import com.leakguard.ai.domain.model.Profile;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

public class ProfileViewModel extends AndroidViewModel {

    private final ProfileRepository profileRepository;
    private final AuthRepository authRepository;
    private final SessionManager sessionManager;

    private final MutableLiveData<Resource<Profile>> profileState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> logoutState = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        profileRepository = new ProfileRepository(application);
        authRepository = new AuthRepository(application);
        sessionManager = SessionManager.getInstance(application);
    }

    public LiveData<Resource<Profile>> getProfileState() {
        return profileState;
    }

    public LiveData<Resource<Void>> getLogoutState() {
        return logoutState;
    }

    public void loadProfile() {
        String userId = sessionManager.getUserId();
        if (userId == null) {
            profileState.setValue(Resource.error("Not logged in", Resource.ErrorType.UNAUTHORIZED));
            return;
        }
        profileState.setValue(Resource.loading());
        profileRepository.getProfile(userId, new RepositoryCallback<Profile>() {
            @Override
            public void onSuccess(Profile data) {
                profileState.setValue(Resource.success(data));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                profileState.setValue(Resource.error(message, errorType));
            }
        });
    }

    public void logout() {
        logoutState.setValue(Resource.loading());
        authRepository.signOut(new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                logoutState.setValue(Resource.success(null));
            }

            @Override
            public void onError(String message, Resource.ErrorType errorType) {
                // AuthRepository.signOut always clears the local session even on
                // server failure, so from the app's perspective this is still a
                // successful logout.
                logoutState.setValue(Resource.success(null));
            }
        });
    }
}
