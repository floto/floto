package io.github.floto.core.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class DockerfileHelper {

    private boolean useProxy = false;
    String httpProxyUrl = null;

    public void setHttpProxy(String httpProxyUrl) {
        this.httpProxyUrl = httpProxyUrl;
        useProxy = true;
    }


    public String createDockerfile(List<JsonNode> buildSteps) {
        StringBuilder dockerfileBuilder = new StringBuilder();
        for (JsonNode step : buildSteps) {
            String type = step.path("type").asText();
            String line = step.path("line").asText();
            if ("ADD_TEMPLATE".equals(type)) {
                type = "ADD";
                String destination = step.path("destination").asText();
                String source = destination;
                if (source.startsWith("/")) {
                    source = source.substring(1);
                }
                line = source + " " + destination;
            } else if ("COPY_FILES".equals(type)) {
                type = "ADD";
                String destination = step.path("destination").asText();
                line = destination + " " + destination;
            } else if ("COPY_DIRECTORY".equals(type)) {
                type = "ADD";
                String destination = step.path("destination").asText();
                line = destination + " " + destination;
            } else if ("ADD_MAVEN".equals(type)) {
                type = "ADD";
                String destination = step.path("destination").asText();
                String source = destination;
                String coordinates = step.path("coordinates").asText();
                if (coordinates.contains(":tar.gz:")) {
                    // append .x to allow writing into directories which were created by tar.gz adds
                    source = source + ".x";
                }
                if (source.startsWith("/")) {
                    source = source.substring(1);
                }
                line = source + " " + destination;
            } else if ("DOWNLOAD".equals(type)) {
                String url = step.path("url").asText();
                String destination = step.path("destination").asText();
                type = "RUN";
                line = "wget \"" + url + "\" --output-document=" + destination;
            } else if ("VOLUME".equals(type)) {
                String path = step.path("path").asText();
                String name = step.path("name").asText();
                line = path;
            }

            if (useProxy && type.equals("RUN")) {
                line = "export http_proxy=" + httpProxyUrl + "; export https_proxy=" + httpProxyUrl + "; " + line;
            }
            dockerfileBuilder.append(type).append(" ").append(line);
            dockerfileBuilder.append("\n");
        }
        return dockerfileBuilder.toString();
    }
}
