package io.github.floto.server.websocket;

import io.github.floto.util.task.TaskService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.function.Consumer;

public class LogPusher {
    private final Logger log = LoggerFactory.getLogger(LogPusher.class);
    private final InputStream inputStream;
    private final String streamId;
    private Consumer<String> callback;

    public LogPusher(InputStream inputStream, String streamId, Consumer<String> callback) {
        this.inputStream = inputStream;
        this.streamId = streamId;
        this.callback = callback;
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
                        sb.append("\"type\": \"logEntry\",\n");
                        sb.append("\"entry\": ");
                        sb.append(new String(buffer, 0, length));
                        sb.append("}");
                        callback.accept(sb.toString());
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
