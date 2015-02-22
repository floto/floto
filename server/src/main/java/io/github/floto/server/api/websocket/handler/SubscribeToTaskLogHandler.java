package io.github.floto.server.api.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.floto.server.api.websocket.MessageHandler;
import io.github.floto.server.api.websocket.WebSocket;
import io.github.floto.util.task.TaskService;

public class SubscribeToTaskLogHandler implements MessageHandler {

    private TaskService taskService;

    public SubscribeToTaskLogHandler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void handleMessage(JsonNode message, WebSocket webSocket) {
        String streamId = message.get("streamId").asText();
        String taskId = message.get("taskId").asText();
        TaskLogPusher logPusher = new TaskLogPusher(taskService.getLogStream(taskId), streamId, (messageString) -> {
            webSocket.sendTextMessage(messageString);
        });
        logPusher.start();

    }
}
