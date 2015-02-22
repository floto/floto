package io.github.floto.server.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

@ClientEndpoint
@ServerEndpoint(value = "/api/_websocket")
public class WebSocket {
    private final Logger log = LoggerFactory.getLogger(WebSocket.class);

    private Session session;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, MessageHandler> messageHandlers = new HashMap<>();
    private String sessionId;

    @SuppressWarnings("UnusedDeclaration")
    @OnOpen
    public void onWebSocketConnect(Session sess) {
        this.session = sess;
        this.sessionId = sess.getId();
    }

    public void addMessageHandler(String messageType, MessageHandler messageHandler) {
        messageHandlers.put(messageType, messageHandler);
    }

    @SuppressWarnings("UnusedDeclaration")
    @OnMessage
    public void onWebSocketText(String messageString) {
        try {
            JsonNode message = objectMapper.reader().readTree(messageString);
            String messageType = message.findPath("type").asText();
            if(messageType == null) {
                throw new IllegalArgumentException("No message type given");
            }
            MessageHandler messageHandler = messageHandlers.get(messageType);
            if(messageHandler != null) {
                messageHandler.handleMessage(message, this);
            } else {
                log.error("Unknown message type '{}'", messageType);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private void sendMessage(Object message) throws IOException {
        sendTextMessage(objectMapper.writeValueAsString(message));
    }

    public void sendTextMessage(String textMessage) throws IOException{
        if(!session.isOpen()) {
            throw new EOFException("WebSocket closed");
        }
        try {
            session.getAsyncRemote().sendText(textMessage);
        } catch (Throwable throwable) {
            log.error("Unable to send message {}", textMessage, throwable);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @OnClose
    public void onWebSocketClose(CloseReason reason) {
        CloseReason.CloseCode closeCode = reason.getCloseCode();
        if(closeCode.equals(CloseReason.CloseCodes.NORMAL_CLOSURE) || closeCode.equals(CloseReason.CloseCodes.GOING_AWAY)) {
            return;
        }
        log.warn("WebSocket closed: {} - {}", reason.getCloseCode(), reason.getReasonPhrase());
    }

    @SuppressWarnings("UnusedDeclaration")
    @OnError
    public void onWebSocketError(Throwable cause) {
        if(cause instanceof SocketTimeoutException) {
            // ignored
            return;
        }
        log.error("WebSocket error", cause);
    }

    public String getSessionId() {
        return sessionId;
    }
}