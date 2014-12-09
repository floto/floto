package io.github.floto.core.virtualization.virtualbox;

import org.junit.Ignore;
import org.junit.Test;

public class VirtualboxHypervisorServiceTest {
	
	private VirtualboxHypervisorService hService = new VirtualboxHypervisorService(null);
	
	@Test
	@Ignore
	public void testCreateHostonlyIf() throws Exception {
		String hostonlyNw = this.hService.createHostonlyIf();
		System.out.println("hostonlyNw=" + hostonlyNw);
	}

}
