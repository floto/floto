package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.util.task.TaskInfo;

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
	public TaskInfo<Void> redeployContainers(ContainersRequest containersRequest) {
		return flotoService.redeployContainers(containersRequest.containers);
	}

	@POST
	@Path("_stop")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> stopContainers(ContainersRequest containersRequest) {
		return flotoService.stopContainers(containersRequest.containers);
	}

    @POST
    @Path("_destroyUnmanaged")
    @Produces(MediaType.APPLICATION_JSON)
    public TaskInfo<Void> destroyUnmanagedContainer(UnmanagedContainerRequest containerRequest) {
        return flotoService.destroyUnmanagedContainer(containerRequest.containerName, containerRequest.hostName);
    }


    @POST
	@Path("_start")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> startContainers(ContainersRequest containersRequest) {
		return flotoService.startContainers(containersRequest.containers);
	}

	@POST
	@Path("_restart")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> restartContainers(ContainersRequest containersRequest) {
		return flotoService.restartContainers(containersRequest.containers);
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
	public TaskInfo<Void> purgeData(ContainersRequest containersRequest) {
		return flotoService.purgeContainerData(containersRequest.containers);
	}

	@Path("{containerName}")
	public ContainerResource getDockerfile(@PathParam("containerName") String containerName) {
		return new ContainerResource(flotoService, containerName);
	}

}
