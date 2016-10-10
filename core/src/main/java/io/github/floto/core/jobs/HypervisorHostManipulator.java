package io.github.floto.core.jobs;

import io.github.floto.core.virtualization.HypervisorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class HypervisorHostManipulator implements HostManipulator {

    private HypervisorService hypervisorService;
    private String vmName;

	private final Logger log = LoggerFactory.getLogger(HypervisorHostManipulator.class);

	public HypervisorHostManipulator(HypervisorService hypervisorService, String vmName) {
        this.hypervisorService = hypervisorService;
        this.vmName = vmName;
    }

    @Override
    public void run(String command) {
    	hypervisorService.runInVm(vmName, command);
    }

	@Override
	public void run(String command, int timeout) {
		log.warn(HypervisorHostManipulator.class.getSimpleName() + " doesn't support timeout parameter.");
		run(command);
	}

    @Override
    public void writeToVm(String content, String destination) {
        String command = "cat << 'EOFEOFEOF' > "+destination+"\n" + content + "\nEOFEOFEOF\n";
        hypervisorService.runInVm(vmName, command);

    }

	@Override
	public void copyToVm(File file, String destination) {
		throw new UnsupportedOperationException("copy To VM not supported before determineIp was called");
	}

}
