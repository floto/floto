package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.core.patch.PatchService;
import io.github.floto.util.task.TaskInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("patches")
public class PatchesResource {
	private FlotoService flotoService;
	private PatchService patchService;

	public PatchesResource(FlotoService flotoService, PatchService patchService) {
		this.flotoService = flotoService;
		this.patchService = patchService;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getPatches() {
		HashMap<String, Object> result = new HashMap<>();
		result.put("patches", patchService.getPatches());
		return result;
	}

	@Path("{patchId}")
	public PatchResource getTask(@PathParam("patchId") String patchId) {
		return new PatchResource(flotoService, patchService, patchId);
	}



	@POST
	@Path("create-full")
	public TaskInfo<Void> createFullPatch() {
		return patchService.createFullPatch();
	}

	@POST
	@Path("create-incremental-from/{parentPatchId}")
	public TaskInfo<Void> createIncrementalPatch(@PathParam("parentPatchId") String parentPatchId) {
		return patchService.createIncrementalPatch(parentPatchId);
	}
}
