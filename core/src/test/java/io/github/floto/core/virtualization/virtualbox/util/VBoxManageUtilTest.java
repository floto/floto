package io.github.floto.core.virtualization.virtualbox.util;

import static io.github.floto.core.virtualization.virtualbox.util.VBoxManageUtil.LINE_SEPARATOR;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class VBoxManageUtilTest {
	
	@Test
	public void testConvertVBoxManageResult() throws Exception {
		
		String result = "NetworkName:    vboxnet2" + LINE_SEPARATOR +
				"IP:             192.169.91.2" + LINE_SEPARATOR +
				"NetworkMask:    255.255.255.0" + LINE_SEPARATOR +
				"lowerIPAddress: 192.169.91.100" + LINE_SEPARATOR +
				"upperIPAddress: 192.169.91.100" + LINE_SEPARATOR +
				"Enabled:        Yes" + LINE_SEPARATOR + LINE_SEPARATOR +

				"NetworkName:    HostInterfaceNetworking-vboxnet0" + LINE_SEPARATOR +
				"IP:             193.169.91.1" + LINE_SEPARATOR +
				"NetworkMask:    255.255.255.0" + LINE_SEPARATOR +
				"lowerIPAddress: 193.169.91.100" + LINE_SEPARATOR +
				"upperIPAddress: 193.169.91.200" + LINE_SEPARATOR +
				"Enabled:        Yes" + LINE_SEPARATOR + LINE_SEPARATOR + "";
		
		List<Map<String, String>> resultConverted = VBoxManageUtil.convertVBoxManageResult(result);
		assertEquals(2, resultConverted.size());
		assertEquals(6, resultConverted.get(0).size());
		assertEquals(6, resultConverted.get(1).size());
		assertEquals("vboxnet2", resultConverted.get(0).get("NetworkName"));
		assertEquals("Yes", resultConverted.get(1).get("Enabled"));
	}

}
