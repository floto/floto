package io.github.floto.util.task;

import java.util.concurrent.Callable;

class TaskRunnable<RESULT_TYPE> implements Runnable {
    private TaskInfo<RESULT_TYPE> taskInfo;
    private Callable<RESULT_TYPE> taskCallable;

    public TaskRunnable(TaskInfo<RESULT_TYPE> taskInfo, Callable<RESULT_TYPE> taskCallable) {
        this.taskInfo = taskInfo;
        this.taskCallable = taskCallable;
    }

    @Override
    public void run() {

        String oldThreadName = null;
        Thread currentThread = Thread.currentThread();
        try {
            oldThreadName = currentThread.getName();
            currentThread.setName(taskInfo.getTitle());
            Task.setCurrentTaskInfo(taskInfo);
            RESULT_TYPE result = taskCallable.call();
            taskInfo.complete(result);
        } catch (Throwable throwable) {
            taskInfo.getLogger().error("Task completed with exception", throwable);
            taskInfo.completeExceptionally(throwable);
        } finally {
            Task.setCurrentTaskInfo(null);
            if(oldThreadName != null) {
                currentThread.setName(oldThreadName);
            }
        }
    }
}
