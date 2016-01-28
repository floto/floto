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
import java.io.*;

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
            public PrintStream out;

            @Override
            public void write(OutputStream outputStream) throws IOException,
                    WebApplicationException {
                PrintStream out = this.out = new PrintStream(outputStream);
                out.println("floto.configure(function(config) {");
                if (parameters.developmentMode) {
                    addConfig("armed", "true");
                    addConfig("defaultDeploymentMode", "\"fromRootImage\"");
                }
                addConfig("patchMode", "\"" + parameters.patchMode + "\"");
                addConfig("canDeployFromRootImage", parameters.patchMode.equals("apply")?"false":"true");
                out.println("});");


            }

            private void addConfig(String key, String value) {
                out.println("\tconfig." + key + " = " + value + ";");
            }
        };
        return Response.ok(output).build();
    }

}
