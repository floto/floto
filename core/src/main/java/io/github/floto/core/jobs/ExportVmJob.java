package io.github.floto.core.jobs;

import io.github.floto.core.FlotoService;
import io.github.floto.util.GitHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ExportVmJob extends HypervisorJob<Void> {
	private Logger log = LoggerFactory.getLogger(ExportVmJob.class);
	private FlotoService flotoService;

	public ExportVmJob(FlotoService flotoService, String vmName) {
		super(flotoService.getManifest(), vmName);
		this.flotoService = flotoService;
	}

	@Override
	public Void execute() throws Exception {
		log.info("Exporting vm {}", host.name);

		File rootDefinitionFile = new File(flotoService.getManifest().rootFile);
		log.info("Root definition file: {}", rootDefinitionFile);
		String gitDescription = new GitHelper(rootDefinitionFile.getParentFile()).describe();
		log.info("Git Description {}", gitDescription);

		// TODO: delete intermediate images?
		// TODO: Zerofill disk?

		// Stop VM
		hypervisorService.stopVm(host.name);

		// HACK: Sleep a bit to give vmware workstation time to clean up its lock file
		Thread.sleep(1000);

		// Export Image
		String exportName = host.exportName;
		if(exportName == null) {
			exportName = host.name + "_" + gitDescription + ".ova";
		}

		File exportFile = new File("vm/" + exportName);
		FileUtils.forceMkdir(exportFile.getParentFile());
		hypervisorService.exportVm(host.name, exportFile.getAbsolutePath());

		log.info("Exported to: {}", exportName);

		return null;
	}
}
