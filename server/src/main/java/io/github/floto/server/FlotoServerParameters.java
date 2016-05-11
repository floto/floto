package io.github.floto.server;

import com.beust.jcommander.Parameter;
import io.github.floto.core.FlotoCommonParameters;

public class FlotoServerParameters extends FlotoCommonParameters {
    @Parameter(names = "--port", description = "HTTP Port")
    public int port = 40004;
}
