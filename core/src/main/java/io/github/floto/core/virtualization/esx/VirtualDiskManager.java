package io.github.floto.core.virtualization.esx;

import com.vmware.vim25.VirtualController;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecFileOperation;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualDiskMode;
import com.vmware.vim25.VirtualDiskType;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mox.VirtualMachineDeviceManager;

public class VirtualDiskManager extends VirtualMachineDeviceManager {
	private VirtualMachine vm;

	public VirtualDiskManager(VirtualMachine vm) {
		super(vm);
		this.vm = vm;
	}

	public VirtualMachine getVM() {
		return this.vm;
	}

	public void createHardDisk(int diskSizeMB, VirtualDiskType type, VirtualDiskMode mode, int unitNumber) throws Exception {
		
		VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();
		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();

		VirtualDiskFlatVer2BackingInfo diskfileBacking = new VirtualDiskFlatVer2BackingInfo();
		diskfileBacking.setFileName("");
		diskfileBacking.setDiskMode(mode.toString());
		diskfileBacking.setThinProvisioned(type == VirtualDiskType.thin);

		VirtualSCSIController scsiController = getFirstAvailableController(VirtualSCSIController.class);
		VirtualDisk disk = new VirtualDisk();
		disk.setControllerKey(scsiController.key);
		disk.setUnitNumber(unitNumber);
		disk.setBacking(diskfileBacking);
		disk.setCapacityInKB(1024 * diskSizeMB);
		disk.setKey(-1);
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.create);
		diskSpec.setDevice(disk);
		VirtualDeviceConfigSpec[] vdiskSpecArray = { diskSpec };

		vmConfigSpec.setDeviceChange(vdiskSpecArray);
		Task task = vm.reconfigVM_Task(vmConfigSpec);

		task.waitForTask(200, 100);
	}


	public void removeHardDisk(VirtualDisk virtualDisk, boolean destroyBacking) throws Exception {
		
		VirtualMachineConfigSpec vmConfigSpec = new VirtualMachineConfigSpec();

		VirtualDeviceConfigSpec diskSpec = new VirtualDeviceConfigSpec();
		diskSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
		diskSpec.setFileOperation(VirtualDeviceConfigSpecFileOperation.destroy);
		diskSpec.setDevice(virtualDisk);

		VirtualDeviceConfigSpec[] vdiskSpecArray = { diskSpec };
		vmConfigSpec.setDeviceChange(vdiskSpecArray);

		Task task = vm.reconfigVM_Task(vmConfigSpec);
		task.waitForTask(200, 100);
	}
	
	
	
	public VirtualDisk findHardDisk(Integer unitNumber) {
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

	public VirtualDisk findFirstHardDisk() {
		VirtualDevice[] devices = getAllVirtualDevices();

		for (int i = 0; i < devices.length; i++) {
			if (devices[i] instanceof VirtualDisk) {
				return (VirtualDisk) devices[i];
			}
		}
		return null;
	}
	
	private <T extends VirtualController> T getFirstAvailableController(
			Class<T> clazz) {
		VirtualController vc = createControllerInstance(clazz);
		int maxNodes = getMaxNodesPerControllerOfType(vc);

		for (T controller : getVirtualDevicesOfType(clazz)) {
			// Check if controller can accept addition of new devices.
			if (controller.device == null
					|| controller.device.length < maxNodes) {
				return controller;
			}
		}
		return null;
	}

	private static int getMaxNodesPerControllerOfType(VirtualController controller) {
		int count = 0;

		if (VirtualSCSIController.class.isInstance(controller)) {
			// The actual device nodes of SCSI controller are 16
			// but one of them is reserved for the controller itself
			// so this means that the maximum free nodes are 15.
			count = 16;
		} else if (VirtualIDEController.class.isInstance(controller)) {
			count = 2;
		} else {
			throw new RuntimeException("Unknown controller type - "
					+ controller.getDeviceInfo().getLabel());
		}
		return count;
	}

	private <T extends VirtualController> VirtualController createControllerInstance(Class<T> clazz) {
		VirtualController vc = null;
		try {
			vc = (T) clazz.newInstance();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return vc;
	}

}