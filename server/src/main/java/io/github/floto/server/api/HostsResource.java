package io.github.floto.server.api;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import io.github.floto.core.FlotoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.floto.core.HostService;

@Path("hosts")
public class HostsResource {
    private final FlotoService flotoService;
    private HostService hostService;
	private Logger log = LoggerFactory.getLogger(HostsResource.class);
	
    public HostsResource(FlotoService flotoService, HostService hostService) {
        this.flotoService = flotoService;
        this.hostService = hostService;
    }

    @POST
	@Path("_redeploy")
	@Produces(MediaType.APPLICATION_JSON)
	public String redeployHosts(HostsRequest hostsRequest) {
		log.info("redeployHosts");
		for (String host: hostsRequest.hosts) {
			log.info("redeployVm: "+host);
			hostService.redeployVm(host);
		}
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_stop")
	@Produces(MediaType.APPLICATION_JSON)
	public String stopHosts(HostsRequest hostsRequest) {
		log.info("stopHosts");
		for (String host: hostsRequest.hosts) {
			log.info("stopVm: "+host);
			hostService.stopVm(host);
		}
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_start")
	@Produces(MediaType.APPLICATION_JSON)
	public String startHosts(HostsRequest hostsRequest) {
		log.info("startHosts");
		for (String host: hostsRequest.hosts) {
			log.info("startVm: "+host);
			hostService.startVm(host);
		}
		return "{\"result\": \"success\"}";
	}

	@POST
	@Path("_restart")
	@Produces(MediaType.APPLICATION_JSON)
	public String restartHosts(HostsRequest hostsRequest) {
		stopHosts(hostsRequest);
		startHosts(hostsRequest);
		return "{\"result\": \"success\"}";
	}

	@GET
	@Path("_state")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getState() {
		log.info("getState");
		Map<String, Object> result = new HashMap<>();
		result.put("states", hostService.getHostStates());
		return result;
	}

	@POST
	@Path("_delete")
	@Produces(MediaType.APPLICATION_JSON)
	public String deleteHosts(HostsRequest hostsRequest) {
		log.info("deleteHosts");
		for (String host: hostsRequest.hosts) {
			log.info("deleteVm: "+host);
			hostService.deleteVm(host);
		}
		return "{\"result\": \"success\"}";
	}

    @Path("{hostName}")
    public HostResource getDockerfile(@PathParam("hostName") String containerName) {
        return new HostResource(flotoService, containerName);
    }


}
