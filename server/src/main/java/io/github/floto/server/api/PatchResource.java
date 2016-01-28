package io.github.floto.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.floto.core.FlotoService;
import io.github.floto.core.patch.PatchDescription;
import io.github.floto.core.patch.PatchInfo;
import io.github.floto.core.patch.PatchService;
import io.github.floto.dsl.model.Container;
import io.github.floto.util.task.TaskInfo;
import org.apache.http.entity.FileEntity;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PatchResource {
    private final FlotoService flotoService;
    private final PatchService patchService;
    private final String patchId;

    public PatchResource(FlotoService flotoService, PatchService patchService, String patchId) {
        this.flotoService = flotoService;
        this.patchService = patchService;
        this.patchId = patchId;
    }

    @GET
    @Path("patchInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public PatchInfo getPatchInfo() {
        return patchService.getPatchInfo(patchId);
    }

	@GET
	@Path("download")
	public Response download() {
		File patchFile = patchService.getPatchFile(patchId);
		Response.ResponseBuilder response = Response.ok(patchFile);
		return response.build();
	}

    @POST
    @Path("activate")
    @Produces(MediaType.APPLICATION_JSON)
    public TaskInfo activatePatch() {
        return patchService.activatePatch(patchId);
    }

}
