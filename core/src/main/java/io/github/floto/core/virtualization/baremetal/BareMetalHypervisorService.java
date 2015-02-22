package io.github.floto.core.virtualization.baremetal;

import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.dsl.model.BareMetalHypervisorDescription;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class BareMetalHypervisorService implements HypervisorService {
    public BareMetalHypervisorService(BareMetalHypervisorDescription description) {
    }

    @Override
    public void deployVm(URL vmUrl, VmDescription desc) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public void exportVm(String vmname, String Path) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public void deleteVm(String vmname) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public VmDescription getVmDescription(String vmname) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public List<VmDescription> getAllVms() {
        return Collections.emptyList();
    }

    @Override
    public boolean isVmRunning(String vmname) {
        return false;
    }

    @Override
    public void startVm(String vmname) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public void stopVm(String vmname) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public void runInVm(String vmname, String cmd) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }

    @Override
    public void copyFileFromGuest(String vmName, String source, File destination) {
        throw new UnsupportedOperationException("Not supported on bare metal systems");
    }
}
