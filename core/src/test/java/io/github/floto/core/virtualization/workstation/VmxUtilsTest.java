package io.github.floto.core.virtualization.workstation;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class VmxUtilsTest {

    @Test
    public void testReadVmx() throws Exception {
        Map<String, String> map = VmxUtils.readVmx(VmxUtilsTest.class.getResourceAsStream("workstation.vmx"));
        assertEquals("persistent", map.get("scsi0:0.mode"));
        assertEquals(81, map.size());
    }
}