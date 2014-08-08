package io.github.floto.util;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class GitHelperTest {

    @Test
    public void testDescribe() throws IOException {
        File directory = new File("");
        GitHelper gitHelper = new GitHelper(directory.getAbsoluteFile());
        String describe = gitHelper.describe();
        Assert.assertNotNull(describe);
    }

    @Test
    public void testTimestamp() throws IOException {
        File directory = new File("");
        GitHelper gitHelper = new GitHelper(directory.getAbsoluteFile());
        String timestamp = gitHelper.timestamp();
        Assert.assertNotNull(timestamp);
        Assert.assertThat(timestamp, CoreMatchers.startsWith("20"));
        Assert.assertThat(timestamp, CoreMatchers.endsWith("Z"));
    }
}