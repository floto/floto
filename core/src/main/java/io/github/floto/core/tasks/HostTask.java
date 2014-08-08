package io.github.floto.core.tasks;

import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;

public abstract class HostTask<T> extends ManifestTask<T> {

    protected final Host host;

    public HostTask(Manifest manifest, String hostName) {
        super(manifest);
        this.host = manifest.findHost(hostName);
    }

}


