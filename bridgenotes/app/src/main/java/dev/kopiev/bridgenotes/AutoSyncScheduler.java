package dev.kopiev.bridgenotes;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class AutoSyncScheduler {
    private static final int JOB_ID = 8787;
    private AutoSyncScheduler() { }

    public static void ensureScheduled(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;

        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == JOB_ID) return;
        }

        ComponentName service = new ComponentName(context, SyncJobService.class);
        JobInfo job = new JobInfo.Builder(JOB_ID, service)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }
}
