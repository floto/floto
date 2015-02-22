package io.github.floto.server.api.websocket;

import com.fasterxml.jackson.databind.JsonNode;

public interface MessageHandler {
    public void handleMessage(JsonNode message, WebSocket webSocket);
}
