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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.net.PercentEscaper;

public class VirtualboxHypervisorService implements HypervisorService {

	public static final int NUM_ATTEMPTS = 9;
	public static final long SLEEP_INTERVAL = 2000L;

	private final Logger log = LoggerFactory
			.getLogger(VirtualboxHypervisorService.class);

	private File cacheDirectory;
	private File vmDirectory;
	private final ExternalProgram vBoxManage = ExternalProgram.create(
			"VBoxManage", "Oracle/VirtualBox");

	public VirtualboxHypervisorService(
			final VirtualboxHypervisorDescription description) {
	}

	private final PercentEscaper escaper;

	{
		try {
			cacheDirectory = new File(System.getProperty("user.home")
					+ "/.floto/virtualbox/cache");
			FileUtils.forceMkdir(cacheDirectory);

			vmDirectory = new File(System.getProperty("user.home")
					+ "/.floto/virtualbox/vms");
			FileUtils.forceMkdir(vmDirectory);
		} catch (final IOException e) {
			Throwables.propagate(e);
		}

		escaper = new PercentEscaper(".-_", false);

	}

	@Override
	public void deployVm(final URL vmUrl, final VmDescription desc) {
		final File cachedFile = new File(cacheDirectory, escaper.escape(vmUrl
				.toExternalForm()));
		try {
			if (!cachedFile.exists()) {
				final Path downloadFile = Paths
						.get(cachedFile.getAbsolutePath() + "."
								+ UUID.randomUUID().toString());
				Files.copy(vmUrl.openStream(), downloadFile,
						StandardCopyOption.REPLACE_EXISTING);
				Files.move(downloadFile, cachedFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			} else {
				log.info("'" + cachedFile + "' exists");
			}
		} catch (final Throwable throwable) {
			Throwables.propagate(throwable);
		}

		if (vmUrl.toExternalForm().endsWith(".iso")) {
			deployFromIso(cachedFile, desc);
		} else {
			deployFromImage(cachedFile, desc);
		}

	}

	private void deployFromImage(final File cachedFile, final VmDescription desc) {
		// Import VM
		vBoxManage.run("import", cachedFile.getAbsolutePath(), "--vsys", "0",
				"--vmname", desc.vmName);

		configureVm(desc);
	}

	private void deployFromIso(final File cachedFile, final VmDescription desc) {
		// Create VM
		vBoxManage.run("createvm", "--name", desc.vmName, "--register",
				"--basefolder", vmDirectory.getAbsolutePath());

		// Create Storage Controller
		vBoxManage.run("storagectl", desc.vmName, "--name", "sata", "--add",
				"sata", "--portcount", "4");

		// Attach ISO
		vBoxManage.run("storageattach", desc.vmName, "--storagectl", "sata",
				"--port", "1", "--type", "dvddrive", "--medium",
				cachedFile.getAbsolutePath());

		// create and attach disk
		final String diskPath = new File(getVmDirectory(desc.vmName),
				"disk1.vdi").getAbsolutePath();
		vBoxManage.run("createhd", "--filename", diskPath, "--format", "vdi",
				"--size", String.valueOf(20 * 1024));
		vBoxManage.run("storageattach", desc.vmName, "--storagectl", "sata",
				"--port", "0", "--type", "hdd", "--medium", diskPath);

		configureVm(desc);
	}

	private void configureVm(final VmDescription desc) {
		// Set VM options
		vBoxManage.run("modifyvm", desc.vmName, "--memory",
				String.valueOf(desc.memoryInMB), "--cpus",
				String.valueOf(desc.numberOfCores), "--ostype", "Linux26_64",
				"--acpi", "on", "--ioapic", "on", "--vram", "32");

		this.setupNetworking(desc);
	}

	private void setupNetworking(final VmDescription desc) {
		// Setup networking
		vBoxManage.run("modifyvm", desc.vmName, "--nic1", "hostonly", "--hostonlyadapter1", "vboxnet0");
		vBoxManage.run("modifyvm", desc.vmName, "--nic2", "hostonly", "--hostonlyadapter2", "vboxnet1");
		//vBoxManage.run("modifyvm", desc.vmName, "--nic3", "nat");
	}

	private File getVmDirectory(final String vmName) {
		return new File(vmDirectory, vmName);
	}

	@Override
	public void exportVm(final String vmname, final String Path) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void deleteVm(final String vmname) {
		try {
			vBoxManage.run("unregistervm", vmname, "--delete");
		} catch (final Throwable throwable) {
			if (throwable.getMessage().contains("VBOX_E_OBJECT_NOT_FOUND")) {
				// Not found, ok
				return;
			}
			throw Throwables.propagate(throwable);
		}
	}

	@Override
	public VmDescription getVmDescription(final String vmname) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public List<VmDescription> getAllVms() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean isVmRunning(final String vmName) {
		String result;
		try {
			for (int i = 0;; i++) {
				try {
					result = vBoxManage.run("showvminfo", vmName,
							"--machinereadable");
					break;
				} catch (final Throwable throwable) {
					if (throwable.getMessage().contains("E_ACCESSDENIED")) {
						if (i < 100) {
							// denied, try again
							try {
								Thread.sleep(100);
							} catch (final InterruptedException e) {
								throw Throwables.propagate(e);
							}
							continue;
						}
					}
					throw Throwables.propagate(throwable);
				}
			}
		} catch (final Throwable throwable) {
			if (throwable.getMessage().contains("VBOX_E_OBJECT_NOT_FOUND")) {
				// Not found
				return false;
			}
			throw Throwables.propagate(throwable);
		}
		try {
			final Properties properties = new Properties();
			properties.load(new StringReader(result));
			final Map<String, String> vmInfo = new HashMap<>();
			for (final String key : properties.stringPropertyNames()) {
				String value = properties.getProperty(key);
				if (value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				vmInfo.put(key, value);
			}
			return "running".equals(vmInfo.get("VMState"));
		} catch (final IOException e) {
			throw Throwables.propagate(e);
		}
	}

	@Override
	public void startVm(final String vmname) {
		try {
			if (isVmRunning(vmname)) {
				return;
			}
			try {
				vBoxManage.run("startvm", vmname);
			} catch (final Throwable ignored) {

			}
			for (int i = 0; i < 100; i++) {
				if (isVmRunning(vmname)) {
					return;
				}
				Thread.sleep(1000);
			}
		} catch (final Throwable throwable) {
			Throwables.propagate(throwable);
		}
	}

	@Override
	public void stopVm(final String vmname) {
		try {
			if (!isVmRunning(vmname)) {
				return;
			}
			try {
				vBoxManage.run("controlvm", vmname, "acpipowerbutton");
			} catch (final Throwable ignored) {

			}
			for (int i = 0; i < 100; i++) {
				if (!isVmRunning(vmname)) {
					return;
				}
				Thread.sleep(1000);
			}
			try {
				vBoxManage.run("controlvm", vmname, " poweroff");
			} catch (final Throwable ignored) {

			}
			for (int i = 0; i < 100; i++) {
				if (!isVmRunning(vmname)) {
					return;
				}
				Thread.sleep(1000);
			}
			throw new RuntimeException("Unable to stop vm");
		} catch (final Throwable throwable) {
			Throwables.propagate(throwable);
		}
	}

	@Override
	public void runInVm(final String vmname, final String cmd) {
		try {
			this.runCommand(() -> vBoxManage.run("guestcontrol", vmname,
					"execute", "--username", "user", "--password", "user",
					"--image", "/usr/bin/sudo", "--wait-exit", "--wait-stdout",
					"--wait-stderr", "--verbose", "--", "/bin/bash", "-c", cmd));
		} catch (final Exception ex) {
			throw new RuntimeException("Unable to run command " + cmd, ex);
		}
	}

	@Override
	public void copyFileFromGuest(final String vmName, final String source,
			final File destination) {
		try {
			this.runCommand(() -> vBoxManage.run("guestcontrol", vmName,
					"copyfrom", source, destination.getAbsolutePath(),
					"--username", "user", "--password", "user", "--verbose"));
		} catch (final Exception ex) {
			throw new RuntimeException(String.format(
					"Could not copy file from=%s to=%s", source, destination));
		}
	}

	private void runCommand(final Supplier<?> function) {
		for (int i = 0; i <= NUM_ATTEMPTS; i++) {
			try {
				function.get();
				return;
			} catch (final Throwable throwable) {
				if (!ExceptionUtils.getStackTrace(throwable).contains(
						"The guest execution service is not ready")) {
					throw throwable;
				}

				if (i < NUM_ATTEMPTS) {
					try {
						log.info("guest execution service may not be ready. Will wait a bit...");
						Thread.sleep(SLEEP_INTERVAL);
					} catch (final InterruptedException iex) {
						log.warn("Thread interupted");
					}
				} else {
					throw throwable;
				}
			}
		}
	}
}
