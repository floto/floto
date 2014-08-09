package io.github.floto.server.util;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.HashMap;
import java.util.UUID;

public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {
	private Logger log = LoggerFactory
			.getLogger(ThrowableExceptionMapper.class);

	private static final int INTERNAL_SERVER_ERROR = 500;

	@Context
	private Request request;
	@Context
	private UriInfo uriInfo;

	@Override
	public Response toResponse(Throwable exception) {
		final HashMap<String, Object> model = new HashMap<String, Object>();
		Object entity = model;
		int status = INTERNAL_SERVER_ERROR;
		String id = UUID.randomUUID().toString();
        String message = exception.getMessage();
        if(message == null) {
            message = exception.toString();
        }
        model.put("message", message);
		if (exception instanceof WebApplicationException) {
			WebApplicationException webApplicationException = (WebApplicationException) exception;
			status = webApplicationException.getResponse().getStatus();
			if (status == INTERNAL_SERVER_ERROR) {
				// Only log internal errors with high level
				log.error("Error handling '" + uriInfo.getRequestUri()
						+ "', id:" + id, exception);
			} else {
				log.info("Error handling '" + uriInfo.getRequestUri()
						+ "', id:" + id, exception);
			}
			if (webApplicationException.getResponse().getEntity() != null) {
				// response is provided, serve that
				return webApplicationException.getResponse();
			}
			// Try to get a decent message for our error
			if (exception.getCause() != null) {
				model.put("message", exception.getCause().getMessage());
			}
		} else {
			log.error("Error handling '" + uriInfo.getRequestUri() + "', id:"
					+ id, exception);
			model.put("stacktrace", ExceptionUtils.getFullStackTrace(exception));
		}

		model.put("id", id);
		model.put("stacktrace", ExceptionUtils.getFullStackTrace(exception));
		model.put("status", "" + status);

		return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
				.entity(entity).build();
	}
}
