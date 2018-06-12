package com.google.ar.core.examples.java.common.messaging;

import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.ar.core.examples.java.cloudanchor.HuntTreasureActivity;

public class MyJobService extends JobService {

    private static final String TAG = HuntTreasureActivity.class.getSimpleName() + "."
            + MyJobService.class.getSimpleName();

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.d(TAG, "Performing long running task in scheduled job");
        // TODO(developer): add long running task here.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

}