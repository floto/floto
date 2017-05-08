package io.github.floto.core.virtualization.esx;

import com.vmware.vim25.*;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;
import io.github.floto.core.virtualization.VmDescription.Disk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualDiskManager extends VirtualMachineDeviceManager {
    private Logger log = LoggerFactory.getLogger(VirtualDiskManager.class);
	private VirtualMachine vm;

	public VirtualDiskManager(VirtualMachine vm) {
		super(vm);
		this.vm = vm;
	}

	public VirtualMachine getVM() {
		return this.vm;
	}

	public VirtualDeviceConfigSpec createHardDisk(Disk vmDiskDesc, VirtualDiskType type, VirtualDiskMode mode, int ctrlKey) throws Exception {
		String vmdkPath = "[" + vmDiskDesc.datastore + "] " + vm.getName() + "/" + vm.getName() + "_" + (vmDiskDesc.slot+1) + ".vmdk"; //_data

		VirtualDiskFlatVer2BackingInfo diskfileBacking = new VirtualDiskFlatVer2BackingInfo();
		diskfileBacking.setFileName(vmdkPath);
		diskfileBacking.setDiskMode(mode.toString());
		diskfileBacking.setThinProvisioned(type == VirtualDiskType.thin);

		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		VirtualDisk disk = new VirtualDisk();
		disk.setControllerKey(ctrlKey);
		disk.setUnitNumber(vmDiskDesc.slot);
		disk.setBacking(diskfileBacking);
		disk.setCapacityInKB(1024l * 1024l * vmDiskDesc.sizeInGB);
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.create);
		diskSpec.setDevice(disk);

		return diskSpec;
	}

	public VirtualDeviceConfigSpec addVirtualDisk(Disk vmDiskDesc, VirtualDiskMode diskMode, int ctrlKey) throws Exception {
		String vmdkPath = "[" + vmDiskDesc.datastore + "] " + vm.getName() + "/" + vm.getName() + "_" + (vmDiskDesc.slot+1) + ".vmdk"; //_data

		VirtualDiskFlatVer2BackingInfo diskfileBacking = new VirtualDiskFlatVer2BackingInfo();
		diskfileBacking.setFileName(vmdkPath);
		diskfileBacking.setDiskMode(diskMode.toString());

		VirtualDisk disk = new VirtualDisk();
		disk.setControllerKey(ctrlKey);
		disk.setUnitNumber(vmDiskDesc.slot);
		disk.setBacking(diskfileBacking);

		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		diskSpec.setDevice(disk);

		return diskSpec;
	}
	  
	

	public void removeVirtualDisk(VirtualDisk virtualDisk, boolean destroyBacking) throws Exception {
		
		VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();

		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
		if (destroyBacking){
			diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.destroy);
		}
		diskSpec.setDevice(virtualDisk);

		VirtualDeviceConfigSpec[] vdiskSpecArray = { diskSpec };
		vmConfigSpec.setDeviceChange(vdiskSpecArray);

		Task task = vm.reconfigVM_Task(vmConfigSpec);
		EsxUtils.waitForTask(task, "remove virtual disk from "+ vm.getName());
	}



	public VirtualDisk findVirtualDisk(Integer unitNumber) {
		VirtualDevice[] devices = getAllVirtualDevices();

		for (int i = 0; i < devices.length; i++) {
			if (devices[i] instanceof VirtualDisk) {
				VirtualDisk vDisk = (VirtualDisk) devices[i];
				if (unitNumber.intValue() == vDisk.getUnitNumber().intValue()) {
					return vDisk;
				}
			}
		}
		return null;
	}

	public VirtualDisk findFirstVirtualDisk() {
		VirtualDevice[] devices = getAllVirtualDevices();

		for (int i = 0; i < devices.length; i++) {
			if (devices[i] instanceof VirtualDisk) {
				return (VirtualDisk) devices[i];
			}
		}
		return null;
	}

	private String getFileName(String path, boolean withExtension) {
		String fileName = path.toString().substring(path.toString().lastIndexOf('/') + 1, path.toString().length());
		if (withExtension) {
			return fileName;
		} else {
			return fileName.substring(0, fileName.lastIndexOf('.'));
		}
	}

	private ParaVirtualSCSIController findSCSIController(){
		ParaVirtualSCSIController scsiCtrl = null;
		for (VirtualDevice vDev : vm.getConfig().getHardware().device) {
			if (vDev instanceof ParaVirtualSCSIController) {
				scsiCtrl = (ParaVirtualSCSIController)vDev;
				break;
			}
		}
		if (scsiCtrl == null) {
			throw new NullPointerException("No ParaVirtualSCSIController found.");
		} else {
			return scsiCtrl;
		}
	}

}
