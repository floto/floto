package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.util.VersionUtil;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("info")
public class InfoResource {

        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Map<String, Object> getManifest() {
            HashMap<String, Object> info = new HashMap<>();
            info.put("flotoVersion", VersionUtil.version);
            info.put("flotoRevision", VersionUtil.revision);
            return info;
        }


    }
