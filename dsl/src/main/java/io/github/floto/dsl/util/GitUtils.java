package io.github.floto.dsl.util;

import com.google.common.base.Throwables;
import io.github.floto.util.GitHelper;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class GitUtils {
	public static String describe(String directory) {
		String stubValue = getStubValue(directory, ".GIT_DESCRIBE");
		if (stubValue != null) {
			return stubValue;
		}
		return new GitHelper(new File(directory)).describe();
	}

	public static String timestamp(String directory) {
		String stubValue = getStubValue(directory, ".GIT_TIMESTAMP");
		if (stubValue != null) {
			return stubValue;
		}
		return new GitHelper(new File(directory)).timestamp();
	}

	private static String getStubValue(String dir, String file) {
		File directory = new File(dir);
		while (directory != null) {
			File stubFile = new File(directory, file);
			if (stubFile.exists()) {
				try {
					return FileUtils.readFileToString(stubFile);
				} catch (IOException e) {
					Throwables.propagate(e);
				}
			}
			directory = directory.getParentFile();
		}
		return null;
	}


}
