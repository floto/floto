package io.github.floto.util.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskService {
    private Executor executor = Executors.newFixedThreadPool(8);
    private List<TaskInfo<?>> taskList = new ArrayList<>();
    private Map<String, TaskInfo<?>> taskMap = new HashMap<>();
    private Map<String, TaskInfo<?>> threadTaskMap = new HashMap<>();

    public TaskService() {
        initLogging();
    }

    private void initLogging() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent loggingEvent) {
                TaskInfo<?> taskInfo = threadTaskMap.get(loggingEvent.getThreadName());
                if (taskInfo != null) {
                    taskInfo.getLogEntries().add(new LogEntry(loggingEvent.getFormattedMessage(), loggingEvent.getLevel().toString().toLowerCase()));
                }
            }
        };
        appender.setContext(rootLogger.getLoggerContext());
        appender.start();

        rootLogger.addAppender(appender);
    }


    public <RESULT_TYPE> TaskInfo<RESULT_TYPE> startTask(String title, Callable<RESULT_TYPE> taskCallable) {
        TaskInfo<RESULT_TYPE> taskInfo = new TaskInfo<>(title, taskCallable);
        taskList.add(taskInfo);
        taskMap.put(taskInfo.getId(), taskInfo);
        executor.execute(new TaskRunnable(this, taskInfo, taskCallable));
        return taskInfo;
    }

    public List<TaskInfo<?>> getTasks() {
        return taskList;
    }

    public List<LogEntry> getLogEntries(String taskId) {
        return taskMap.get(taskId).getLogEntries();
    }

    public void registerThread(String threadName, TaskInfo<?> taskInfo) {
        threadTaskMap.put(threadName, taskInfo);
    }

    public void unregisterThread(String threadName) {
        threadTaskMap.remove(threadName);
    }

    public TaskInfo getTaskInfo(String taskId) {
        TaskInfo<?> taskInfo = taskMap.get(taskId);
        if (taskInfo == null) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        return taskInfo;
    }
}
