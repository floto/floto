package io.github.floto.builder;

import com.beust.jcommander.Parameter;
import io.github.floto.core.FlotoCommonParameters;

public class FlotoBuilderParameters extends FlotoCommonParameters {
	@Parameter(names = "--compile-check", description = "Only run the compilation step, return exit code 0 iff compile is successful and without warnings")
	public boolean compileCheck = false;
}
