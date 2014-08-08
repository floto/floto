package io.github.floto.dsl.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

public class Manifest {
	public List<Image> images = new ArrayList<>();
	public List<Container> containers = new ArrayList<>();
	public List<Host> hosts = new ArrayList<>();
	public JsonNode site;
    public Map<String, Object> files = new HashMap<>();
    public String rootFile;

    public Host findHost(String hostName) {
        for (Host candidate : hosts) {
            if (hostName.equals(candidate.name)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown host: " + hostName);
    }
}
