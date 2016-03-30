package io.github.floto.server.api;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.google.common.base.Joiner;
import io.github.floto.core.FlotoService;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.floto.core.HostService;

@Path("hosts")
public class HostsResource {
    private final FlotoService flotoService;
    private HostService hostService;
    private TaskService taskService;
    private Logger log = LoggerFactory.getLogger(HostsResource.class);
	
    public HostsResource(FlotoService flotoService, HostService hostService, TaskService taskService) {
        this.flotoService = flotoService;
        this.hostService = hostService;
        this.taskService = taskService;
    }

    @POST
	@Path("_redeploy")
	@Produces(MediaType.APPLICATION_JSON)
    public TaskInfo<Void> redeployHosts(HostsRequest hostsRequest) {
        return taskService.startTask("Redeploy hosts " + getHostsString(hostsRequest), () -> {
            for (String host : hostsRequest.hosts) {
                log.info("Redeploying: {}", host);
                hostService.redeployVm(host);
            }
            return null;
        });
    }

	@POST
	@Path("_export")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> exportHosts(HostsRequest hostsRequest) {
		return taskService.startTask("Export hosts " + getHostsString(hostsRequest), () -> {
			for (String host : hostsRequest.hosts) {
				log.info("Exporting: {}", host);
				hostService.exportVm(host);
			}
			return null;
		});
	}

    private String getHostsString(HostsRequest hostsRequest) {
        return Joiner.on(",").join(hostsRequest.hosts);
    }

    @POST
	@Path("_stop")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> stopHosts(HostsRequest hostsRequest) {
        return taskService.startTask("Stop hosts " + getHostsString(hostsRequest), () -> {
            for (String host : hostsRequest.hosts) {
                log.info("Stopping: {}", host);
                hostService.stopVm(host);
            }
            return null;
        });
	}

	@POST
	@Path("_start")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> startHosts(HostsRequest hostsRequest) {
        return taskService.startTask("Start hosts " + getHostsString(hostsRequest), () -> {
            for (String host : hostsRequest.hosts) {
                log.info("Starting: {}", host);
                hostService.startVm(host);
            }
            return null;
        });
	}

	@POST
	@Path("_restart")
	@Produces(MediaType.APPLICATION_JSON)
	public TaskInfo<Void> restartHosts(HostsRequest hostsRequest) {
        return taskService.startTask("Restart hosts " + getHostsString(hostsRequest), () -> {
            for (String host : hostsRequest.hosts) {
                log.info("Stopping: {}", host);
                hostService.stopVm(host);
            }
            for (String host : hostsRequest.hosts) {
                log.info("Starting: {}", host);
                hostService.startVm(host);
            }
            return null;
        });
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
	public TaskInfo<Void> deleteHosts(HostsRequest hostsRequest) {
        return taskService.startTask("Delete hosts " + getHostsString(hostsRequest), () -> {
            for (String host : hostsRequest.hosts) {
				log.info("Stopping: {}", host);
				hostService.stopVm(host);
                log.info("Deleting: {}", host);
                hostService.deleteVm(host);
            }
            return null;
        });
    }

    @Path("{hostName}")
    public HostResource getDockerfile(@PathParam("hostName") String containerName) {
        return new HostResource(flotoService, containerName);
    }


}
