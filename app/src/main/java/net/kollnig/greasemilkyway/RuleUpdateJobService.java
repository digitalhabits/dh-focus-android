package net.kollnig.greasemilkyway;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/** Runs the daily rule download scheduled by {@link RuleUpdates#sync}. */
public class RuleUpdateJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        Context context = getApplicationContext();
        if (!new ServiceConfig(context).isRuleUpdatesEnabled()) {
            return false;
        }
        new Thread(() -> {
            boolean updated = RuleUpdates.update(context);
            new Handler(Looper.getMainLooper()).post(() -> {
                DistractionControlService service = DistractionControlService.getInstance();
                if (updated && service != null) {
                    service.updateRules();
                }
                // A failed download waits for the next daily run rather than retrying sooner.
                jobFinished(params, false);
            });
        }, "RuleUpdates").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
