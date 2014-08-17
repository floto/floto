package io.github.floto.dsl.util;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class GitUtilsTest {

    @Test
    public void testDescribe() throws Exception {
        String description = GitUtils.describe(".");
        assertThat(description, CoreMatchers.notNullValue());
    }

    @Test
    public void testTimestamp() throws Exception {
        String timestamp = GitUtils.timestamp(".");
        assertNotNull(timestamp);
        TemporalAccessor date = DateTimeFormatter.ISO_INSTANT.parse(timestamp);
        assertNotNull(date);
    }
}