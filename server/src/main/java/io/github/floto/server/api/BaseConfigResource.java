package io.github.floto.server.api;

import com.google.common.base.Charsets;
import io.github.floto.server.FlotoServerParameters;
import org.apache.commons.io.FileUtils;

import javax.activation.URLDataSource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Path("/client-config.base.js")
public class BaseConfigResource {

	private FlotoServerParameters parameters;

	public BaseConfigResource(FlotoServerParameters parameters) {
		this.parameters = parameters;
	}

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
					outputStream.write(("// No base config required").getBytes(Charsets.UTF_8));
				}

			}
		};
		if(parameters.developmentMode) {
			return Response.ok(new URLDataSource(BaseConfigResource.class.getResource("/io/github/floto/server/client-config.base.development.js"))).build();
		} else {
			return Response.ok("// No base config required").build();
		}
	}

}
