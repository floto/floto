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
	public String projectRevision;

}
