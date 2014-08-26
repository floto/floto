package io.github.floto.core.jobs;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.esx.EsxHypervisorService;
import io.github.floto.core.virtualization.virtualbox.VirtualboxHypervisorService;
import io.github.floto.core.virtualization.workstation.WorkstationHypervisorService;
import io.github.floto.dsl.model.EsxHypervisorDescription;
import io.github.floto.dsl.model.HypervisorDescription;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.dsl.model.VirtualboxHypervisorDescription;
import io.github.floto.dsl.model.WorkstationHypervisorDescription;

import java.io.File;

public abstract class HypervisorJob<T> extends HostJob<T> {

	protected final HypervisorService hypervisorService;

	public HypervisorJob(final Manifest manifest, final String hostName) {
		super(manifest, hostName);
		this.hypervisorService = createHypervisorService();
	}

	protected HypervisorService createHypervisorService() {
		final HypervisorDescription hypervisorDescription = host.vmConfiguration.hypervisor;
		if (hypervisorDescription instanceof WorkstationHypervisorDescription) {
			return new WorkstationHypervisorService(new File(
					System.getProperty("user.home") + "/.floto/vm"));
		} else if (hypervisorDescription instanceof EsxHypervisorDescription) {
			final EsxHypervisorDescription description = (EsxHypervisorDescription) hypervisorDescription;
			return new EsxHypervisorService(description, manifest.site.get(
					"domainName").asText());
		} else if (hypervisorDescription instanceof VirtualboxHypervisorDescription) {
			final VirtualboxHypervisorDescription description = (VirtualboxHypervisorDescription) hypervisorDescription;
			return new VirtualboxHypervisorService(description);
		} else {
			throw new IllegalArgumentException("Unknown hypervisor type: "
					+ hypervisorDescription.getClass().getName());
		}
	}

	public HypervisorService getHypervisorService() {
		return hypervisorService;
	}
}
