package io.github.floto.server.api;

import java.io.File;
import java.io.FileInputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import jersey.repackaged.com.google.common.base.Throwables;

import org.apache.commons.io.IOUtils;


@Path("template")
public class TemplateResource {
	
	@GET
    @Path("latest")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
    public StreamingOutput getImage() {
		
			return o -> {
				try(FileInputStream fis = new FileInputStream(new File("/floto/images/vmware.ova"))) {
					IOUtils.copy(fis, o);
				}
				catch(Throwable t) {
					throw Throwables.propagate(t);
				}
			};
    }
}
