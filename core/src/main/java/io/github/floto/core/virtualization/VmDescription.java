package io.github.floto.core.virtualization;

import java.util.ArrayList;
import java.util.List;

public class VmDescription {
	public String vmName;
	public int numberOfCores;
	public long memoryInMB;
	public boolean running;
	public List<String> vmNetworks = new ArrayList<>();
	public List<Disk> disks = new ArrayList<>();

	public static class Disk {
		public long sizeInMB;
		public String path;
		public boolean thinProvisioned;
	}

	public String toString(){
		String ret= "vmName:"+vmName+"; "+
				"; numberOfCores:"+numberOfCores+
				"; memoryInMB:"+memoryInMB+
				"; running:"+running;

		//list networks
		if (vmNetworks != null && vmNetworks.size()>0) {
			ret += "; Network:";
			String sep = "";
			for (String net: vmNetworks){
				ret += sep+net;
				sep = ", ";
			}
		}
		
		//list disks
		if (disks != null && disks.size()>0) {
			ret += "; Disk:";
			String sep = "";
			for (Disk disk:disks){
				ret += sep + disk.path + " " + disk.sizeInMB + " thin="+disk.thinProvisioned;
				sep = ", ";
			}
		}
		
		return ret;
	}
}
