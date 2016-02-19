package io.github.floto.server.api;

import io.github.floto.core.FlotoService;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("documents")
public class DocumentsResource {
	private FlotoService flotoService;
	private String containerName;

	public DocumentsResource(FlotoService flotoService) {
		this.flotoService = flotoService;
	}

	@GET
	@Path("{documentId:.*}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getTemplate(@PathParam("documentId") String documentId) {
		return Response.ok(flotoService.getDocumentString(documentId)).type(MediaType.TEXT_HTML_TYPE).build();
	}


}
