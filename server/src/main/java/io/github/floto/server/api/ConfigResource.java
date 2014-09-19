package io.github.floto.server.api;

import com.google.common.base.Charsets;
import org.apache.commons.io.FileUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Path("/client-config.js")
public class ConfigResource {

	@GET
	@Produces("text/javascript")
	public Response getConfig() throws Exception {
		StreamingOutput output = new StreamingOutput() {
			@Override
			public void write(OutputStream outputStream) throws IOException,
					WebApplicationException {
				File configFile = new File("client-config.js");
				if(configFile.exists()) {
					FileUtils.copyFile(configFile, outputStream);
				} else {
					outputStream.write(("// Client configuration not found: "+configFile).getBytes(Charsets.UTF_8));
				}

			}
		};
		return Response.ok(output).build();
	}

}
