package io.github.floto.core.tasks;

import io.github.floto.core.FlotoService;
import io.github.floto.core.util.TemplateHelper;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.dsl.model.VmConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;

public class RedeployVmTask extends HypervisorTask<Void> {
    private Logger log = LoggerFactory.getLogger(RedeployVmTask.class);
    private TemplateHelper templateHelper = new TemplateHelper(RedeployVmTask.class, "templates");

    private FlotoService flotoService;
    private final String vmName;

    public RedeployVmTask(FlotoService flotoService, String vmName) {
        super(flotoService.getManifest(), vmName);
        this.flotoService = flotoService;
        this.vmName = vmName;
    }

    @Override
    public Void execute() throws Exception {
        VmConfiguration vmConfiguration = host.vmConfiguration;
        VmDescription vmDescription = new VmDescription();
        vmDescription.vmName = vmName;
        vmDescription.numberOfCores = vmConfiguration.numberOfCores;
        vmDescription.memoryInMB = vmConfiguration.memoryInMB;
        vmDescription.vmNetworks = new ArrayList<>(vmConfiguration.networks);


        log.info("Removing old VM");
        hypervisorService.stopVm(vmName);
        hypervisorService.deleteVm(vmName);

        log.info("Deploying VM {}", vmName);
        URL ovaUrl = new URL(vmConfiguration.ovaUrl);
        hypervisorService.deployVm(ovaUrl, vmDescription);

        log.info("Starting VM {}", vmName);
        hypervisorService.startVm(vmName);

        runPostDeploy();

        log.info("Post-deploy completed on host {}", vmName);
        return null;
    }

    private void runPostDeploy() {
        HostStepRunner hostStepRunner = new HostStepRunner(host, flotoService, manifest, hypervisorService, vmName);
        log.info("Running post-deploy on {}", vmName);
        hostStepRunner.run(host.postDeploySteps);
        log.info("Running reconfigure on {}", vmName);
        hostStepRunner.run(host.reconfigureSteps);
    }

}
