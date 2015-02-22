package io.github.floto.server.api.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.floto.core.FlotoService;
import io.github.floto.server.api.websocket.MessageHandler;
import io.github.floto.server.api.websocket.WebSocket;

public class SubscribeToContainerLogHandler implements MessageHandler {

    private FlotoService flotoService;

    public SubscribeToContainerLogHandler(FlotoService flotoService) {
        this.flotoService = flotoService;
    }

    @Override
    public void handleMessage(JsonNode message, WebSocket webSocket) {
        String streamId = message.get("streamId").asText();
        String containerName = message.get("containerName").asText();
        ContainerLogPusher logPusher = new ContainerLogPusher(flotoService.getContainerLogStream(containerName), streamId, (messageString) -> {
            webSocket.sendTextMessage(messageString);
        });
        logPusher.start();

    }
}
