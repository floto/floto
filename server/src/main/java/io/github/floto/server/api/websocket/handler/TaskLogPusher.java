package io.github.floto.server.api.websocket.handler;

import io.github.floto.server.api.websocket.WebSocket;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;

public class TaskLogPusher {
    private final Logger log = LoggerFactory.getLogger(TaskLogPusher.class);
    private final InputStream inputStream;
    private final String streamId;
    private WebSocket webSocket;

    public TaskLogPusher(InputStream inputStream, String streamId, WebSocket webSocket) {
        this.inputStream = inputStream;
        this.streamId = streamId;
        this.webSocket = webSocket;
    }

    public void start() {
        new Thread(() -> {
            try(InputStream input = inputStream) {
                DataInputStream dataInput = new DataInputStream(input);
                byte[] buffer = new byte[4*1024];
                try {
                    while (true) {
                        IOUtils.skip(input, 1);
                        String line = dataInput.readLine();
                        if(line == null || line.isEmpty()) {
                            break;
                        }
                        int length = Integer.parseInt(line);
                        if(length > buffer.length) {
                            buffer = new byte[length];
                        }
                        IOUtils.readFully(input, buffer, 0, length);
                        StringBuilder sb = new StringBuilder();
                        sb.append("{\n");
                        sb.append("\"streamId\": \"").append(streamId).append("\",\n");
                        sb.append("\"type\": \"taskLogEntry\",\n");
                        sb.append("\"entry\": ");
                        sb.append(new String(buffer, 0, length));
                        sb.append("}");
                        webSocket.sendTextMessage(sb.toString());
                    }
                } catch(EOFException ignored) {
                    // EOF reached, terminate
                }
                log.trace("Log Pusher terminated");
            } catch(Throwable throwable) {
                log.error("Error pushing logs", throwable);
            }
        }, "LogPusher "+streamId).start();
    }
}
