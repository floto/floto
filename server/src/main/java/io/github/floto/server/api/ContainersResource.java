package io.github.floto.server.api;

import io.github.floto.core.FlotoService;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("containers")
public class ContainersResource {
	private FlotoService flotoService;

	public ContainersResource(FlotoService flotoService) {
		this.flotoService = flotoService;
	}

	@POST
	@Path("_redeploy")
	@Produces(MediaType.APPLICATION_JSON)
	public String redeployContainers(ContainersRequest containersRequest) {
		flotoService.redeployContainers(containersRequest.containers);
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_stop")
	@Produces(MediaType.APPLICATION_JSON)
	public String stopContainers(ContainersRequest containersRequest) {
		flotoService.stopContainers(containersRequest.containers);
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_start")
	@Produces(MediaType.APPLICATION_JSON)
	public String startContainers(ContainersRequest containersRequest) {
		flotoService.startContainers(containersRequest.containers);
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_restart")
	@Produces(MediaType.APPLICATION_JSON)
	public String restartContainers(ContainersRequest containersRequest) {
		flotoService.restartContainers(containersRequest.containers);
		return "{\"result\": \"success\"}";
	}

	@GET
	@Path("_state")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getState() {
		Map<String, Object> result = new HashMap<>();
		result.put("states", flotoService.getContainerStates());
		return result;
	}

	@POST
	@Path("_purgeData")
	@Produces(MediaType.APPLICATION_JSON)
	public String purgeData(ContainersRequest containersRequest) {
		flotoService.purgeContainerData(containersRequest.containers);
		return "{\"result\": \"success\"}";
	}

	@Path("{containerName}")
	public ContainerResource getDockerfile(@PathParam("containerName") String containerName) {
		return new ContainerResource(flotoService, containerName);
	}

}
