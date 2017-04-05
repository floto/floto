package io.github.floto.core.virtualization.workstation;

import com.google.common.base.Throwables;
import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.VmDescription;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;

public class  WorkstationHypervisorService implements HypervisorService {
    ExternalProgram vmrun = ExternalProgram.create("vmrun", "VMware");
    ExternalProgram ovftool = ExternalProgram.create("ovftool", "VMware");
    private Logger log = LoggerFactory.getLogger(WorkstationHypervisorService.class);
    private File vmDirectory;
    private File cacheDirectory;

    public WorkstationHypervisorService(File flotoHome) {
        this.vmDirectory = new File(flotoHome, "vm");

        vmrun.setTimeout(Duration.ofMinutes(1));
        ovftool.setTimeout(Duration.ofHours(1));

        cacheDirectory = new File(flotoHome, "cache/vm");
        try {
            FileUtils.forceMkdir(cacheDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public void deployVm(URL url, VmDescription vmDescription) {
        log.info("deployVm '" + url.toString() + "' to '" + vmDescription.vmName + "'");

        String fileName = url.toString().substring(url.toString().lastIndexOf('/') + 1, url.toString().length());
        Path ovaCacheFile = Paths.get(cacheDirectory.toString(), fileName);
        if (!ovaCacheFile.toFile().exists()) {
            try {
                log.info("download '" + url.toString() + "' to '" + ovaCacheFile + "'");
                Files.copy(url.openStream(), ovaCacheFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("failed to download '" + url.toString() + "'", e);
                return;
            }
        } else {
            log.info("'" + ovaCacheFile + "' exists");
        }

        String vmxFile = getVmxPath(vmDescription.vmName);

        ovftool.run(ovaCacheFile.toString(), getVmxPath(vmDescription.vmName));

        //workaround for ovftool - if it creates unnecessary subdirectory depends on OS, so move the files one level up​
		File dir;
		if (System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0) {
			dir = new File(vmDirectory, vmDescription.vmName + "/" + vmDescription.vmName + ".vmwarevm");
		} else {
			dir = new File(vmDirectory, vmDescription.vmName + "/" + vmDescription.vmName);
		}
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            for (File file: files){

				try {
					FileUtils.rename(file, new File(vmDirectory, vmDescription.vmName + "/" + file.getName()));
				} catch (IOException e) {
					Throwables.propagate(e);
				}
			}
			try {
				FileUtils.deleteDirectory(dir);
			} catch (IOException e) {
				Throwables.propagate(e);
			}
		}
        

        Map<String, String> vmxConfiguration;
        try (InputStream input = new FileInputStream(vmxFile)) {
            vmxConfiguration = VmxUtils.readVmx(input);
            vmxConfiguration.put("numvcpus", String.valueOf(vmDescription.numberOfCores));
            vmxConfiguration.put("memsize", String.valueOf(vmDescription.memoryInMB));
            int adapterIndex = 0;
            for (String networkName : vmDescription.vmNetworks) {
                vmxConfiguration.put("ethernet" + adapterIndex + ".present", "TRUE");
                vmxConfiguration.put("ethernet" + adapterIndex + ".connectionType", "nat");
                vmxConfiguration.put("ethernet" + adapterIndex + ".virtualDev", "e1000");
                adapterIndex++;
            }

        } catch (IOException ex) {
            throw Throwables.propagate(ex);
        }
        try (OutputStream output = new FileOutputStream(vmxFile)) {
            VmxUtils.writeVmx(vmxConfiguration, output);
        } catch (IOException ex) {
            throw Throwables.propagate(ex);
        }

    }

    @Override
    public void exportVm(String vmName, String hostName, String path) {
        String vmxFile = getVmxPath(vmName);
        if(path.endsWith(".zip")) {
            try {
                // Give VMware time to delete lock file
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            File vmDirectory = new File(vmxFile).getParentFile();
            ZipUtil.pack(vmDirectory, new File(path));
        } else {
            ovftool.run(vmxFile, path);
        }
    }

    @Override
    public void deleteVm(String vmName) {
        try {
            String vmxPath = getVmxPath(vmName);
            try {
                vmrun.run("deleteVM", vmxPath);
            } catch (Exception ignored) {
                // may not exist
            }
            FileUtils.forceDelete(new File(vmxPath).getParentFile());
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @Override
    public VmDescription getVmDescription(String vmname) {
        return null;
    }

    @Override
    public List<VmDescription> getAllVms() {
        ArrayList<VmDescription> vmList = new ArrayList<>();
        for (File directory : vmDirectory.listFiles((java.io.FileFilter) DirectoryFileFilter.DIRECTORY)) {
            File vmxFile = new File(directory, directory.getName() + ".vmx");
            if (!vmxFile.exists()) {
                continue;
            }

            try (InputStream input = new FileInputStream(vmxFile)) {
                VmDescription description = new VmDescription();
                Properties prop = new Properties();
                prop.load(input);
                Map<String, String> vmxConfiguration = new HashMap<>();
                for (final String name : prop.stringPropertyNames()) {
                    vmxConfiguration.put(name, prop.getProperty(name).replaceAll("^\"|\"$", ""));
                }
                description.numberOfCores = Integer.valueOf(vmxConfiguration.get("cpuid.coresPerSocket"));
                description.vmName = directory.getName();

                vmList.add(description);
            } catch (IOException ex) {
                Throwables.propagate(ex);
            }
        }
        return vmList;
    }

    @Override
    public boolean isVmRunning(String vmname) {
        String vmList = vmrun.run("list");
        try {
            return vmList.contains(new File(getVmxPath(vmname)).getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startVm(String vmname) {
        vmrun.run("start", getVmxPath(vmname), "gui");
    }

    @Override
    public void stopVm(String vmname) {
        try {
            String vmxPath = getVmxPath(vmname);
            if (!new File(vmxPath).exists()) {
                return;
            }
            vmrun.run("stop", vmxPath, "soft");
        } catch (Throwable throwable) {
            if (!throwable.getMessage().contains("The virtual machine is not powered on")) {
                Throwables.propagate(throwable);
            }
        }
    }

	private void sleep(long ms){
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			log.debug("interrupted");
		}
	}

    private void run(String vmName, String cmd){
		vmrun.run("-gu", "user", "-gp", "user", "runScriptInGuest", getVmxPath(vmName), "/bin/bash", "sudo bash -c \"" + cmd + "\"");
	}

    @Override
    public void runInVm(String vmName, String cmd) {
        // TODO: quote escape cmd
        // TODO: log and get output

		int defaultRetries = 3;

		// if System Property vmrun = retry, on fail try to execute 3 times.
		int tries = System.getProperty("vmrunRetry") != null && System.getProperty("vmrunRetry").equalsIgnoreCase("true")? defaultRetries : 1;

		if (tries != 1){
			log.info("--vmrun-retry ist set, max: " + tries +" tries for vmrun.");
		}

		try {
			for (int i=1; i<=tries; i++) {
				try {
					run(vmName, cmd);
					break;
				} catch (Exception e1) {
					if (i == tries) {
						throw e1;
					} else {
						log.info("vmrun failed, " + (tries - i) + " tries left.");
						sleep(1000); // short pause before next try
					}
				}
			}
        } catch (Throwable throwable) {
            throw new RuntimeException("Unable to run command " + cmd, throwable);
        }
    }

	@Override
	public void runInVm(String vmname, String cmd, int timeout) {
		log.warn(WorkstationHypervisorService.class.getSimpleName() + " doesn't support timeout parameter.");
		runInVm(vmname, cmd);
	}

	@Override
    public void copyFileFromGuest(String vmName, String source, File target) {
        vmrun.run("-gu", "user", "-gp", "user", "CopyFileFromGuestToHost", getVmxPath(vmName), source, target.getAbsolutePath());
    }
    

    private String getVmxPath(String vmname) {
        return new File(vmDirectory, vmname + "/" + vmname + ".vmx").getAbsolutePath();
    }

    
}
