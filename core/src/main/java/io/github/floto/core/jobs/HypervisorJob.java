package io.github.floto.core.jobs;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.baremetal.BareMetalHypervisorService;
import io.github.floto.core.virtualization.esx.EsxHypervisorService;
import io.github.floto.core.virtualization.virtualbox.VirtualboxHypervisorService;
import io.github.floto.core.virtualization.workstation.WorkstationHypervisorService;
import io.github.floto.dsl.model.*;

import java.io.File;

public abstract class HypervisorJob<T> extends HostJob<T> {

	protected final HypervisorService hypervisorService;

	public HypervisorJob(final Manifest manifest, final String hostName, File flotoHome) {
		super(manifest, hostName);
		this.hypervisorService = createHypervisorService(flotoHome);
	}

	protected HypervisorService createHypervisorService(File flotoHome) {
		final HypervisorDescription hypervisorDescription = host.vmConfiguration.hypervisor;
		if (hypervisorDescription instanceof WorkstationHypervisorDescription) {
			return new WorkstationHypervisorService(flotoHome);
		} else if (hypervisorDescription instanceof EsxHypervisorDescription) {
			final EsxHypervisorDescription description = (EsxHypervisorDescription) hypervisorDescription;
			return new EsxHypervisorService(description);
		} else if (hypervisorDescription instanceof VirtualboxHypervisorDescription) {
			final VirtualboxHypervisorDescription description = (VirtualboxHypervisorDescription) hypervisorDescription;
			return new VirtualboxHypervisorService(description, flotoHome);
        } else if (hypervisorDescription instanceof BareMetalHypervisorDescription) {
            final BareMetalHypervisorDescription description = (BareMetalHypervisorDescription) hypervisorDescription;
            return new BareMetalHypervisorService(description);
		} else {
			throw new IllegalArgumentException("Unknown hypervisor type: "
					+ hypervisorDescription.getClass().getName());
		}
	}

	public HypervisorService getHypervisorService() {
		return hypervisorService;
	}
}
