package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.util.task.TaskInfo;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("patch")
public class PatchResource {
	private FlotoService flotoService;

	public PatchResource(FlotoService flotoService) {
		this.flotoService = flotoService;
	}

	@POST
	@Path("create")
	public TaskInfo<Void> createPatch() {
		return flotoService.createGenesisPatch();
	}

}
