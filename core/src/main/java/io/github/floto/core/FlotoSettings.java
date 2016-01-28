package io.github.floto.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FlotoSettings {
    public String activePatchId;
    public String activeSite;
}
