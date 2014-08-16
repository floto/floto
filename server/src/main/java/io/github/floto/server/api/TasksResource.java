package io.github.floto.server.api;

import com.google.common.collect.Lists;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("tasks")
public class TasksResource {
    private TaskService taskService;

    public TasksResource(TaskService taskService) {
        this.taskService = taskService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getState() {
        Map<String, Object> result = new HashMap<>();
        ArrayList<Object> tasks = new ArrayList<>();
        for(TaskInfo taskInfo: Lists.reverse(taskService.getTasks())) {
            Map<String, Object> task = new HashMap<>();
            task.put("id", taskInfo.getId());
            task.put("title", taskInfo.getTitle());
            CompletableFuture resultFuture = (CompletableFuture) taskInfo.getResultFuture();
            String status = "running";
            if(resultFuture.isDone()) {
                if(resultFuture.isCancelled()) {
                    status = "cancelled";
                } else if(resultFuture.isCompletedExceptionally()) {
                    status = "error";
                } else {
                    status = "success";
                }
            }
            task.put("status", status);
            task.put("creationDate", taskInfo.getCreationDate());
            task.put("startDate", taskInfo.getStartDate());
            task.put("endDate", taskInfo.getEndDate());
            if(taskInfo.getDuration() != null) {
                task.put("durationInMs", taskInfo.getDuration().toMillis());
            }

            tasks.add(task);
        }

        result.put("tasks", tasks);
        return result;
    }


}
