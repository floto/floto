package io.github.floto.server.api;

import io.github.floto.util.task.LogEntry;
import io.github.floto.util.task.TaskService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TaskResource {
    private TaskService taskService;
    private String taskId;

    public TaskResource(TaskService taskService, String taskId) {
        this.taskService = taskService;
        this.taskId = taskId;
    }

    @GET
    @Path("logs")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getLogs() {
        Map<String, Object> result = new HashMap<>();
        ArrayList<Object> logs = new ArrayList<>();
        for(LogEntry logEntry: taskService.getLogEntries(taskId)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("message", logEntry.getMessage());
            entry.put("level", logEntry.getLevel());
            logs.add(entry);
        }

        result.put("logs", logs);
        return result;
    }


}
