package io.github.floto.server.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ClientEndpoint
@ServerEndpoint(value = "/events/")
public class EventSocket {
    private Session sess;

    @OnOpen
    public void onWebSocketConnect(Session sess) {
        this.sess = sess;
        System.out.println("this: " + this);
        System.out.println("Socket Connected: " + sess);
    }

    @OnMessage
    public void onWebSocketText(String message) {
        System.out.println("Received TEXT message: " + message);
        sess.getAsyncRemote().sendText("Foobar");
    }

    @OnClose
    public void onWebSocketClose(CloseReason reason) {
        System.out.println("Socket Closed: " + reason);
    }

    @OnError
    public void onWebSocketError(Throwable cause) {
        cause.printStackTrace(System.err);
    }
}