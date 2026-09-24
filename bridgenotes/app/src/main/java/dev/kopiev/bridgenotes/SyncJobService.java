package dev.kopiev.bridgenotes;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class SyncJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                if (SyncEngine.isConfigured(this)) SyncEngine.syncBlocking(this);
            } catch (Exception ignored) {
            } finally {
                jobFinished(params, false);
            }
        }, "bridge-background-sync").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return true;
    }
}
