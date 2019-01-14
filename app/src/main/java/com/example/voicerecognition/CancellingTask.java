package com.example.voicerecognition;

import android.util.Log;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CancellingTask implements Cancelable {

    private final ExecutorService mExec;
    private final Cancelable mTask;
    private final long mTimeout;
    private final TimeUnit mUnit;

    public CancellingTask(ExecutorService exec, Cancelable task) {
        this(exec, task, 0, null);
    }

    public CancellingTask(ExecutorService exec, Cancelable task, long timeout,
                          TimeUnit unit) {
        mExec = exec;
        mTask = task;
        mTimeout = timeout;
        mUnit = unit;
    }

    public void cancel() {
        mTask.cancel();
    }

    public void run() {
        Future<?> future = null;
        try {
            future = mExec.submit(mTask);
            waitForCompletionOrTimeout(future);
        } catch (InterruptedException e) {
            mTask.cancel();
        } catch (ExecutionException e) {
            Log.e(Constants.TAG, "task failed", e);
            mTask.cancel();
        }
    }

    private void waitForCompletionOrTimeout(Future<?> future)
            throws InterruptedException, ExecutionException {
        if (mTimeout <= 0) {
            future.get();
            return;
        }
        try {
            future.get(mTimeout, mUnit);
        } catch (TimeoutException e) {
            mTask.cancel();
        }
    }
}
