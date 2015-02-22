package io.github.floto.server.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;

public class WebSocketBroadcaster {
    private final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private ServerContainer websocketContainer;

    private ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketBroadcaster(ServerContainer websocketContainer) {
        this.websocketContainer = websocketContainer;
    }

    public void sendMessage(Object message) {
        for(Session session: websocketContainer.getOpenSessions()) {
            try {
                session.getAsyncRemote().sendText(objectMapper.writeValueAsString(message));
            } catch (Throwable throwable) {
                log.error("Unable to send message {}", message, throwable);
            }
        }

    }
}
