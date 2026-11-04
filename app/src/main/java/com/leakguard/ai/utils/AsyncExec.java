package com.leakguard.ai.utils;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs blocking repository work off the main thread; delivers RepositoryCallback on the main thread. */
public final class AsyncExec {

    public interface Task<T> {
        T run() throws Exception;
    }

    public static class Failure extends Exception {
        public final Resource.ErrorType type;

        public Failure(String message, Resource.ErrorType type) {
            super(message);
            this.type = type;
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AsyncExec() {
    }

    public static <T> void run(Task<T> task, RepositoryCallback<T> callback) {
        IO.execute(() -> {
            try {
                T result = task.run();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Failure f) {
                MAIN.post(() -> callback.onError(f.getMessage(), f.type));
            } catch (IOException e) {
                MAIN.post(() -> callback.onError("Couldn't reach the server. Check your connection and try again.",
                        Resource.ErrorType.NETWORK));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("Something went wrong. Please try again.", Resource.ErrorType.UNKNOWN));
            }
        });
    }
}
