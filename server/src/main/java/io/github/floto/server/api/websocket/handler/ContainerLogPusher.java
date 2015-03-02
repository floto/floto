package io.github.floto.server.api.websocket.handler;

import com.google.common.base.Throwables;
import io.github.floto.server.api.websocket.WebSocket;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class ContainerLogPusher {
    private final Logger log = LoggerFactory.getLogger(ContainerLogPusher.class);
    private final InputStream inputStream;
    private final String streamId;
    private StringBuilder sb;
    private WebSocket webSocket;
    private volatile boolean running = true;

    public ContainerLogPusher(InputStream inputStream, String streamId, WebSocket webSocket) {
        this.inputStream = inputStream;
        this.streamId = streamId;
        this.webSocket = webSocket;
    }

    public void start() {
        new Thread(() -> {
            try(InputStream input = inputStream) {
                initStringBuilder();
                byte[] buffer = new byte[4*1024];
                DataInputStream dataInputStream = new DataInputStream(inputStream);
                while (running) {
                    if(dataInputStream.available() < 8) {
                        flush();
                    }
                    int flags = dataInputStream.readInt();
                    int size = dataInputStream.readInt();
                    if(dataInputStream.available() < size) {
                        flush();
                    }
                    String stream = "stdout";
                    if((flags & 0x2000000) != 0) {
                        stream = "stderr";
                    }
                    if(size > buffer.length) {
                        // Resize buffer
                        buffer = new byte[size];
                    }
                    IOUtils.readFully(inputStream, buffer, 0, size);
                    String message = new String(buffer, 0, size);

                    int spaceIndex = message.indexOf(" ");
                    // separate timestamp and actual message
                    if(spaceIndex < 0) {
                        log.error("Malformed log message: {}", message);
                        break;
                    }
                    String timeStamp = message.substring(0, spaceIndex);
                    // Currently (docker 1.5) the timestamps are sometimes surround with brackets []
                    int startIndex = 0;
                    if(timeStamp.startsWith("[")) {
                        timeStamp = timeStamp.substring(1, timeStamp.length()-1);
                    }
                    String log = message.substring(spaceIndex+1);
                    sb.append("{\"log\": \"").append(StringEscapeUtils.escapeJson(log)).append("\", ");
                    sb.append("\"stream\": \"").append(stream).append("\", ");
                    sb.append("\"time\": \"").append(timeStamp).append("\"}");
                    sb.append(",");
                    if(sb.length() > 1024*1024) {
                        flush();
                    }
                }
            } catch (EOFException expected) {

            } catch(Throwable throwable) {
                log.error("Error pushing logs", throwable);
            }
        }, "ContainerLogPusher "+streamId).start();
    }

    private void flush() throws IOException {
        int length = sb.length();
        if(sb.substring(length -1).equals(",")) {
            sb.deleteCharAt(sb.length()-1);
        }
        sb.append("]}");
        webSocket.sendTextMessage(sb.toString());
        initStringBuilder();
        if(length < 10000) {
            // Only send a small portion last time, sleep a little to let stream catch up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Throwables.propagate(e);
            }
        }
    }

    private void initStringBuilder() {
        sb = new StringBuilder(1024*1024+10*1024);
        sb.append("{\n");
        sb.append("\"type\": \"containerLogMessages\",\n");
        sb.append("\"streamId\": \"").append(streamId).append("\",\n");
        sb.append("\"messages\": [\n");
    }

    public void stop() {
        running = false;
    }
}
