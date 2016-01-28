package io.github.floto.server.api.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.floto.server.api.websocket.MessageHandler;
import io.github.floto.server.api.websocket.WebSocket;
import io.github.floto.util.task.TaskService;

import java.util.HashMap;
import java.util.Map;

public class SubscribeToTaskLogHandler implements MessageHandler {

    private TaskService taskService;
	private Map<String, TaskLogPusher> subscriptionMap = new HashMap<>();
	private MessageHandler unsubscriptionHandler = new MessageHandler() {
		@Override
		public void handleMessage(JsonNode message, WebSocket webSocket) {
			String streamId = message.get("streamId").asText();
			String id = webSocket.getSessionId() + "-" + streamId;
			TaskLogPusher taskLogPusher = subscriptionMap.remove(id);
			if(taskLogPusher != null) {
				taskLogPusher.stop();
			}
		}
	};

    public SubscribeToTaskLogHandler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public void handleMessage(JsonNode message, WebSocket webSocket) {
        String streamId = message.get("streamId").asText();
        String taskId = message.get("taskId").asText();
        TaskLogPusher logPusher = new TaskLogPusher(taskService.getLogStream(taskId), streamId, webSocket);
		subscriptionMap.put(webSocket.getSessionId() + "-" + streamId, logPusher);
        logPusher.start();

    }

	public MessageHandler getUnsubscriptionHandler() {
		return unsubscriptionHandler;
	}

}
