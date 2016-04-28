package io.github.floto.core;

import com.beust.jcommander.Parameter;

public class FlotoCommonParameters {
    @Parameter(names = "--root", description = "Root definition file (site JS)")
    public String rootDefinitionFile;

    @Parameter(names = "--environment", description = "The target environment (production, development, testing)")
    public String environment;

    @Parameter(names = "--no-proxy", description = "Disable HTTP proxy")
    public boolean noProxy = false;

	@Parameter(names = "--proxy-port", description = "HTTP-proxy port")
	public int proxyPort = 40005;

	@Parameter(names = "--proxy-url", description = "Set custom HTTP-proxy address")
    public String proxyUrl;

    @Parameter(names = "--proxy-prefix", description = "When several IP addresses are available for proxying, choose the one with the given prefix")
    public String proxyPrefix;

    @Parameter(names = "--floto-home", description = "Home directory of floto to store temporary files, patches, etc.")
    public String flotoHome;

    @Parameter(names = "--patch-mode", description = "The patch mode of this instance (\"create\" to create patches (default), \"apply\" to apply those")
    public String patchMode = "create";

    @Parameter(names = {"--patch-maker"}, description = "Patch-maker mode to create patches")
    public boolean patchMaker = false;

	@Parameter(names = {"--allow-dirty-patches"}, description = "Allow patch maker to create dirty patches (with uncommitted changes)")
	public boolean dirtyPatchesAllowed = false;

}
