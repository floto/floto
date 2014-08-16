package io.github.floto.core.virtualization.virtualbox;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.core.virtualization.workstation.ExternalProgram;
import io.github.floto.dsl.model.VirtualboxHypervisorDescription;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;

import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.net.PercentEscaper;

public class VirtualboxHypervisorService implements HypervisorService {
	
	public static final int NUM_ATTEMPTS = 9;
	public static final long SLEEP_INTERVAL = 2000L;
	
    private Logger log = LoggerFactory.getLogger(VirtualboxHypervisorService.class);

    private File cacheDirectory;
    private File vmDirectory;
    private ExternalProgram vBoxManage = ExternalProgram.create("VBoxManage", "Oracle/VirtualBox");

    public VirtualboxHypervisorService(VirtualboxHypervisorDescription description) {
    }

    private final PercentEscaper escaper;

    {
        try {
            cacheDirectory = new File(System.getProperty("user.home") + "/.floto/virtualbox/cache");
            FileUtils.forceMkdir(cacheDirectory);

            vmDirectory = new File(System.getProperty("user.home") + "/.floto/virtualbox/vms");
            FileUtils.forceMkdir(vmDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }

        escaper = new PercentEscaper(".-_", false);


    }


    @Override
    public void deployVm(URL vmUrl, VmDescription desc) {
        File cachedFile = new File(cacheDirectory, escaper.escape(vmUrl.toExternalForm()));
        try {
            if (!cachedFile.exists()) {
                Path downloadFile = Paths.get(cachedFile.getAbsolutePath() + "." + UUID.randomUUID().toString());
                Files.copy(vmUrl.openStream(), downloadFile, StandardCopyOption.REPLACE_EXISTING);
                Files.move(downloadFile, cachedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                log.info("'" + cachedFile + "' exists");
            }
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }
        if(vmUrl.toExternalForm().endsWith(".iso")) {
            deployFromIso(cachedFile, desc);
        } else {
            deployFromImage(cachedFile, desc);
        }

    }

    private void deployFromImage(File cachedFile, VmDescription desc) {
        // Import VM
        vBoxManage.run("import", cachedFile.getAbsolutePath(), "--vsys", "0", "--vmname", desc.vmName);

        configureVm(desc);
    }

    private void deployFromIso(File cachedFile, VmDescription desc) {
        // Create VM
        vBoxManage.run("createvm", "--name", desc.vmName, "--register", "--basefolder", vmDirectory.getAbsolutePath());

        // Create Storage Controller
        vBoxManage.run("storagectl", desc.vmName, "--name", "sata", "--add", "sata", "--portcount", "4");

        // Attach ISO
        vBoxManage.run("storageattach", desc.vmName, "--storagectl", "sata", "--port", "1", "--type", "dvddrive", "--medium", cachedFile.getAbsolutePath());

        // create and attach disk
        String diskPath = new File(getVmDirectory(desc.vmName), "disk1.vdi").getAbsolutePath();
        vBoxManage.run("createhd", "--filename", diskPath, "--format", "vdi", "--size", String.valueOf(20 * 1024));
        vBoxManage.run("storageattach", desc.vmName, "--storagectl", "sata", "--port", "0", "--type", "hdd", "--medium", diskPath);

        configureVm(desc);

        // Setup networking
        vBoxManage.run("modifyvm", desc.vmName, "--nic1", "hostonly", "--hostonlyadapter1", "vboxnet0");
    }

    private void configureVm(VmDescription desc) {
        // Set VM options
        vBoxManage.run("modifyvm", desc.vmName, "--memory", String.valueOf(desc.memoryInMB), "--cpus", String.valueOf(desc.numberOfCores), "--ostype", "Linux26_64", "--acpi", "on", "--ioapic", "on", "--vram", "32");

    }

    private File getVmDirectory(String vmName) {
        return new File(vmDirectory, vmName);
    }

    @Override
    public void exportVm(String vmname, String Path) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void deleteVm(String vmname) {
        try {
            vBoxManage.run("unregistervm", vmname, "--delete");
        } catch (Throwable throwable) {
            if (throwable.getMessage().contains("VBOX_E_OBJECT_NOT_FOUND")) {
                // Not found, ok
                return;
            }
            throw Throwables.propagate(throwable);
        }
    }

    @Override
    public VmDescription getVmDescription(String vmname) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<VmDescription> getAllVms() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isVmRunning(String vmName) {
        String result;
        try {
            for (int i = 0; ; i++) {
                try {
                    result = vBoxManage.run("showvminfo", vmName, "--machinereadable");
                    break;
                } catch (Throwable throwable) {
                    if (throwable.getMessage().contains("E_ACCESSDENIED")) {
                        if (i < 100) {
                            // denied, try again
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw Throwables.propagate(e);
                            }
                            continue;
                        }
                    }
                    throw Throwables.propagate(throwable);
                }
            }
        } catch (Throwable throwable) {
            if (throwable.getMessage().contains("VBOX_E_OBJECT_NOT_FOUND")) {
                // Not found
                return false;
            }
            throw Throwables.propagate(throwable);
        }
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(result));
            Map<String, String> vmInfo = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                vmInfo.put(key, value);
            }
            return "running".equals(vmInfo.get("VMState"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void startVm(String vmname) {
        try {
            if (isVmRunning(vmname)) {
                return;
            }
            try {
                vBoxManage.run("startvm", vmname);
            } catch (Throwable ignored) {

            }
            for (int i = 0; i < 100; i++) {
                if (isVmRunning(vmname)) {
                    return;
                }
                Thread.sleep(1000);
            }
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }
    }

    @Override
    public void stopVm(String vmname) {
        try {
            if (!isVmRunning(vmname)) {
                return;
            }
            try {
                vBoxManage.run("controlvm", vmname, "acpipowerbutton");
            } catch (Throwable ignored) {

            }
            for (int i = 0; i < 100; i++) {
                if (!isVmRunning(vmname)) {
                    return;
                }
                Thread.sleep(1000);
            }
            try {
                vBoxManage.run("controlvm", vmname, " poweroff");
            } catch (Throwable ignored) {

            }
            for (int i = 0; i < 100; i++) {
                if (!isVmRunning(vmname)) {
                    return;
                }
                Thread.sleep(1000);
            }
            throw new RuntimeException("Unable to stop vm");
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }
    }
    
    @Override
    public void runInVm(String vmname, String cmd) {
    	try {
	    	this.runCommand(() -> vBoxManage.run("guestcontrol", vmname, "execute", 
	    			"--username", "user", 
	    			"--password", "user", 
	    			"--image", "/usr/bin/sudo",
	    			"--wait-exit", 
	    			"--wait-stdout",
	    			"--wait-stderr",
	    			"--verbose",
	    			"--",
	    			"/bin/bash",
	    			"-c",
	    			cmd));
    	}
    	catch(Exception ex) {
    		throw new RuntimeException("Unable to run command " + cmd, ex);
    	}
    }

    @Override
    public void copyFileFromGuest(String vmName, String source, File destination) {
    	try {
	    	this.runCommand(() -> vBoxManage.run("guestcontrol", vmName, "copyfrom",
	    			source,
	    			destination.getAbsolutePath(),
	    			"--username", "user", 
	    			"--password", "user", 
	    			"--verbose"));
    	}
    	catch(Exception ex) {
    		throw new RuntimeException(String.format("Could not copy file from=%s to=%s", source, destination));
    	}
    }
    
    private void runCommand(Supplier<?> function) {
    	for(int i = 0; i <= NUM_ATTEMPTS; i++) {
	    	 try {
	    		 function.get();
	    		 return;
	    	 } catch (Throwable throwable) {
	    		 if(i < NUM_ATTEMPTS) {
	    			 try {
	    				 log.info("guest execution service may not be ready. Will wait a bit...");
	    				 Thread.sleep(SLEEP_INTERVAL);
	    			 }
	    			 catch(InterruptedException iex) {
	    				 log.warn("Thread interupted");
	    			 }
	    		 }
	    		 else {
	    			 throw throwable;
	    		 }
	         }
    	}
    }
}
