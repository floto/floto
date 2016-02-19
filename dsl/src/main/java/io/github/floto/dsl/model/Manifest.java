package io.github.floto.dsl.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public class Manifest {
	public List<Image> images = new ArrayList<>();
	public List<Container> containers = new ArrayList<>();
	public List<Host> hosts = new ArrayList<>();
	public List<DocumentDefinition> documents = new ArrayList<>();
	public JsonNode site;
    public Map<String, Object> files = new HashMap<>();
    public String rootFile;
    public String projectRevision;

    public Host findHost(String hostName) {
        for (Host candidate : hosts) {
            if (hostName.equals(candidate.name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown host: " + hostName);
    }
    
    public Container findContainer(String containerName) {
    	return containers.stream().filter(c -> c.name.equals(containerName)).findFirst().orElse(null);
    }

    public Image findImage(String imageName) {
        for (Image candidate : images) {
            if (imageName.equals(candidate.name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown image: " + imageName);
    }


    @JsonIgnore
    public String getSiteName() {
        return site.get("projectName").asText();
    }
}
