package io.github.floto.util.task;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.pattern.RootCauseFirstThrowableProxyConverter;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.input.Tailer;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;

public class TaskService {
    private Executor executor = Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setDaemon(true).build());
    private Map<String, TaskInfo<?>> activeTaskMap = new HashMap<>();
    private Map<String, TaskInfo<?>> threadTaskMap = new HashMap<>();
    private TaskPersistence taskPersistence;

    private List<BiConsumer<TaskInfo, Throwable>> taskCompletionListeners = new ArrayList<>();

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
					if(loggingEvent.getLevel().isGreaterOrEqual(Level.WARN)) {
						taskInfo.incrementNumberOfWarnings();
					}
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
        taskInfo.getCompletionStage().whenComplete(new BiConsumer<RESULT_TYPE, Throwable>() {
            @Override
            public void accept(RESULT_TYPE result_type, Throwable throwable) {
                for(BiConsumer<TaskInfo, Throwable> taskCompletionListener: taskCompletionListeners) {
                    taskCompletionListener.accept(taskInfo, throwable);
                }
            }
        });
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

	public File getLogFile(String taskId) {
		return taskPersistence.getLogFile(taskId);
	}

	public File getTaskInfoFile(String taskId) {
		return taskPersistence.getTaskInfoFile(taskId);
	}

	public List<Integer> getTaskNumbers() {
		return taskPersistence.getTaskNumbers();
	}

	public void addTaskCompletionListener(BiConsumer<TaskInfo, Throwable> taskCompletionListener) {
        taskCompletionListeners.add(taskCompletionListener);
    }
}
