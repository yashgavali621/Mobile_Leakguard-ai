package com.leakguard.ai;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.util.Log;

import com.leakguard.ai.data.local.IntegrationPrefs;
import com.leakguard.ai.data.local.SessionManager;
import com.leakguard.ai.data.remote.supabase.SupabaseConfig;
import com.leakguard.ai.service.IntegrationSyncWorker;

public class LeakGuardApplication extends Application {

    // Channel IDs are referenced from here so later phases (WorkManager jobs,
    // AlertEngine) can post to a channel that's guaranteed to already exist.
    public static final String CHANNEL_IMPORTANT_ALERTS = "channel_important_alerts";
    public static final String CHANNEL_RENEWALS = "channel_renewals";
    public static final String CHANNEL_INSIGHTS = "channel_insights";
    public static final String CHANNEL_BUDGET = "channel_budget";

    @Override
    public void onCreate() {
        super.onCreate();
        logConfigOnDebugBuilds();
        createNotificationChannels();
        restoreIntegrationSync();
    }

    /** Re-arms the 6-hourly Email/SMS sync after app/device restarts (WorkManager dedupes). */
    private void restoreIntegrationSync() {
        try {
            if (!SessionManager.getInstance(this).hasSession()) {
                return;
            }
            IntegrationPrefs prefs = new IntegrationPrefs(this);
            if (prefs.isSmsEnabled() || prefs.isGmailConnected()) {
                IntegrationSyncWorker.schedule(this);
            }
        } catch (RuntimeException e) {
            Log.w("LeakGuardSync", "Could not restore integration sync", e);
        }
    }

    /**
     * Debug builds only. Prints the resolved Supabase origin and a masked
     * fragment of the key so a wrong/truncated/quoted value is obvious in
     * Logcat (filter: LeakGuardConfig). The key itself is never logged.
     */
    private void logConfigOnDebugBuilds() {
        if (!BuildConfig.VERBOSE_LOGGING) {
            return;
        }
        Log.i("LeakGuardConfig", SupabaseConfig.describeForDiagnostics());
        if (!SupabaseConfig.isConfigured()) {
            Log.w("LeakGuardConfig", SupabaseConfig.NOT_CONFIGURED_MESSAGE);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel important = new NotificationChannel(
                CHANNEL_IMPORTANT_ALERTS, "Important Alerts", NotificationManager.IMPORTANCE_HIGH);
        important.setDescription("Unusual transactions, failed payments and other urgent findings.");

        NotificationChannel renewals = new NotificationChannel(
                CHANNEL_RENEWALS, "Subscription Renewals", NotificationManager.IMPORTANCE_DEFAULT);
        renewals.setDescription("Upcoming subscription renewals and price changes.");

        NotificationChannel insights = new NotificationChannel(
                CHANNEL_INSIGHTS, "Financial Insights", NotificationManager.IMPORTANCE_LOW);
        insights.setDescription("New AI-generated insights about your spending.");

        NotificationChannel budget = new NotificationChannel(
                CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_DEFAULT);
        budget.setDescription("Alerts when a budget category crosses a threshold you've set.");

        manager.createNotificationChannel(important);
        manager.createNotificationChannel(renewals);
        manager.createNotificationChannel(insights);
        manager.createNotificationChannel(budget);
    }
}
