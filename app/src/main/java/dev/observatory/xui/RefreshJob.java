package dev.observatory.xui;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RefreshJob extends JobService {
    private static final int ID=32511;
    private static final int MANUAL_ID=32512;
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(JobParameters params) {
        pool.execute(()->{try{Syncer.refreshAll(getApplicationContext());}finally{jobFinished(params,false);}});
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
    @Override public void onDestroy(){pool.shutdownNow();super.onDestroy();}
    /** Schedule a durable one-off refresh; widget broadcasts must finish quickly. */
    public static void requestNow(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        JobInfo job=new JobInfo.Builder(MANUAL_ID,new ComponentName(context,RefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setOverrideDeadline(0L).build();
        scheduler.schedule(job);
    }
    public static void schedule(Context context) {
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(ID);
        if(Store.panels(context).isEmpty())return;
        long period=Math.max(15,Store.interval(context))*60_000L;
        JobInfo info=new JobInfo.Builder(ID,new ComponentName(context,RefreshJob.class))
          .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
          .setPeriodic(period,Math.min(5*60_000L,period/3))
          .setPersisted(true).build();
        scheduler.schedule(info);
    }
}
