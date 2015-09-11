package io.github.floto.dsl.model;

import java.util.ArrayList;
import java.util.List;

public class VmConfiguration {
    public String ovaUrl;
    public String vmName;
    public int numberOfCores = 4;
    public long memoryInMB = 4096;
    public HypervisorDescription hypervisor = new WorkstationHypervisorDescription();
    public List<String> networks = new ArrayList<>();
    {
        networks.add("VM Network");
    }
    public List<DiskDescription> disks = new ArrayList<>();

}
