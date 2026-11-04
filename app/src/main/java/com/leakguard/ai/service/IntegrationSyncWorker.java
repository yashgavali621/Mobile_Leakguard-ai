package com.leakguard.ai.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.leakguard.ai.data.local.IntegrationPrefs;
import com.leakguard.ai.data.repository.GmailRepository;
import com.leakguard.ai.data.repository.SmsRepository;
import com.leakguard.ai.data.repository.SyncSession;

import java.util.concurrent.TimeUnit;

/** Background sync for SMS (inbox import) and Gmail (Edge Function). Safe to run repeatedly - all inserts dedupe. */
public class IntegrationSyncWorker extends Worker {

    private static final String PERIODIC = "leakguard_integration_sync";
    private static final String ONCE = "leakguard_integration_sync_now";
    private static final long OVERLAP_MS = 10 * 60 * 1000L;

    public IntegrationSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private static Constraints net() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Every 6 hours while online. Idempotent. */
    public static void schedule(Context context) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(IntegrationSyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(net()).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    /** One sync soon (used by SmsReceiver and for "Sync now"). */
    public static void runNow(Context context, long delaySeconds) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(IntegrationSyncWorker.class)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS).setConstraints(net()).build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(ONCE, ExistingWorkPolicy.APPEND_OR_REPLACE, req);
    }

    /** Call on logout. */
    public static void cancel(Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.cancelUniqueWork(PERIODIC);
        wm.cancelUniqueWork(ONCE);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        if (!SyncSession.ensure(ctx)) return Result.success(); // logged out / not configured: nothing to do

        IntegrationPrefs prefs = new IntegrationPrefs(ctx);
        boolean failed = false;

        if (prefs.isSmsEnabled() && SmsRepository.hasSmsPermission(ctx)) {
            long started = System.currentTimeMillis();
            long last = prefs.getLastSmsSync();
            long since = last > 0 ? last - OVERLAP_MS : started - 90L * 24 * 60 * 60 * 1000;
            if (new SmsRepository(ctx).importInbox(since) >= 0) prefs.setLastSmsSync(started);
            else failed = true;
        }

        if (prefs.isGmailConnected() && new GmailRepository(ctx).syncBlocking() == null) {
            failed = true;
        }

        return (failed && getRunAttemptCount() < 3) ? Result.retry() : Result.success();
    }
}
