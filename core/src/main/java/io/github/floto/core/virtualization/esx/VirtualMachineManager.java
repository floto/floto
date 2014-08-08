package io.github.floto.core.virtualization.esx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.vmware.vim25.HttpNfcLeaseDeviceUrl;
import com.vmware.vim25.HttpNfcLeaseInfo;
import com.vmware.vim25.HttpNfcLeaseState;
import com.vmware.vim25.OvfCreateImportSpecParams;
import com.vmware.vim25.OvfCreateImportSpecResult;
import com.vmware.vim25.OvfFileItem;
import com.vmware.vim25.OvfNetworkMapping;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceConfigSpec;
import com.vmware.vim25.VirtualDeviceConfigSpecOperation;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigSpec;
import com.vmware.vim25.VirtualMachineRelocateDiskMoveOptions;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.HttpNfcLease;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.dsl.model.EsxHypervisorDescription;

public class VirtualMachineManager {
    private static final int CHUCK_LEN = 1 * 1024 * 1024 * 16;
    private static LeaseProgressUpdater leaseUpdater;
    
    private Logger log = LoggerFactory.getLogger(VirtualMachineManager.class);
    private EsxHypervisorDescription esxDesc;
    
    ServiceInstance si;
    Folder rootFolder;
    HostSystem host;
    Datacenter dc;
    ResourcePool rp;
    
    
    public VirtualMachineManager(EsxHypervisorDescription description) {
    	this.esxDesc = description;
    	connect();
    }
    
    
    public void connect() {
        try {
            log.info("connect()");

            si = new ServiceInstance(new URL("https://" + esxDesc.vCenter + "/sdk"), esxDesc.username, esxDesc.password, true);
            rootFolder = si.getRootFolder();
            dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
            rp = (ResourcePool) new InventoryNavigator(dc).searchManagedEntities("ResourcePool")[0];

            host = (HostSystem) si.getSearchIndex().findByIp(null, esxDesc.esxHost, false);
            
            if (host == null) {
                ManagedEntity[] hosts = new InventoryNavigator(rootFolder).searchManagedEntities("HostSystem");
                for(int i=0; i<hosts.length; i++) {
                	if (hosts[i].getName().equals(esxDesc.esxHost)) {
                		host = (HostSystem)hosts[i];
                		break;
                	}
                }
            }
            if (host == null) {
                throw new RuntimeException("Host " + esxDesc.esxHost + " not found");
            }

            rootFolder = dc.getVmFolder();
            // TODO: check if everything initialized right
        } catch (Throwable e) {
            disconnect();
            Throwables.propagate(e);
        }
    }
    
    public ServiceInstance getServiceInstance(){
    	return this.si;
    }

    public Datacenter getDatacenter(){
    	return this.dc;
    }
    
    public void disconnect() {
        log.info("disconnect()");
        si.getServerConnection().logout();
        si = null;
        rootFolder = null;
        host = null;
        dc = null;
        rp = null;
    }
    
    
	public VirtualMachine getVm(String vmName) throws Exception {
        return (VirtualMachine) new InventoryNavigator(rootFolder)
                .searchManagedEntity("VirtualMachine", vmName);
	}
	
	public ManagedEntity[] getVms() throws Exception {
		return new InventoryNavigator(rootFolder).searchManagedEntities("VirtualMachine");
	}
	
    public static void markAsTemplate(VirtualMachine vm) throws Exception {
        vm.markAsTemplate();
    }
    
    
    
    public void cloneVm(String templateVmName, VmDescription vmDesc,
			boolean linked) throws Exception {
        log.info("cloneVm " + templateVmName + " -> " + vmDesc);

        if (!existsVm(templateVmName)) {
            log.error("No VM " + templateVmName + " found");
            return;
        }

        VirtualMachine vm = (VirtualMachine) new InventoryNavigator(rootFolder)
                .searchManagedEntity("VirtualMachine", templateVmName);

        if (existsVm(vmDesc.vmName)) {
            log.error("VM " + vmDesc.vmName + " already exists");
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
            log.warn("Datastore: " + esxDesc.defaultDatastore
                    + " not found, using the first available: "
                    + datastore.getName());
        } else {
            log.info("Datastore: " + datastore.getName());
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
                    log.info("VM got snapshoted successfully.");
                } else {
                    log.error("Failure -: VM snapshot failed");
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

        Task task = vm.cloneVM_Task((Folder) vm.getParent(), vmDesc.vmName,
                cloneSpec);
        log.info("Launching the VM clone task. Please wait ...");

        if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
            log.info("VM got cloned successfully.");
        } else {
            log.error("Failure -: VM cannot be cloned");
        }

    }

	public void reconfigureVm(VmDescription vmDesc) throws Exception {
        log.info("reconfigureVm(" + vmDesc.vmName + ")");

        VirtualMachine vm = getVm(vmDesc.vmName);;

        Network network = null;
        for (Network net : host.getNetworks()) {
            if (net.getName().equals(esxDesc.networks.get(0))) {
                network = net;
                break;
            }
        }

        if (network == null) {
            log.error("network " + esxDesc.networks.get(0) + " not found.");
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

        if (task.waitForTask(200, 100).equals(Task.SUCCESS)) {
            log.info("VM reconfigured successfully");
        } else {
            log.error("failed to reconfigure VM");
        }
    }

	public void deployTemplate(URL vmUrl, String templateVmName)
            throws Exception {
        log.info("deploy template " + vmUrl);

        // find the datastore
        Datastore datastore = host.getDatastores()[0];
        for (Datastore ds : host.getDatastores()) {
            if (ds.getName().equals(esxDesc.defaultDatastore)) {
                datastore = ds;
                break;
            }
        }
        log.info("Datastore:" + datastore.getName());

        if (existsVm(templateVmName)) {
            log.warn("template " + templateVmName + " already exists");
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
            log.error("ovfImportResult=null");
            return;
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
                rootFolder, host);

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
                        log.info("Completed uploading the VMDK file:"
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
            VirtualMachine vm = (VirtualMachine) new InventoryNavigator(
                    rootFolder).searchManagedEntity("VirtualMachine", vmName);
            return (vm != null);
        } catch (Exception e) {
            return false;
        }
    }
    
    
}
