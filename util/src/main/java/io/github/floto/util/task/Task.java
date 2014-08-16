package io.github.floto.util.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Task {
    private static Logger log = LoggerFactory.getLogger(Task.class);

    private Task() {

    }

    private static ThreadLocal<TaskInfo<?>> currentTaskInfo = ThreadLocal.withInitial(() -> {
        log.warn("Requesting task info object, but none is set");
        return null;
    });

    protected static void setCurrentTaskInfo(TaskInfo<?> taskInfo) {
        currentTaskInfo.set(taskInfo);
    }

    public static TaskInfo<?> getCurrentTaskInfo() {
        return currentTaskInfo.get();
    }
}
