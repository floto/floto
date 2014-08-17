package io.github.floto.core.jobs;

import io.github.floto.dsl.model.Manifest;

public abstract class ManifestJob<T> implements Job<T> {

    protected Manifest manifest;

    public ManifestJob(Manifest manifest) {
        this.manifest = manifest;
    }
}
