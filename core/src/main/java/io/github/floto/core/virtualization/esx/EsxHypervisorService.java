package io.github.floto.core.virtualization.esx;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.core.virtualization.VmDescription.Disk;
import io.github.floto.dsl.model.EsxHypervisorDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EsxHypervisorService implements HypervisorService {

    private Logger log = LoggerFactory.getLogger(EsxHypervisorService.class);
	static CopyOnWriteArrayList<String> templateList = new CopyOnWriteArrayList<>();
    EsxHypervisorDescription esxDesc;
    VirtualMachineManager vmManager;



    public EsxHypervisorService(EsxHypervisorDescription description, String domainName) {
    	this.esxDesc = description;
        Preconditions.checkNotNull(description.networks, "networks");
        Preconditions.checkNotNull(description.esxHost, "esxHost");
        Preconditions.checkNotNull(description.vCenter, "esxHost");
        Preconditions.checkNotNull(description.username, "username");
        Preconditions.checkNotNull(description.password, "password");
        Preconditions.checkNotNull(description.defaultDatastore, "datastore");
        vmManager = new VirtualMachineManager(esxDesc, domainName);
    }



    @Override
    public void deleteVm(String vmname) {
    	this.deleteVm(vmname, false);
    }

    private void deleteVm(String vmname, boolean purgeData) {
        try {
            log.info("Delete vm " + vmname);

            VirtualMachine vm = vmManager.getVm(vmname);

            if (vm == null) {
                log.error("Vm " + vmname + " not found");
                return;
            }

			VirtualDiskManager vdm =  new VirtualDiskManager(vm);

			for (VirtualDevice vd : vdm.getAllVirtualDevices()) {
				if (vd instanceof VirtualDisk) {
					VirtualDisk vDisk = (VirtualDisk)vd;
					VirtualDiskFlatVer2BackingInfo fileBacking = ((VirtualDiskFlatVer2BackingInfo)vDisk.getBacking());
					if (fileBacking.getDiskMode().equals(VirtualDiskMode.independent_persistent.toString())) {
						Task task = vdm.removeDevice(vd, purgeData);
						EsxUtils.waitForTask(task, "Remove virtual data disk " + fileBacking.fileName + " from " + vmname);
					}
				}
			}

			log.info("Launching the VM destroy task. Please wait ...");
            Task task = vm.destroy_Task();
			EsxUtils.waitForTask(task, "Destroy vm " + vmname);

        } catch (Throwable t) {
            throw Throwables.propagate(t);
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
                    Disk disk = new Disk(vmDesc);
                    disk.sizeInGB = vDisk.capacityInKB / 1024;
                    disk.path = ((VirtualDiskFlatVer2BackingInfo)vDisk.getBacking()).fileName;
                    disk.thinProvisioned = ((VirtualDiskFlatVer2BackingInfo) vDisk.getBacking()).thinProvisioned;
                    vmDesc.disks.add(disk);
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

            if (vm.getRuntime().getPowerState().equals(VirtualMachinePowerState.poweredOn)) {
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
			EsxUtils.waitForTask(task, "Power on " + vmname);

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
            int SHUTDOWN_GRACE_PERIOD = 20;
            VirtualMachine vm = vmManager.getVm(vmname);

            if (vm != null) {
                try {
                    vm.shutdownGuest();
                    for (int i = 0; i < SHUTDOWN_GRACE_PERIOD; i++) {
                        if (!isVmRunning(vmname)) {
                            log.warn("VM {} shutdown gracefully", vmname);
                            return;
                        }
                        Thread.sleep(1000);
                    }
                    log.warn("VM {} did not shutdown after {} seconds, terminating forcefully", vmname, SHUTDOWN_GRACE_PERIOD);
                } catch(Throwable throwable) {
                    log.warn("Error stopping VM {}", throwable);
                }
            }
            vm = vmManager.getVm(vmname);
            if (vm != null) {
                Task task = vm.powerOffVM_Task();
				EsxUtils.waitForTask(task, "Power off " + vmname);
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
		try {
			vmManager.exportVM(vmname, Path);
		} catch (Throwable t) {
			throw Throwables.propagate(t);
		}
    }

    @Override
    public void deployVm(URL vmUrl, VmDescription vmDesc) {
        try {
            log.info("Deploy vm " + vmUrl.toString() + " to " + vmDesc);

            String fileName = vmUrl.toString().substring(
                    vmUrl.toString().lastIndexOf('/') + 1,
                    vmUrl.toString().length());
            String fileNameWithoutExt = fileName.substring(0,
                    fileName.lastIndexOf('.'));
            String templateVmName = fileNameWithoutExt +"_"+ esxDesc.esxHost;


            if (templateList.addIfAbsent(templateVmName) && vmManager.getVm(templateVmName) == null) {
				try {
                    vmManager.deployTemplate(vmUrl, templateVmName);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deploy template " + vmUrl, e);
                } finally {
					templateList.remove(templateVmName);
				}
			} else {
				VirtualMachine vm = vmManager.getVm(templateVmName);
				if (vm == null || vm.getResourcePool() != null) {
					log.warn("Template seems to be in deployment right now - wait ...");
					long timeout = 600;
					for (int i=1; i<timeout; i+=1) {
						try {
							Thread.sleep(1000);
							if (i % 60 == 0) {
								log.info("Template seems to be still in deployment - wait ... " + i + "s");
							}
						} catch (InterruptedException ignored) {
						}
						vm = vmManager.getVm(templateVmName);
						if (vm != null && vm.getResourcePool() == null) {
							log.info("Template ready! go on with " + vmDesc.vmName);
							break;
						}
					}
					vm = vmManager.getVm(templateVmName);
					if (vm == null || vm.getResourcePool() != null) {
						throw new RuntimeException("Timeout! template " + templateVmName + " not ready! ");
					}
				}
			}

			vmManager.cloneVm(templateVmName, vmDesc, true);

            // reconfigure VM
            vmManager.reconfigureVm(vmDesc);

            //create or reattach data disk(s)
			VirtualMachine vm = vmManager.getVm(vmDesc.vmName);
			VirtualDiskManager vdm =  new VirtualDiskManager(vm);
			for (Disk disk : vmDesc.disks) {
				fileName = "[" + disk.datastore + "] " + vm.getName() + "_data" + disk.slot + ".vmdk";

				try {
		            ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
					Folder rootFolder = si.getRootFolder();
		            Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];

					EsxConnectionManager.getConnection(esxDesc).getVirtualDiskManager().queryVirtualDiskFragmentation(fileName, dc);
					log.info(fileName + " exists - will add it to virtual machine.");
					vdm.addVirtualDisk(disk, VirtualDiskMode.independent_persistent, disk.slot);
				} catch (Exception e) {
					log.info(fileName + " does not exist - will create new virtual disk.");
					vdm.createHardDisk(disk, VirtualDiskType.thin, VirtualDiskMode.independent_persistent, disk.slot);
				}
			}
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

            GuestOperationsManager gom = EsxConnectionManager.getConnection(esxDesc).getGuestOperationsManager();

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
            throw new RuntimeException("Failed to execute " + cmd + " on " + vmname, e);
        }

    }

}
