package io.github.floto.core;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParameterReader {
	
	private static Logger log = LoggerFactory.getLogger(ParameterReader.class);
	
	public static File getFlotoHome(FlotoCommonParameters commonParameters) {
		// default
		File flotoHome = new File(System.getProperty("user.home") + "/.floto");
		// override through environment variable
		String envFlotoHome = System.getenv("FLOTO_HOME");
		if (envFlotoHome != null) {
			flotoHome = new File(envFlotoHome);
		}
		// override through command line
		if (commonParameters.flotoHome != null) {
			flotoHome = new File(commonParameters.flotoHome);
		}
		log.info("Using floto home: {}", flotoHome);
		try {
			FileUtils.forceMkdir(flotoHome);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create floto home " + flotoHome, e);
		}

		return flotoHome;
	}
}
