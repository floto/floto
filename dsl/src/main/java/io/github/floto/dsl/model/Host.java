package io.github.floto.dsl.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class Host {
	public String name;
	public String ip;
	public VmConfiguration vmConfiguration;
    public String exportName;
    public List<JsonNode> postDeploySteps = new ArrayList<>();
    public List<JsonNode> reconfigureSteps = new ArrayList<>();
}
