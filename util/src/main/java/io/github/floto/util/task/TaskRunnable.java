package io.github.floto.util.task;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Callable;

class TaskRunnable<RESULT_TYPE> implements Runnable {
    private Logger log = LoggerFactory.getLogger(TaskRunnable.class);

    private TaskService taskService;
    private TaskInfo<RESULT_TYPE> taskInfo;
    private Callable<RESULT_TYPE> taskCallable;

    public TaskRunnable(TaskService taskService, TaskInfo<RESULT_TYPE> taskInfo, Callable<RESULT_TYPE> taskCallable) {
        this.taskService = taskService;
        this.taskInfo = taskInfo;
        this.taskCallable = taskCallable;
    }

    @Override
    public void run() {
        String oldThreadName = null;
        Thread currentThread = Thread.currentThread();
        String threadName = taskInfo.getThreadName();
        try {
            oldThreadName = currentThread.getName();
            taskService.registerThread(threadName, taskInfo);
            currentThread.setName(threadName);
            Task.setCurrentTaskInfo(taskInfo);
            taskInfo.setStartDate(Instant.now());
            log.info("Task started: {}", taskInfo.getTitle());
            RESULT_TYPE result = taskCallable.call();
            taskInfo.complete(result);
            log.info("Task completed: {}", taskInfo.getTitle());
        } catch (Throwable throwable) {
            log.error("Task completed with exception: {}", ExceptionUtils.getMessage(throwable), throwable);
            taskInfo.completeExceptionally(throwable);
        } finally {
            Task.setCurrentTaskInfo(null);
            if(oldThreadName != null) {
                currentThread.setName(oldThreadName);
            }
            taskInfo.setEndDate(Instant.now());
            taskService.unregisterThread(threadName);
        }
    }
}
