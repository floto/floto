package io.github.floto.core.util;

import org.apache.commons.io.IOUtils;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class ErrorClientResponseFilter implements ClientResponseFilter {
	public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
			throws IOException {
		if (!Response.Status.Family.SUCCESSFUL.equals(responseContext.getStatusInfo().getFamily())) {
			String body = IOUtils.toString(responseContext.getEntityStream());
			throw new RuntimeException("Server error: " + responseContext.getStatus() + "  " + responseContext.getStatusInfo().getReasonPhrase()+"\n"+body);
		}
	}
}
