package io.github.floto.core.tasks;

import io.github.floto.core.virtualization.HypervisorService;

public class HypervisorHostManipulator implements HostManipulator {

    private HypervisorService hypervisorService;
    private String vmName;

    public HypervisorHostManipulator(HypervisorService hypervisorService, String vmName) {
        this.hypervisorService = hypervisorService;
        this.vmName = vmName;
    }

    @Override
    public void run(String command) {
        hypervisorService.runInVm(vmName, command);
    }

    @Override
    public void writeToVm(String content, String destination) {
        String command = "cat << 'EOFEOFEOF' > "+destination+"\n" + content + "\nEOFEOFEOF\n";
        hypervisorService.runInVm(vmName, command);

    }
}
