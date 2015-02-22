package io.github.floto.server.api.websocket.handler;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.function.Consumer;

public class ContainerLogPusher {
    private final Logger log = LoggerFactory.getLogger(ContainerLogPusher.class);
    private final InputStream inputStream;
    private final String streamId;
    private Consumer<String> callback;

    public ContainerLogPusher(InputStream inputStream, String streamId, Consumer<String> callback) {
        this.inputStream = inputStream;
        this.streamId = streamId;
        this.callback = callback;
    }

    public void start() {
        new Thread(() -> {
            try(InputStream input = inputStream) {
                byte[] buffer = new byte[4*1024];
                DataInputStream dataInputStream = new DataInputStream(inputStream);
                while (true) {
                    int flags = dataInputStream.readInt();
                    int size = dataInputStream.readInt();
                    String stream = "stdout";
                    if((flags & 0x2000000) != 0) {
                        stream = "stderr";
                    }
                    // TODO: check size
                    IOUtils.readFully(inputStream, buffer, 0, size);
                    String message = new String(buffer, 0, size);
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\n");
                    sb.append("\"type\": \"containerLogMessages\",\n");
                    sb.append("\"streamId\": \"").append(streamId).append("\",\n");
                    sb.append("\"messages\": [\n");
                    sb.append("{\"log\": \"").append(StringEscapeUtils.escapeJson(message)).append("\", ");
                    sb.append("\"stream\": \"").append(stream).append("\"}");
                    sb.append("]}");
                    callback.accept(sb.toString());
                }
            } catch (EOFException expected) {


            } catch(Throwable throwable) {
                log.error("Error pushing logs", throwable);
            }
        }, "ContainerLogPusher "+streamId).start();
    }
}
