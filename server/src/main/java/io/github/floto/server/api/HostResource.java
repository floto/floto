package io.github.floto.server.api;

import io.github.floto.core.FlotoService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

public class HostResource {
	private FlotoService flotoService;
	private String hostName;

	public HostResource(FlotoService flotoService, String hostName) {
		this.flotoService = flotoService;
		this.hostName = hostName;
	}

    @GET
    @Path("script/{type:.*}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getHostScript(@PathParam("type") String type) {
        return flotoService.getHostScript(hostName, type);
    }


    @GET
	@Path("template/{path:.*}")
	@Produces(MediaType.TEXT_PLAIN)
	public String getTemplate(@PathParam("path") String path) {
		return flotoService.getHostTemplate(hostName, path);
	}


}
