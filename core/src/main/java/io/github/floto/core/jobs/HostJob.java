package io.github.floto.core.jobs;

import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;

public abstract class HostJob<T> extends ManifestJob<T> {

    protected final Host host;

    public HostJob(Manifest manifest, String hostName) {
        super(manifest);
        this.host = manifest.findHost(hostName);
    }

}


