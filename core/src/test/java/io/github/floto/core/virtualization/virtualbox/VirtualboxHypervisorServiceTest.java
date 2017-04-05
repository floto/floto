package io.github.floto.core.virtualization.virtualbox;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

public class VirtualboxHypervisorServiceTest {
	
	private VirtualboxHypervisorService hService = new VirtualboxHypervisorService(null, new File(System.getProperty("user.home"),
			".floto"));
	
	@Test
	@Ignore
	public void testCreateHostonlyIf() throws Exception {
		String hostonlyNw = this.hService.createHostonlyIf();
		System.out.println("hostonlyNw=" + hostonlyNw);
	}

}
