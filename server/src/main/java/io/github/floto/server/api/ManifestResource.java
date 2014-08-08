package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.dsl.FlotoDsl;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("manifest")
public class ManifestResource {
	private FlotoDsl flotoDsl = new FlotoDsl();
	private FlotoService flotoService;

	public ManifestResource(FlotoService flotoService) {
		this.flotoService = flotoService;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String getManifest() {
		return flotoService.getManifestString();
	}

	@POST
	@Path("reload")
	public void reloadManifest() {
		flotoService.reload();
	}

}
