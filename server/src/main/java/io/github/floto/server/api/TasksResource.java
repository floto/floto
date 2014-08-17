package io.github.floto.server.api;

import com.google.common.collect.Lists;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
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
    public StreamingOutput getState() {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                taskService.writeTasks(output);
            }
        };
    }


    @Path("{taskId}")
    public TaskResource getTask(@PathParam("taskId") String taskId) {
        return new TaskResource(taskService, taskId);
    }


}
