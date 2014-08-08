package io.github.floto.dsl.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties("definition")
public class Image {
	public String name;
	public List<JsonNode> buildSteps = new ArrayList<>();
}
