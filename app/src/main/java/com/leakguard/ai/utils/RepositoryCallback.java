package com.leakguard.ai.utils;

/**
 * Simple async result callback used by Repository classes. Retrofit already
 * delivers Callback#onResponse/onFailure on the main thread on Android, so
 * ViewModels can safely post straight to LiveData from here without extra
 * thread-hopping.
 */
public interface RepositoryCallback<T> {
    void onSuccess(T data);

    void onError(String message, Resource.ErrorType errorType);
}
