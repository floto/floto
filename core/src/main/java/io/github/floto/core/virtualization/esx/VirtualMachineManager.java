package io.github.floto.core.virtualization.esx;

import com.google.common.base.Throwables;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.dsl.model.EsxHypervisorDescription;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VirtualMachineManager {
    private static final int CHUCK_LEN = 1 * 1024 * 1024 * 16;
    private static LeaseProgressUpdater leaseUpdater;
    
    private Logger log = LoggerFactory.getLogger(VirtualMachineManager.class);
    private EsxHypervisorDescription esxDesc;
    
    String domainName;
    
    public VirtualMachineManager(EsxHypervisorDescription description, String domainName) {
    	this.esxDesc = description;
    	this.domainName = domainName;
    	
        try {
			ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
			Folder rootFolder = si.getRootFolder();
			Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];

			rootFolder = dc.getVmFolder();
			ManagedEntity me = new InventoryNavigator(rootFolder).searchManagedEntity("Folder", domainName);
			if (me == null) {
				rootFolder.createFolder(domainName);
			}
		} catch (Throwable e) {
			Throwables.propagate(e);
		}
    }
    
	public VirtualMachine getVm(String vmName) throws Exception {
		Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
        return (VirtualMachine) new InventoryNavigator(rootFolder).searchManagedEntity("VirtualMachine", vmName);
	}
	
	public ManagedEntity[] getVms() throws Exception {
		Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
		return new InventoryNavigator(rootFolder).searchManagedEntities("VirtualMachine");
	}
	
    public static void markAsTemplate(VirtualMachine vm) throws Exception {
        vm.markAsTemplate();
    }
    
    
    
    public void cloneVm(String templateVmName, VmDescription vmDesc,
			boolean linked) throws Exception {
        log.info("cloneVm " + templateVmName + " -> " + vmDesc);
        
        Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
        Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
        ResourcePool rp = (ResourcePool) new InventoryNavigator(dc).searchManagedEntities("ResourcePool")[0];
        Folder vmFolder = (Folder)new InventoryNavigator(rootFolder).searchManagedEntity("Folder", domainName);
        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        
        if (!existsVm(templateVmName)) {
            log.error("template " + templateVmName + " not found");
            return;
        }
        VirtualMachine vm = (VirtualMachine) new InventoryNavigator(rootFolder)
                .searchManagedEntity("VirtualMachine", templateVmName);

        if (existsVm(vmDesc.vmName)) {
            log.error("virtual machine " + vmDesc.vmName + " allready exists");
            return;
        }

        // find the datastore
        Datastore datastore = null;
        for (Datastore ds : host.getDatastores()) {
            if (ds.getName().equals(esxDesc.defaultDatastore)) {
                datastore = ds;
                break;
            }
        }

        if (datastore == null) {
            datastore = host.getDatastores()[0];
            log.warn("datastore: " + esxDesc.defaultDatastore
                    + " not found, using the first available: "
                    + datastore.getName());
        } else {
            log.info("datastore: " + datastore.getName());
        }

        // CustomizationSpec customSpec = new CustomizationSpec();
        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
        VirtualMachineRelocateSpec vmRelocateSpec = new VirtualMachineRelocateSpec();

        if (linked) {
            // vmRelocateSpec.setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.moveChildMostDiskBacking.name());

            vmRelocateSpec
                    .setDiskMoveType(VirtualMachineRelocateDiskMoveOptions.createNewChildDiskBacking
                            .name());

            if (vm.getSnapshot() == null) {
                if (vm.getResourcePool() == null) {
                    vm.markAsVirtualMachine(rp, host);
                }
                Task task = vm.createSnapshot_Task("snap1",
                        "snapshot for creating linked virtual machines", false,
                        true);

                if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
                    log.info("successfully created snapshot");
                } else {
                    log.error("failed craeting snapshot");
                }
                vm.markAsTemplate();
            }

            cloneSpec.snapshot = vm.getSnapshot().currentSnapshot;
        }

        vmRelocateSpec.setPool(rp.getMOR());
        vmRelocateSpec.setDatastore(datastore.getMOR());
        vmRelocateSpec.setHost(host.getMOR());
        cloneSpec.setLocation(vmRelocateSpec);
        cloneSpec.setPowerOn(false);
        cloneSpec.setTemplate(false);

        Task task = vm.cloneVM_Task(vmFolder, vmDesc.vmName, cloneSpec);
        log.info("launching the virtual machine clone task ...");

        EsxUtils.waitForTask(task, "clone virtual machine "+ vm.getName());

        ManagedEntity[] me = {(ManagedEntity)getVm(vmDesc.vmName)};
        vmFolder.moveIntoFolder_Task(me);
    }

	public void reconfigureVm(VmDescription vmDesc) throws Exception {
        log.info("reconfigureVm(" + vmDesc.vmName + ")");
        
        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        
        VirtualMachine vm = getVm(vmDesc.vmName);;

        Network network = null;
        for (Network net : host.getNetworks()) {
            if (net.getName().equals(esxDesc.networks.get(0))) {
                network = net;
                break;
            }
        }

        if (network == null) {
            log.error("network " + esxDesc.networks.get(0) + " not found");
            return;
        }

        List<VirtualDeviceConfigSpec> vdcss = new ArrayList<VirtualDeviceConfigSpec>();

        for (VirtualDevice vd : vm.getConfig().getHardware().device) {
            if (vd instanceof VirtualEthernetCard) {
                VirtualDeviceConfigSpec vdcs = new VirtualDeviceConfigSpec();
                vdcs.setOperation(VirtualDeviceConfigSpecOperation.edit);
                VirtualEthernetCard vec = (VirtualEthernetCard) vd;
                VirtualEthernetCardNetworkBackingInfo vecnb = new VirtualEthernetCardNetworkBackingInfo();
                vecnb.setDeviceName(network.getName());
                vec.setBacking(vecnb);
                vdcs.setDevice(vec);
                vdcss.add(vdcs);
            }
        }

        VirtualMachineConfigSpec vmcs = new VirtualMachineConfigSpec();
        vmcs.setNumCPUs(vmDesc.numberOfCores);
        vmcs.setMemoryMB(vmDesc.memoryInMB);

        VirtualDeviceConfigSpec[] nicSpecArray = new VirtualDeviceConfigSpec[vdcss
                .size()];
        nicSpecArray = vdcss.toArray(nicSpecArray);

        vmcs.setDeviceChange(nicSpecArray);
        Task task = vm.reconfigVM_Task(vmcs);
        EsxUtils.waitForTask(task, "reconfigure vm "+ vm.getName());

    }

	public void deployTemplate(URL vmUrl, String templateVmName)
            throws Exception {
        log.info("deploy template " + vmUrl);

        ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
        Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
        Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
        ResourcePool rp = (ResourcePool) new InventoryNavigator(dc).searchManagedEntities("ResourcePool")[0];
        Folder vmFolder = (Folder)new InventoryNavigator(rootFolder).searchManagedEntity("Folder", domainName);
        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        
        // find the datastore
        Datastore datastore = host.getDatastores()[0];
        for (Datastore ds : host.getDatastores()) {
            if (ds.getName().equals(esxDesc.defaultDatastore)) {
                datastore = ds;
                break;
            }
        }
        log.info("datastore:" + datastore.getName());

        if (existsVm(templateVmName)) {
            log.warn("template " + templateVmName + " allready exists");
            return;
        }

        OvfCreateImportSpecParams importSpecParams = new OvfCreateImportSpecParams();
        importSpecParams.setHostSystem(host.getMOR());
        importSpecParams.setLocale("US");
        importSpecParams.setEntityName(templateVmName);
        importSpecParams.setDeploymentOption("");
        OvfNetworkMapping networkMapping = new OvfNetworkMapping();
        networkMapping.setName("Network 1");
        networkMapping.setNetwork(host.getNetworks()[0].getMOR()); // network);
        importSpecParams
                .setNetworkMapping(new OvfNetworkMapping[]{networkMapping});
        importSpecParams.setPropertyMapping(null);

        String ovfDescriptor = null;

        TarInputStream tis = new TarInputStream(new BufferedInputStream(
                vmUrl.openStream()));

        // read the ovf description file
        TarEntry entry;
        while ((entry = tis.getNextEntry()) != null) {
            if (entry.getName().endsWith(".ovf")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                tis.copyEntryContents(baos);
                ovfDescriptor = baos.toString();
                log.debug("ovfDesc:" + ovfDescriptor);

                if (ovfDescriptor == null) {
                    return;
                }
                break;
            }
        }
        tis.close();

        OvfCreateImportSpecResult ovfImportResult = si.getOvfManager()
                .createImportSpec(ovfDescriptor, rp, datastore,
                        importSpecParams);

        if (ovfImportResult == null) {
            throw new RuntimeException("ovfImportResult=null");
        }

        // addTotalBytes
        OvfFileItem[] fileItemArr = ovfImportResult.getFileItem();

        long totalBytes = 0;
        if (fileItemArr != null) {
            for (OvfFileItem fi : fileItemArr) {
                // log.info("================ OvfFileItem ================");
                // log.info("chunkSize: " + fi.getChunkSize());
                // log.info("create: " + fi.isCreate());
                // log.info("deviceId: " + fi.getDeviceId());
                // log.info("path: " + fi.getPath());
                // log.info("size: " + fi.getSize());
                // log.info("==============================================");
                totalBytes += fi.getSize();
            }
        }
        log.info("Total bytes: " + totalBytes);

        HttpNfcLease httpNfcLease;
        httpNfcLease = rp.importVApp(ovfImportResult.getImportSpec(),
                vmFolder, host);

        // Wait until the HttpNfcLeaseState is ready
        HttpNfcLeaseState hls;
        for (; ; ) {
            hls = httpNfcLease.getState();
            if (hls == HttpNfcLeaseState.ready
                    || hls == HttpNfcLeaseState.error) {
                break;
            }
        }

        if (hls.equals(HttpNfcLeaseState.ready)) {
            // log.info("HttpNfcLeaseState: ready ");
            HttpNfcLeaseInfo httpNfcLeaseInfo = (HttpNfcLeaseInfo) httpNfcLease
                    .getInfo();
            // log.info("================ HttpNfcLeaseInfo ================");
            // HttpNfcLeaseDeviceUrl[] deviceUrlArr = httpNfcLeaseInfo
            // .getDeviceUrl();
            // for (HttpNfcLeaseDeviceUrl durl : deviceUrlArr) {
            // log.info("Device URL Import Key: " + durl.getImportKey());
            // log.info("Device URL Key: " + durl.getKey());
            // log.info("Device URL : " + durl.getUrl());
            // log.info("Updated device URL: " + durl.getUrl());
            // }
            // log.info("Lease Timeout: " + httpNfcLeaseInfo.getLeaseTimeout());
            // log.info("Total Disk capacity: "
            // + httpNfcLeaseInfo.getTotalDiskCapacityInKB());
            // log.info("==================================================");

            leaseUpdater = new LeaseProgressUpdater(httpNfcLease, 5000);
            leaseUpdater.start();

            HttpNfcLeaseDeviceUrl[] deviceUrls = httpNfcLeaseInfo
                    .getDeviceUrl();

            long bytesAlreadyWritten = 0;
            for (HttpNfcLeaseDeviceUrl deviceUrl : deviceUrls) {
                String deviceKey = deviceUrl.getImportKey();
                for (OvfFileItem ovfFileItem : ovfImportResult.getFileItem()) {
                    if (deviceKey.equals(ovfFileItem.getDeviceId())) {
                        log.info("Import key==OvfFileItem device id: "
                                + deviceKey);
                        String absoluteFile = new File(vmUrl.toExternalForm())
                                .getParent()
                                + File.separator
                                + ovfFileItem.getPath();
                        String urlToPost = deviceUrl.getUrl().replace("*",
                        		esxDesc.esxHost);

                        tis = new TarInputStream(new BufferedInputStream(
                                vmUrl.openStream()));
                        while ((entry = tis.getNextEntry()) != null) {
                            if (entry.getName().equals(ovfFileItem.getPath())) {
                                uploadVmdkFile(ovfFileItem.isCreate(), tis,
                                        urlToPost, bytesAlreadyWritten,
                                        totalBytes);
                                break;
                            }
                        }
                        tis.close();

                        bytesAlreadyWritten += ovfFileItem.getSize();
                        log.info("completed uploading the VMDK file:"
                                + absoluteFile);
                    }
                }
            }

            leaseUpdater.interrupt();
            httpNfcLease.httpNfcLeaseProgress(100);
            httpNfcLease.httpNfcLeaseComplete();
        }
        
        markAsTemplate(getVm(templateVmName));

    }

    private void uploadVmdkFile(boolean put, InputStream diskInputStream,
                                String urlStr, long bytesAlreadyWritten, long totalBytes)
            throws IOException {
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            public boolean verify(String urlHostName, SSLSession session) {
                return true;
            }
        });

        HttpsURLConnection conn = (HttpsURLConnection) new URL(urlStr)
                .openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setChunkedStreamingMode(CHUCK_LEN);
        conn.setRequestMethod(put ? "PUT" : "POST"); // Use a post method to
        // write the file.
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type",
                "application/x-vnd.vmware-streamVmdk");
        // conn.setRequestProperty("Content-Length",
        // Long.toString(diskFilePath.));

        BufferedOutputStream bos = new BufferedOutputStream(
                conn.getOutputStream());

        BufferedInputStream diskis = new BufferedInputStream(diskInputStream);
        int bytesAvailable = diskis.available();
        int bufferSize = Math.min(bytesAvailable, CHUCK_LEN);
        byte[] buffer = new byte[bufferSize];

        long totalBytesWritten = 0;
        while (true) {
            int bytesRead = diskis.read(buffer, 0, bufferSize);
            if (bytesRead == -1) {
                log.info("Total bytes written: " + totalBytesWritten);
                break;
            }

            totalBytesWritten += bytesRead;
            bos.write(buffer, 0, bufferSize);
            bos.flush();
            int progressPercent = (int) (((bytesAlreadyWritten + totalBytesWritten) * 100) / totalBytes);
            leaseUpdater.setPercent(progressPercent);
        }

        diskis.close();
        bos.flush();
        bos.close();
        conn.disconnect();
    }

    private boolean existsVm(String vmName) {
        try {
            Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
            
            VirtualMachine vm = (VirtualMachine) new InventoryNavigator(
                    rootFolder).searchManagedEntity("VirtualMachine", vmName);
            return (vm != null);
        } catch (Exception e) {
            return false;
        }
    }
    
    
}
