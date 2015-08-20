package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.core.patch.PatchService;
import io.github.floto.util.task.TaskInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Path("patches")
public class PatchResource {
	private FlotoService flotoService;
	private PatchService patchService;

	public PatchResource(FlotoService flotoService, PatchService patchService) {
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


	@POST
	@Path("create-initial")
	public TaskInfo<Void> createInitialPatch() {
		return patchService.createInitialPatch();
	}

}
