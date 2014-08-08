package io.github.floto.dsl.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class MavenUtilsTest {

    @Test
    public void testGetVersion() throws Exception {
        assertNotNull(MavenUtils.getVersion("."));
    }
}