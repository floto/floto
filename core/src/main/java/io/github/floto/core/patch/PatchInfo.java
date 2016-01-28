package io.github.floto.core.patch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PatchInfo extends PatchDescription {

    public long patchSize;
}
