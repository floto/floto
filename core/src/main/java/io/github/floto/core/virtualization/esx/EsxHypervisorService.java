package io.github.floto.core.virtualization.esx;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.vmware.vim25.GuestProgramSpec;
import com.vmware.vim25.InvalidPowerState;
import com.vmware.vim25.NamePasswordAuthentication;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualDiskFlatVer2BackingInfo;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.FileManager;
import com.vmware.vim25.mo.GuestOperationsManager;
import com.vmware.vim25.mo.GuestProcessManager;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.core.virtualization.VmDescription.Disk;
import io.github.floto.dsl.model.EsxHypervisorDescription;

public class EsxHypervisorService implements HypervisorService {

    private Logger log = LoggerFactory.getLogger(EsxHypervisorService.class);

    EsxHypervisorDescription esxDesc;
    VirtualMachineManager vmManager;
    


    public EsxHypervisorService(EsxHypervisorDescription description) {
    	this.esxDesc = description;
        Preconditions.checkNotNull(description.networks, "networks");
        Preconditions.checkNotNull(description.esxHost, "esxHost");
        Preconditions.checkNotNull(description.vCenter, "esxHost");        
        Preconditions.checkNotNull(description.username, "username");
        Preconditions.checkNotNull(description.password, "password");
        Preconditions.checkNotNull(description.defaultDatastore, "datastore");
        
        vmManager = new VirtualMachineManager(esxDesc);
    }



    @Override
    public void deleteVm(String vmname) {
        try {
            log.info("deleteVm " + vmname);

            VirtualMachine vm = vmManager.getVm(vmname);

            if (vm == null) {
                log.error("No VM " + vmname + " found");
                return;
            }			

//            storeDataDisk(vm);

            Task task = vm.destroy_Task();
            log.info("Launching the VM destroy task. Please wait ...");

            if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
                log.info("VM got destroyed successfully.");
            } else {
                log.error("Failure -: VM cannot be destroyed");
            }

        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }

    }

    
    private void storeDataDisk(VirtualMachine vm){
    	// save the data disk, before deleting the vm
		try {
			VirtualDisk vd =  new VirtualDiskManager(vm).findHardDisk(10);
			if (vd == null) {
				log.error("data virtual disk not found.");
				return;
			}
			
			FileManager fileMgr = vmManager.getServiceInstance().getFileManager();
			if (fileMgr == null) {
				log.error("FileManager not available.");
				return;
			}

			String fileName = ((VirtualDiskFlatVer2BackingInfo) vd.getBacking()).getFileName();
//    					String fileName2 = fileName.substring(0, fileName.indexOf("]") + 1)
//    							+ " " + fileName.substring(fileName.lastIndexOf("/") + 1, fileName.length());
			String fileName2 = fileName.substring(0, fileName.indexOf("]") + 1)
					+ " " +vm.getName()+"_data.vmdk";

			Task mTask = fileMgr.moveDatastoreFile_Task(fileName, vmManager.getDatacenter(), fileName2, vmManager.getDatacenter(), true);

			if (mTask.waitForTask(200, 100) == Task.SUCCESS) {
				log.info("moved " + fileName + " to " + fileName2);
			} else {
				log.info("move of " + fileName + " to " + fileName2 + " failed.");
				return;
			}
		} catch (Exception e) {
			log.error("failed to save the data virtual disk", e);
		}
    }
    
    
    
    private boolean restoreDataDisk(VirtualMachine vm){
    	// save the data disk, before deleting the vm
		try {
			VirtualDisk vd =  new VirtualDiskManager(vm).findFirstHardDisk();
			if (vd == null) {
				log.error("data virtual disk not found.");
				return false;
			}
			
			FileManager fileMgr = vmManager.getServiceInstance().getFileManager();
			if (fileMgr == null) {
				log.error("FileManager not available.");
				return false;
			}

			String fileName = ((VirtualDiskFlatVer2BackingInfo) vd.getBacking()).getFileName();
//    					String fileName2 = fileName.substring(0, fileName.indexOf("]") + 1)
//    							+ " " + fileName.substring(fileName.lastIndexOf("/") + 1, fileName.length());
			String fileName2 = fileName.substring(0, fileName.indexOf("]") + 1)
					+ " " +vm.getName()+"_data.vmdk";
			String fileName3 = fileName.substring(0, fileName.indexOf("]") + 1)
					+ " " +vm.getName()+"/"+vm.getName()+"_data.vmdk";

			Task mTask = fileMgr.moveDatastoreFile_Task(fileName2, vmManager.getDatacenter(), fileName3, vmManager.getDatacenter(), true);

			if (mTask.waitForTask(200, 100) == Task.SUCCESS) {
				log.info("moved " + fileName2 + " to " + fileName3);
				return true;
			} else {
				log.info("move of " + fileName2 + " to " + fileName3 + " failed.");
				return false;
			}
		} catch (Exception e) {
			log.error("failed to restore the saved data virtual disk", e);
			return false;
		}
    }
    
    
    @Override
    public VmDescription getVmDescription(String vmname) {
        try {

            VirtualMachine vm = vmManager.getVm(vmname);
            if (vm == null) {
                throw new NullPointerException("vm not found");
            }
            boolean running = false;

            if (vm.getRuntime().getPowerState()
                    .equals(VirtualMachinePowerState.poweredOn)) {
                running = true;
            }
            VmDescription vmDesc = new VmDescription();
            vmDesc.vmName = vmname;
            vmDesc.running = running;
            vmDesc.numberOfCores = vm.getConfig().getHardware().getNumCPU();
            vmDesc.memoryInMB = vm.getConfig().getHardware().getMemoryMB();

            for (Network net : vm.getNetworks()) {
                vmDesc.vmNetworks.add(net.getName());
            }

            VirtualDevice[] devs = vm.getConfig().getHardware().getDevice();
            for (VirtualDevice dev : devs) {
                if (dev instanceof VirtualDisk) {
                    VirtualDisk vDisk = (VirtualDisk) dev;
                    Disk disk = new Disk();
                    disk.sizeInMB = vDisk.capacityInKB / 1024;
                    // TODO:
                    // disk.path = vDisk.get
                    // disk.thinProvisioned =
                }
            }

            return vmDesc;
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }

    }

    @Override
    public List<VmDescription> getAllVms() {
        try {
            List<VmDescription> list = new ArrayList<>();            
            for (ManagedEntity entity : vmManager.getVms()) {
                list.add(getVmDescription(entity.getName()));
            }
            return list;
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }

    }

    @Override
    public boolean isVmRunning(String vmname) {
        try {
            VirtualMachine vm = vmManager.getVm(vmname);
            if (vm == null) {
                return false;
            }
            boolean running = false;

            if (vm.getRuntime().getPowerState()
                    .equals(VirtualMachinePowerState.poweredOn)) {
                running = true;
            }

            return running;
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }

    }

    @Override
    public void startVm(String vmname) {
        if (isVmRunning(vmname)) {
            return;
        }
        try {

            VirtualMachine vm = vmManager.getVm(vmname);
            
            Task task = vm.powerOnVM_Task(null);
			if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
                log.info(vmname + " powered on");
            } else {
                log.error("failed to power on " + vmname);
            }

        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }
    }

    @Override
    public void stopVm(String vmname) {
        if (!isVmRunning(vmname)) {
            return;
        }
        try {
            VirtualMachine vm = vmManager.getVm(vmname);

            if (vm != null) {
                Task task = vm.powerOffVM_Task();
				if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
                    log.info(vmname + " powered off");
                } else {
                    log.error("failed to power off " + vmname);
                }
            }

        } catch (InvalidPowerState ignored) {
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }
    }

    @Override
    public void copyFileFromGuest(String vmName, String source, File ipFile) {

    }

    @Override
    public void exportVm(String vmname, String Path) {

    }

    @Override
    public void deployVm(URL vmUrl, VmDescription vmDesc) {
        try {
            log.info("deployVm " + vmUrl.toString() + " to " + vmDesc);

            String fileName = vmUrl.toString().substring(
                    vmUrl.toString().lastIndexOf('/') + 1,
                    vmUrl.toString().length());
            String fileNameWithoutExtn = fileName.substring(0,
                    fileName.lastIndexOf('.'));
            String templateVmName = fileNameWithoutExtn +"_"+ esxDesc.esxHost;

            if (vmManager.getVm(templateVmName) == null) {
                try {
                    vmManager.deployTemplate(vmUrl, templateVmName);
                } catch (Exception e) {
                    log.error("failed to deploy template", e);
                    return;
                }
            }
			
			vmManager.cloneVm(templateVmName, vmDesc, true);

            // reconfigure VM
            vmManager.reconfigureVm(vmDesc);

			VirtualMachine vm = vmManager.getVm(vmDesc.vmName);

			// try restore old data disk
//			if (!restoreDataDisk(vm)) {
//				// create new data disk
//				try {
//					VirtualDiskManager vmdm = new VirtualDiskManager(vm);
//					vmdm.createHardDisk(102400, VirtualDiskType.thin,
//							VirtualDiskMode.persistent, 10);
////					VirtualDisk vd = vmdm.findHardDisk(10);
////					log.info(((VirtualDiskFlatVer2BackingInfo)vd.getBacking()).getFileName());
//				} catch (Exception e) {
//					log.error("failed to create new virtual disk", e);
//				}
//			}
			

        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }

    }

	


    private boolean isGuestToolsAvailable(String vmname, long timeout)
            throws Exception {
        VirtualMachine vm = vmManager.getVm(vmname);

        if (vm == null) {
            return false;
        }

        long counter = 0;
        while (counter < timeout * 1000) {

            if ("guestToolsRunning".equals(vm.getGuest().toolsRunningStatus)) {
                if (counter > 0) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        log.error("", ie);
                    }
                }
                return true;
            } else {
                try {
                    Thread.sleep(1000);
                    counter += 1000;
                } catch (InterruptedException ie) {
                    log.error("", ie);
                }
            }
        }
        return false;
    }

    @Override
    public void runInVm(String vmname, String cmd) {
        log.info("runInVm(" + vmname + "," + cmd + ")");

        try {
            VirtualMachine vm = vmManager.getVm(vmname);

            GuestOperationsManager gom = vmManager.getServiceInstance().getGuestOperationsManager();

            isGuestToolsAvailable(vmname, 300);

            if (!"guestToolsRunning".equals(vm.getGuest().toolsRunningStatus)) {
                log.error("The VMware Tools is not running in the Guest OS on VM: "
                        + vm.getName());
                return;
            }

            // GuestAuthManager gam = gom.getAuthManager(vm);
            NamePasswordAuthentication npa = new NamePasswordAuthentication();
            npa.username = "user";
            npa.password = "user";

            GuestProgramSpec spec = new GuestProgramSpec();
            spec.programPath = "/";
            spec.arguments = "!#/bin/bash\n sudo bash -c \"" + cmd + "\"";

            GuestProcessManager gpm = gom.getProcessManager(vm);
            gpm.startProgramInGuest(npa, spec);
        } catch (Exception e) {
            log.error("failed to execute " + cmd + " on " + vmname, e);
        }

    }
	
}
