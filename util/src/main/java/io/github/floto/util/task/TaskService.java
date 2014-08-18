package io.github.floto.util.task;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.pattern.RootCauseFirstThrowableProxyConverter;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.apache.commons.io.input.Tailer;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public class TaskService {
    private Executor executor = Executors.newFixedThreadPool(8);
    private Map<String, TaskInfo<?>> activeTaskMap = new HashMap<>();
    private Map<String, TaskInfo<?>> threadTaskMap = new HashMap<>();
    private TaskPersistence taskPersistence;

    public TaskService() {
        initLogging();
        taskPersistence = new TaskPersistence();
    }

    private void initLogging() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        RootCauseFirstThrowableProxyConverter throwableConverter = new RootCauseFirstThrowableProxyConverter();
        throwableConverter.start();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
            TargetLengthBasedClassNameAbbreviator abbreviator = new TargetLengthBasedClassNameAbbreviator(14);
            @Override
            protected void append(ILoggingEvent loggingEvent) {
                TaskInfo<?> taskInfo = threadTaskMap.get(loggingEvent.getThreadName());
                if (taskInfo != null) {
                    LogEntry logEntry = new LogEntry(
                            loggingEvent.getFormattedMessage(),
                            loggingEvent.getLevel().toString(),
                            abbreviator.abbreviate(loggingEvent.getLoggerName()),
                            Instant.ofEpochMilli(loggingEvent.getTimeStamp()));
                    if (loggingEvent.getThrowableProxy() != null) {
                        logEntry.setStackTrace(throwableConverter.convert(loggingEvent));
                    }
                    taskPersistence.addLogEntry(taskInfo.getId(), logEntry);
                    taskInfo.getLogEntries().add(logEntry);
                }
            }
        };
        appender.setContext(rootLogger.getLoggerContext());
        appender.start();

        rootLogger.addAppender(appender);
    }


    public <RESULT_TYPE> TaskInfo<RESULT_TYPE> startTask(String title, Callable<RESULT_TYPE> taskCallable) {
        TaskInfo<RESULT_TYPE> taskInfo = new TaskInfo<>(taskPersistence.getNextTaskId(), title, taskCallable);
        activeTaskMap.put(taskInfo.getId(), taskInfo);
        taskPersistence.save(taskInfo);
        executor.execute(new TaskRunnable(this, taskInfo, taskCallable));
        return taskInfo;
    }

    public void registerThread(String threadName, TaskInfo<?> taskInfo) {
        threadTaskMap.put(threadName, taskInfo);
    }

    public void unregisterThread(String threadName) {
        threadTaskMap.remove(threadName);
    }

    public TaskInfo getTaskInfo(String taskId) {
        TaskInfo<?> taskInfo = activeTaskMap.get(taskId);
        if (taskInfo == null) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        return taskInfo;
    }

    public void save(TaskInfo<?> taskInfo) {
        taskPersistence.save(taskInfo);
    }

    public void writeTasks(OutputStream output) {
        taskPersistence.writeTasks(output);
    }

    public void writeLogs(String taskId, OutputStream output) {
        taskPersistence.writeLogs(taskId, output);
    }

    public void closeLogFile(String taskId) {
        taskPersistence.closeLogFile(taskId);
    }

    public InputStream getLogStream(String taskId) {
        InputStream inputStream = taskPersistence.getLogStream(taskId);
        TaskInfo<?> taskInfo = activeTaskMap.get(taskId);
        if (taskInfo != null) {
            TailingInputStream tailingInputStream = new TailingInputStream(inputStream);
            taskInfo.getCompletionStage().whenComplete((BiConsumer<Object, Throwable>) (a, b) -> {
                tailingInputStream.setFileClosed();
            });
            inputStream = tailingInputStream;
        }
        return inputStream;
    }
}
