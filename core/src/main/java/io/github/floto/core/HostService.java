package io.github.floto.core;

import com.google.common.base.Throwables;
import io.github.floto.core.jobs.*;
import io.github.floto.core.virtualization.HypervisorService;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class HostService {
    private Logger log = LoggerFactory.getLogger(HostService.class);
    private JobRunner jobRunner = new JobRunner();
    private FlotoService flotoService;

    public HostService(FlotoService flotoService) {
        this.flotoService = flotoService;
    }

    public Map<String, String> getHostStates() {
        HashMap<String, String> states = new HashMap<>();
        Manifest manifest = getManifest();
        for (Host host : manifest.hosts) {
            boolean running;
            String state = "unknown";
            try {
                running = runHypervisorTask(host.name, HypervisorService::isVmRunning);
                if (running) {
                    state = "running";
                } else {
                    state = "stopped";
                }
            } catch (Throwable e) {
                // ignored
            }
            states.put(host.name, state);
        }
        return states;
    }

    public void startVm(String vmName) {
        runHypervisorTask(vmName, HypervisorService::startVm);
        reconfigure(vmName);
    }

    public void reconfigureVms() {
        new Thread("Host reconfiguration") {
            @Override
            public void run() {
                try {
                    getManifest().hosts.forEach(host -> {
                        try {
                            reconfigure(host.name);
                        } catch (Throwable throwable) {
                            log.warn("Could not reconfigure host {}", host.name, throwable);
                        }
                    });
                } catch (Throwable t) {
                    log.error("Configuring hosts", t);
                }
            }
        }.start();
    }

    public void stopVm(String vmName) {
        runHypervisorTask(vmName, HypervisorService::stopVm);
    }

    public void redeployVm(String vmName) {
        runTask(new RedeployVmJob(flotoService, vmName));
    }


    public void deleteVm(String vmName) {
        runHypervisorTask(vmName, HypervisorService::deleteVm);
    }

    public Manifest getManifest() {
        return flotoService.getManifest();
    }

    private <T> T runTask(Job<T> job) {
        return jobRunner.runJob(job);
    }

    private void runHypervisorTask(String vmName, BiConsumer<HypervisorService, String> method) {
        runTask(new HypervisorJob<Object>(flotoService.getManifest(), vmName) {
            @Override
            public Object execute() throws Exception {
                try {
                    method.accept(hypervisorService, vmName);
                    return null;
                } catch (Throwable throwable) {
                    throw Throwables.propagate(throwable);
                }
            }
        });
    }

    private <T> T runHypervisorTask(String vmName, BiFunction<HypervisorService, String, T> method) {
        return runTask(new HypervisorJob<T>(flotoService.getManifest(), vmName) {
            @Override
            public T execute() throws Exception {
                try {
                    return method.apply(hypervisorService, vmName);
                } catch (Throwable throwable) {
                    throw Throwables.propagate(throwable);
                }
            }
        });
    }

    private void reconfigure(String vmName) {
        runTask(new HypervisorJob<Object>(flotoService.getManifest(), vmName) {
            @Override
            public Object execute() throws Exception {
                if (hypervisorService.isVmRunning(vmName)) {
                    HostStepRunner hostStepRunner = new HostStepRunner(host, flotoService, manifest, hypervisorService, vmName);
                    hostStepRunner.run(host.reconfigureSteps);
                }
                return null;
            }
        });
    }


}
