package io.github.floto.core.jobs;

import io.github.floto.core.FlotoService;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.core.virtualization.VmDescription.Disk;
import io.github.floto.dsl.model.DiskDescription;
import io.github.floto.dsl.model.VmConfiguration;

import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedeployVmJob extends HypervisorJob<Void> {
    private Logger log = LoggerFactory.getLogger(RedeployVmJob.class);
    private FlotoService flotoService;
	static CopyOnWriteArrayList<String> deployVmList = new CopyOnWriteArrayList<>();

    public RedeployVmJob(FlotoService flotoService, String vmName) {
        super(flotoService.getManifest(), vmName, flotoService.getFlotoHome());
        this.flotoService = flotoService;
    }

    @Override
    public Void execute() throws Exception {
		if (!deployVmList.addIfAbsent(host.name)) {
			throw new RuntimeException(host.name + " already in deployment");
		}

		try {

			VmConfiguration vmConfiguration = host.vmConfiguration;
			VmDescription vmDescription = new VmDescription();
			vmDescription.vmName = host.vmConfiguration.vmName!=null?host.vmConfiguration.vmName:host.name;
			vmDescription.numberOfCores = vmConfiguration.numberOfCores;
			vmDescription.memoryInMB = vmConfiguration.memoryInMB;
			vmDescription.vmNetworks = new ArrayList<>(vmConfiguration.networks);
			for (DiskDescription diskDesc : vmConfiguration.disks) {
				Disk disk = new Disk(vmDescription);
				disk.sizeInGB = diskDesc.sizeInGB;
				disk.datastore = diskDesc.datastore;
				disk.slot = diskDesc.slot;
				disk.mountpoint = diskDesc.mountpoint;
				vmDescription.disks.add(disk);
			}

			log.info("Removing old VM");
			hypervisorService.stopVm(vmDescription.vmName);
			hypervisorService.deleteVm(vmDescription.vmName);

			log.info("Deploying VM {}", vmDescription.vmName);
			URL ovaUrl = this.flotoService.getTemplateUrl(this.host);
			log.info("Will use template=" + ovaUrl);
			hypervisorService.deployVm(ovaUrl, vmDescription);

			log.info("Starting VM {}", vmDescription.vmName);
			hypervisorService.startVm(vmDescription.vmName);

			runPostDeploy();

			log.info("Post-deploy completed on host {} ({})", vmDescription.vmName, host.name);

		} finally {
			deployVmList.remove(host.name);
		}

		return null;
    }

    private void runPostDeploy() {
        HostStepRunner hostStepRunner = new HostStepRunner(host, flotoService, manifest, hypervisorService);
        log.info("Running post-deploy on {}", host.vmConfiguration.vmName);
        hostStepRunner.run(host.postDeploySteps);
        log.info("Running reconfigure on {}", host.vmConfiguration.vmName);
        hostStepRunner.run(host.reconfigureSteps);
    }

}
