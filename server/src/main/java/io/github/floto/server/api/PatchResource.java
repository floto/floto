package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.core.patch.PatchService;
import io.github.floto.util.task.TaskInfo;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("patch")
public class PatchResource {
	private FlotoService flotoService;
	private PatchService patchService;

	public PatchResource(FlotoService flotoService, PatchService patchService) {
		this.flotoService = flotoService;
		this.patchService = patchService;
	}

	@POST
	@Path("create-initial")
	public TaskInfo<Void> createInitialPatch() {
		return patchService.createInitialPatch();
	}

}
