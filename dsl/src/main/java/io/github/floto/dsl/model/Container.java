package io.github.floto.dsl.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class Container {
	public String name;
	public String image;
	public String host;
	public JsonNode config;
	public List<JsonNode> configureSteps;
    public boolean priviledged = false;
    public boolean externalContainer = false;
    public boolean startable = true;
    public boolean stoppable = true;
    public boolean purgeable = true;
	public String projectRevision;

	public String buildHash;
}
