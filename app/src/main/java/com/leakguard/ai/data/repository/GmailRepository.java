package com.leakguard.ai.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.leakguard.ai.data.local.IntegrationPrefs;
import com.leakguard.ai.data.remote.dto.IntegrationDtos;
import com.leakguard.ai.data.remote.supabase.ApiErrorParser;
import com.leakguard.ai.data.remote.supabase.GmailSyncApi;
import com.leakguard.ai.data.remote.supabase.RetrofitClient;
import com.leakguard.ai.service.IntegrationSyncWorker;
import com.leakguard.ai.utils.AsyncExec;
import com.leakguard.ai.utils.RepositoryCallback;
import com.leakguard.ai.utils.Resource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Gmail integration. All Google token handling (code exchange, encrypted
 * refresh token, Gmail API calls) happens in the gmail-sync Edge Function;
 * the app only forwards the one-time serverAuthCode and triggers syncs.
 */
public class GmailRepository {

    private final Context ctx;
    private final GmailSyncApi api;
    private final IntegrationPrefs prefs;

    public GmailRepository(Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = new IntegrationPrefs(ctx);
        // Same client as the rest of the app, but a longer read timeout: the first sync scans up to 90 days of mail.
        Retrofit base = RetrofitClient.getInstance(ctx);
        OkHttpClient longClient = ((OkHttpClient) base.callFactory()).newBuilder()
                .readTimeout(90, TimeUnit.SECONDS).build();
        this.api = base.newBuilder().client(longClient).build().create(GmailSyncApi.class);
    }

    /** Pass the serverAuthCode from GmailAuthHelper.extractServerAuthCode(). */
    public void connect(@NonNull String serverAuthCode, @NonNull RepositoryCallback<IntegrationDtos.GmailResponse> cb) {
        AsyncExec.run(() -> {
            IntegrationDtos.GmailResponse r = call("connect", serverAuthCode);
            prefs.setGmailConnected(true);
            IntegrationSyncWorker.schedule(ctx);
            return r;
        }, cb);
    }

    public void syncNow(@NonNull RepositoryCallback<IntegrationDtos.GmailResponse> cb) {
        AsyncExec.run(() -> call("sync", null), cb);
    }

    public void disconnect(@NonNull RepositoryCallback<Void> cb) {
        AsyncExec.run(() -> {
            call("disconnect", null);
            prefs.setGmailConnected(false);
            return null;
        }, cb);
    }

    /** Blocking, for WorkManager. @return null on any failure. */
    public IntegrationDtos.GmailResponse syncBlocking() {
        try {
            return call("sync", null);
        } catch (Exception e) {
            return null;
        }
    }

    private IntegrationDtos.GmailResponse call(String action, String code) throws Exception {
        if (!SyncSession.ensure(ctx)) {
            throw new AsyncExec.Failure("Please log in again.", Resource.ErrorType.UNAUTHORIZED);
        }
        Response<IntegrationDtos.GmailResponse> resp = api.call(new IntegrationDtos.GmailRequest(action, code)).execute();
        if (resp.isSuccessful() && resp.body() != null) return resp.body();

        String msg = null;
        try {
            if (resp.errorBody() != null) {
                IntegrationDtos.GmailResponse err = new Gson().fromJson(resp.errorBody().string(), IntegrationDtos.GmailResponse.class);
                if (err != null) msg = err.error;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through to generic message
        }
        if (msg == null) msg = ApiErrorParser.genericMessageForCode(resp.code());
        if (resp.code() == 401 || resp.code() == 409) prefs.setGmailConnected(false); // revoked / not connected
        throw new AsyncExec.Failure(msg, resp.code() == 401 ? Resource.ErrorType.UNAUTHORIZED : Resource.ErrorType.SERVER);
    }
}
