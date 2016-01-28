package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.util.task.TaskInfo;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("manifest")
public class ManifestResource {
	private FlotoService flotoService;

	public ManifestResource(FlotoService flotoService) {
		this.flotoService = flotoService;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getManifest() {
		String manifestString = flotoService.getManifestString();
		if(manifestString == null) {
			Throwable manifestCompilationError = flotoService.getManifestCompilationError();
			if(manifestCompilationError != null) {
				throw new WebApplicationException(manifestCompilationError, 555);
			}
		}
		return manifestString;
	}

	@POST
	@Path("compile")
	public TaskInfo<Void> compileManifest() {
		return flotoService.compileManifest();
	}

}
