package io.github.floto.core.virtualization;

import io.github.floto.dsl.model.Host;

import java.io.File;
import java.net.URL;
import java.util.List;

public interface HypervisorService {

	//url to ova
	public void deployVm(URL vmUrl, VmDescription desc);
	
	public void exportVm(String vmName, String hostName, String Path);
	
	//public void exportVm(String vmname, OutputStream out);
	
	public void deleteVm(String vmname);
	
	public VmDescription getVmDescription(String vmname);
	
	public List<VmDescription> getAllVms();
	
	public boolean isVmRunning(String vmname);
	
	public void startVm(String vmname);
	
	public void stopVm(String vmname);
	
	public void runInVm(String vmname, String cmd);

	void copyFileFromGuest(String vmName, String source, File destination);

	default public void setHostOnlyIpVBoxWin(String vmname, String proxy) {};

}
