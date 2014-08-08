package io.github.floto.core.tasks;

import io.github.floto.dsl.model.Manifest;

public abstract class ManifestTask<T> implements Task<T> {

    protected Manifest manifest;

    public ManifestTask(Manifest manifest) {
        this.manifest = manifest;
    }
}
