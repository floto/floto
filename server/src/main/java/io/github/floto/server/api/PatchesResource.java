package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.core.patch.PatchCreationParams;
import io.github.floto.core.patch.PatchService;
import io.github.floto.core.patch.PatchesInfo;
import io.github.floto.util.task.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Path("patches")
public class PatchesResource {
	private Logger log = LoggerFactory.getLogger(PatchesResource.class);

	private FlotoService flotoService;
	private PatchService patchService;

	public PatchesResource(FlotoService flotoService, PatchService patchService) {
		this.flotoService = flotoService;
		this.patchService = patchService;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public PatchesInfo getPatches() {
		return patchService.getPatches();
	}

	@Path("{patchId}")
	public PatchResource getTask(@PathParam("patchId") String patchId) {
		return new PatchResource(flotoService, patchService, patchId);
	}

	@POST
	@Path("create")
	public TaskInfo<Void> createPatch(PatchCreationParams patchCreationParams) {
		return patchService.createPatch(patchCreationParams);
	}

	@POST
	@Path("upload/{filename}")
	public TaskInfo<Void> uploadPatch(@PathParam("filename") String filename, final InputStream inputStream) throws Exception {
		log.info("Uploading patch: {}", filename);
		return patchService.uploadPatch(filename, inputStream);
	}

}
