package io.github.floto.server.api;

import io.github.floto.core.FlotoService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

public class ContainerResource {
	private FlotoService flotoService;
	private String containerName;

	public ContainerResource(FlotoService flotoService, String containerName) {
		this.flotoService = flotoService;
		this.containerName = containerName;
	}

	@GET
	@Path("dockerfile/{type}")
	@Produces(MediaType.TEXT_PLAIN)
	public String getDockerfile(@PathParam("type") String type) {
		return flotoService.getDockerfile(containerName, type);
	}

	@GET
	@Path("log")
	@Produces(MediaType.TEXT_PLAIN)
	public StreamingOutput getLog() {
		return new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				flotoService.getLog(containerName, output);
			}
		};
	}

	@GET
	@Path("buildlog")
	@Produces(MediaType.TEXT_PLAIN)
	public StreamingOutput getBuildLog() {
		return new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				flotoService.getBuildLog(containerName, output);
			}
		};
	}

	@GET
	@Path("template/{path:.*}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getTemplate(@PathParam("path") String path) {
		return Response.ok(flotoService.getTemplate(containerName, path)).type(MediaType.TEXT_PLAIN).build();
	}


}
