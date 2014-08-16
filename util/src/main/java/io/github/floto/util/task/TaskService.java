package io.github.floto.util.task;

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

    public <RESULT_TYPE> TaskInfo<RESULT_TYPE> startTask(String title, Callable<RESULT_TYPE> taskCallable) {
        TaskInfo<RESULT_TYPE> taskInfo = new TaskInfo<>(title, taskCallable);
        taskList.add(taskInfo);
        taskMap.put(taskInfo.getId(), taskInfo);
        executor.execute(new TaskRunnable(taskInfo, taskCallable));
        return taskInfo;
    }

    public List<TaskInfo<?>> getTasks() {
        return taskList;
    }

    public List<LogEntry> getLogEntries(String taskId) {
        return taskMap.get(taskId).getLogEntries();
    }
}
