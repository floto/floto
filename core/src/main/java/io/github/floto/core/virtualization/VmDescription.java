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
		public long sizeInGB;
		public String path = "[]";
		public String datastore;
		public boolean thinProvisioned = true;
		public int slot;
		public String mountpoint;
		public VmDescription vmDescription;
		public Disk(VmDescription vmDescription){
			this.vmDescription = vmDescription;
		}
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
				ret += sep + disk.datastore + " " + disk.path + " " + disk.sizeInGB + " thin="+disk.thinProvisioned;
				sep = ", ";
			}
		}
		
		return ret;
	}
}
