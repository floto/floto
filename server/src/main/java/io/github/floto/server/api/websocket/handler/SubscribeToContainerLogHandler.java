package io.github.floto.server.api.websocket.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.floto.core.FlotoService;
import io.github.floto.server.api.websocket.MessageHandler;
import io.github.floto.server.api.websocket.WebSocket;

import java.util.HashMap;
import java.util.Map;

public class SubscribeToContainerLogHandler implements MessageHandler {

    private FlotoService flotoService;
    private Map<String, ContainerLogPusher> subscriptionMap = new HashMap<>();
    private MessageHandler unsubscriptionHandler = new MessageHandler() {
        @Override
        public void handleMessage(JsonNode message, WebSocket webSocket) {
            String streamId = message.get("streamId").asText();
            ContainerLogPusher containerLogPusher = subscriptionMap.get(webSocket.getSessionId() + "-" + streamId);
            if(containerLogPusher != null) {
                containerLogPusher.stop();
            }
        }
    };

    public SubscribeToContainerLogHandler(FlotoService flotoService) {
        this.flotoService = flotoService;
    }

    @Override
    public void handleMessage(JsonNode message, WebSocket webSocket) {
        String streamId = message.get("streamId").asText();
        String containerName = message.get("containerName").asText();
        ContainerLogPusher logPusher = new ContainerLogPusher(flotoService.getContainerLogStream(containerName), streamId, webSocket);
        logPusher.start();
        subscriptionMap.put(webSocket.getSessionId()+"-"+streamId, logPusher);
    }

    public MessageHandler getUnsubscriptionHandler() {
        return unsubscriptionHandler;
    }
}
