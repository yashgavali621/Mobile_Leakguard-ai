package com.leakguard.ai.data.remote.supabase;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leakguard.ai.BuildConfig;
import com.leakguard.ai.data.local.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds a single shared Retrofit instance targeting the Supabase project.
 *
 * Logging: HttpLoggingInterceptor is set to BASIC (method/URL/status/timing)
 * even in debug builds, and NEVER to BODY/HEADERS, because request/response
 * bodies can contain access tokens, refresh tokens or (in later phases)
 * financial data. See product spec section 76 - "never log tokens or content".
 */
public final class RetrofitClient {

    private static volatile Retrofit retrofit;

    private RetrofitClient() {
    }

    public static Retrofit getInstance(Context context) {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) {
                if (retrofit == null) {
                    retrofit = build(context.getApplicationContext());
                }
            }
        }
        return retrofit;
    }

    private static Retrofit build(Context appContext) {
        String baseUrl = SupabaseConfig.getBaseUrl();
        SessionManager sessionManager = SessionManager.getInstance(appContext);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.VERBOSE_LOGGING
                ? HttpLoggingInterceptor.Level.BASIC   // method + URL + status + timing only
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(sessionManager, baseUrl))
                .addInterceptor(logging)
                .build();

        Gson gson = new GsonBuilder().setLenient().create();

        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    public static SupabaseAuthApi authApi(Context context) {
        return getInstance(context).create(SupabaseAuthApi.class);
    }

    public static SupabaseDatabaseApi databaseApi(Context context) {
        return getInstance(context).create(SupabaseDatabaseApi.class);
    }
}
