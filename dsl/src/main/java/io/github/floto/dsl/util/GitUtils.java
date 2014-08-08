package io.github.floto.dsl.util;

import io.github.floto.util.GitHelper;

import java.io.File;

public class GitUtils {
    public static String describe(String directory) {
        return new GitHelper(new File(directory)).describe();
    }
    public static String timestamp(String directory) {
        return new GitHelper(new File(directory)).timestamp();
    }
}
