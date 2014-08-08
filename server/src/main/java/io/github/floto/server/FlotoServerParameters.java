package io.github.floto.server;

import com.beust.jcommander.Parameter;
import io.github.floto.core.FlotoCommonParameters;

public class FlotoServerParameters extends FlotoCommonParameters {
    @Parameter(names = {"--dev", "--development-mode"}, description = "Development Mode")
    boolean developmentMode = false;

    @Parameter(names = "--port", description = "HTTP Port")
    int port = 40004;
}
