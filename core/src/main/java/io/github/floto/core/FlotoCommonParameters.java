package io.github.floto.core;

import com.beust.jcommander.Parameter;

public class FlotoCommonParameters {
    @Parameter(names = "--root", description = "Root definition file (site JS)", required = true)
    public String rootDefinitionFile;

    @Parameter(names = "--environment", description = "The target environment (production, development, testing)")
    public String environment;

    @Parameter(names = "--no-proxy", description = "Disable HTTP proxy")
    public boolean noProxy = false;

    @Parameter(names = "--proxy-url", description = "Set custom HTTP-proxy address")
    public String proxyUrl;

    @Parameter(names = "--proxy-prefix", description = "When several IP addresses are available for proxying, choose the one with the given prefix")
    public String proxyPrefix;

}
