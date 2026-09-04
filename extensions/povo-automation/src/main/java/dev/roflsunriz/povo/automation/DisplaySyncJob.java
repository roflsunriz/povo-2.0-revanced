package dev.roflsunriz.povo.automation;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public final class DisplaySyncJob extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> work;
    private final AtomicLong generation = new AtomicLong();

    @Override public boolean onStartJob(JobParameters parameters) {
        long current = generation.incrementAndGet();
        work = executor.submit(() -> {
            boolean success = DisplaySync.publish(this);
            if (generation.get() == current && !Thread.currentThread().isInterrupted()) {
                jobFinished(parameters, !success);
            }
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        generation.incrementAndGet();
        if (work != null) work.cancel(true);
        return true;
    }

    @Override public void onDestroy() {
        generation.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }
}
