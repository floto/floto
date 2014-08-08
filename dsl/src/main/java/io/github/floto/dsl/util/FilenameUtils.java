package io.github.floto.dsl.util;

import java.nio.file.Paths;

public class FilenameUtils {
	public static String normalize(String filename) {
		return Paths.get(filename).normalize().toString();
	}
}
