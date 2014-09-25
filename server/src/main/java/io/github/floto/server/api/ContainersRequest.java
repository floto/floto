package io.github.floto.server.api;

import io.github.floto.core.FlotoService.DeploymentMode;

import java.util.ArrayList;
import java.util.List;

public class ContainersRequest {
	public List<String> containers = new ArrayList<>();
	public DeploymentMode deploymentMode;
}
