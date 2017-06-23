package io.github.floto.core.virtualization.esx;

import com.google.common.base.Throwables;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.dsl.model.EsxHypervisorDescription;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VirtualMachineManager {
    private static final int CHUCK_LEN = 1 * 1024 * 1024 * 16;
    private static LeaseProgressUpdater leaseUpdater;

    private Logger log = LoggerFactory.getLogger(VirtualMachineManager.class);
    private EsxHypervisorDescription esxDesc;

    public VirtualMachineManager(EsxHypervisorDescription description) {
    	this.esxDesc = description;

        try {
			ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
			Folder rootFolder = si.getRootFolder();
			Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];

			rootFolder = dc.getVmFolder();
		} catch (Throwable e) {
			Throwables.propagate(e);
		}
    }

	private Folder findOrCreateFolder(String folderNameOrPath) throws Exception {
		String folders[] = folderNameOrPath.split("/");

		ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
		Folder rootFolder = si.getRootFolder();
		Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];

		Folder currentFolder = dc.getVmFolder();

		Folder nextFolder = null;
		for (String folderName:folders){
			if(folderName.isEmpty()) {
				continue;
			}
			ManagedEntity[] me = currentFolder.getChildEntity();

			for (ManagedEntity m: me) {
				if (m instanceof Folder && m.getName().equals(folderName)) {
					nextFolder = (Folder)m;
					break;
				}
			}
			if (nextFolder == null) {
				nextFolder = currentFolder.createFolder(folderName);
			}
			currentFolder = nextFolder;
			nextFolder = null;
		}

		return currentFolder;
	}

	public VirtualMachine getVm(String vmName) throws Exception {
		String vmFolder = getVmFolder(vmName);
		Folder folder = findOrCreateFolder(vmFolder);
        if(folder == null) {
            throw new RuntimeException("could not find vm folder "+ vmFolder);
        }
        return (VirtualMachine) new InventoryNavigator(folder).searchManagedEntity("VirtualMachine", getShortVmName(vmName));
	}

	public String getVmFolder(String vmName) {
		int index = vmName.lastIndexOf("/");
		if(index < 0) {
			return "";
		}
		return vmName.substring(0, index);
	}

	private String getShortVmName(String vmName) {
		return vmName.substring(vmName.lastIndexOf("/") + 1);
	}

	public ManagedEntity[] getVms() throws Exception {
		Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
		return new InventoryNavigator(rootFolder).searchManagedEntities("VirtualMachine");
	}

    public static void markAsTemplate(VirtualMachine vm) throws Exception {
		if (vm.getResourcePool() != null) {
			vm.markAsTemplate();
		}
    }


	public static void markAsVirtualMachine(VirtualMachine vm, ResourcePool rp, HostSystem host) throws Exception {
		if (vm.getResourcePool() == null) {
			vm.markAsVirtualMachine(rp, host);
		}
	}


	private void renameVm(String oldName, String newName) throws Exception {
		log.info("rename vm '"+oldName+"' to '"+newName+"'");
		VirtualMachine vm = getVm(oldName);
		Task task = vm.rename_Task(newName);
		EsxUtils.waitForTask(task, "rename vm");
	}


	private boolean existsVm(String vmName) {
		try {
			VirtualMachine vm = getVm(vmName);
			return (vm != null);
		} catch (Exception e) {
			return false;
		}
	}

    public void cloneVm(String templateVmName, VmDescription vmDesc,
			boolean linked) throws Exception {
        log.info("Clone vm " + templateVmName + " -> " + vmDesc.vmName);

        Folder folder = findOrCreateFolder(getVmFolder(templateVmName));
        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        ResourcePool rp = getResourcePool(host);

        if (!existsVm(templateVmName)) {
            log.error("Template " + templateVmName + " not found");
            return;
        }
        VirtualMachine vm = (VirtualMachine) new InventoryNavigator(folder)
                .searchManagedEntity("VirtualMachine", getShortVmName(templateVmName));

        if (existsVm(vmDesc.vmName)) {
            log.error("Vm " + vmDesc.vmName + " already exists");
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
	            markAsVirtualMachine(vm, rp, host);

	            Task task = vm.createSnapshot_Task("snap1", "Snapshot for linked clones", false, true);

	            EsxUtils.waitForTask(task, "Create snapshot");

				markAsTemplate(getVm(templateVmName));
            }

            cloneSpec.snapshot = vm.getSnapshot().currentSnapshot;
        }

        vmRelocateSpec.setPool(rp.getMOR());
        vmRelocateSpec.setDatastore(datastore.getMOR());
        vmRelocateSpec.setHost(host.getMOR());
        cloneSpec.setLocation(vmRelocateSpec);
        cloneSpec.setPowerOn(false);
        cloneSpec.setTemplate(false);

        Task task = vm.cloneVM_Task(folder, getShortVmName(vmDesc.vmName), cloneSpec);
        log.info("Launching the vm clone task ...");

        EsxUtils.waitForTask(task, "Clone vm "+ vm.getName());

        ManagedEntity[] me = {getVm(vmDesc.vmName)};
        folder.moveIntoFolder_Task(me);
    }

	public void reconfigureVm(VmDescription vmDesc) throws Exception {
        log.info("Reconfigure vm " + getShortVmName(vmDesc.vmName));

        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        VirtualMachine vm = getVm(vmDesc.vmName);


		// search for the desired Network on the esx
        Network network = null;
        for (Network net : host.getNetworks()) {
            if (net.getName().equals(esxDesc.networks.get(0))) {
                network = net;
                break;
            }
        }

        if (network == null) {
            throw new RuntimeException("Network " + esxDesc.networks.get(0) + " not found");
        }

        List<VirtualDeviceConfigSpec> vDevConfSpecList = new ArrayList<>();

		// remove virtual nic
        for (VirtualDevice vd : vm.getConfig().getHardware().device) {
            if (vd instanceof VirtualEthernetCard) {
                VirtualDeviceConfigSpec vDevConfSpec = new VirtualDeviceConfigSpec();
                vDevConfSpec.setOperation(VirtualDeviceConfigSpecOperation.remove);
                vDevConfSpec.setDevice(vd);
                vDevConfSpecList.add(vDevConfSpec);
            }
        }

		// create new virtual nic of type VMXNET3
		VirtualDeviceConfigSpec vmxnet3ConfSpec = new VirtualDeviceConfigSpec();
		vmxnet3ConfSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		VirtualVmxnet3 vNic = new VirtualVmxnet3();
		VirtualEthernetCardNetworkBackingInfo nicBacking = new VirtualEthernetCardNetworkBackingInfo();
		nicBacking.setDeviceName(network.getName());
		vNic.setAddressType("generated");
		vNic.setBacking(nicBacking);
		vNic.setKey(4);
		vmxnet3ConfSpec.setDevice(vNic);
		vDevConfSpecList.add(vmxnet3ConfSpec);

		// change the default scsi controller to paravirtualized
		VirtualDeviceConfigSpec newScsiConfSpec = new VirtualDeviceConfigSpec();
		newScsiConfSpec.setOperation(VirtualDeviceConfigSpecOperation.add);
		ParaVirtualSCSIController scsiCtrl = new ParaVirtualSCSIController();
		scsiCtrl.setKey(0);
		scsiCtrl.setBusNumber(0);
		scsiCtrl.setSharedBus(VirtualSCSISharing.noSharing);
		newScsiConfSpec.setDevice(scsiCtrl);
		vDevConfSpecList.add(newScsiConfSpec);

		// reconfigure vCPU and vRAM
		VirtualMachineConfigSpec vmcs = new VirtualMachineConfigSpec();
		vmcs.setNumCPUs(vmDesc.numberOfCores);
		vmcs.setMemoryMB(vmDesc.memoryInMB);

		VirtualDeviceConfigSpec[] vDevConfSpecArray = {};
		vDevConfSpecArray = vDevConfSpecList.toArray(vDevConfSpecArray);

		vmcs.setDeviceChange(vDevConfSpecArray);

        Task task = vm.reconfigVM_Task(vmcs);
        EsxUtils.waitForTask(task, "Reconfigure vm " + vm.getName());

		// add data disks
		VirtualDevice[] vds = vm.getConfig().getHardware().getDevice();
		int ctrlKey = 0;
		for(int k=0;k<vds.length;k++) {
			if(vds[k].getDeviceInfo().getLabel().equalsIgnoreCase("SCSI Controller 0")) {
				ctrlKey = vds[k].getKey();
			}
		}

		for (VmDescription.Disk disk : vmDesc.disks) {
			addDataDisk(disk, vmDesc, ctrlKey);
		}
	}

	public void addDataDisk(VmDescription.Disk disk, VmDescription vmDesc, int ctrlKey) throws Exception {
		VirtualMachine vm = getVm(vmDesc.vmName);
		String fileName = "[" + disk.datastore + "] " + vm.getName() + "_data" + disk.slot + ".vmdk"; //_data
		List<VirtualDeviceConfigSpec> vDevConfSpecList = new ArrayList<>();
		VirtualDiskManager vdm =  new VirtualDiskManager(vm);

		try {
			ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
			Folder rootFolder = si.getRootFolder();
			Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
			EsxConnectionManager.getConnection(esxDesc).getVirtualDiskManager().queryVirtualDiskFragmentation(fileName, dc);
			log.info(fileName + " exists - will add it to virtual machine.");
			VirtualDeviceConfigSpec diskDevConfSpec = vdm.addVirtualDisk(disk, VirtualDiskMode.persistent, ctrlKey);
			vDevConfSpecList.add(diskDevConfSpec);
		} catch (Exception e) {
			log.info(fileName + " does not exist - will create new virtual disk.");
			VirtualDeviceConfigSpec diskDevConfSpec = vdm.createHardDisk(disk, VirtualDiskType.thin, VirtualDiskMode.persistent, ctrlKey);
			vDevConfSpecList.add(diskDevConfSpec);
		}

		VirtualMachineConfigSpec vmcs = new VirtualMachineConfigSpec();

		VirtualDeviceConfigSpec[] vDevConfSpecArray = {};
		vDevConfSpecArray = vDevConfSpecList.toArray(vDevConfSpecArray);
		vmcs.setDeviceChange(vDevConfSpecArray);

		Task task = vm.reconfigVM_Task(vmcs);
		EsxUtils.waitForTask(task, "Reconfigure VM " + vm.getName() + ", add vmdk " + fileName);
	}

	public void deployTemplate(URL vmUrl, String templateVmName) throws Exception {
        log.info("Deploy template " + vmUrl + " to " + esxDesc.esxHost);

        ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
        Folder folder = findOrCreateFolder(getVmFolder(templateVmName));
        HostSystem host = EsxConnectionManager.getHost(esxDesc);
        ResourcePool rp = getResourcePool(host);
        // find the datastore
        Datastore datastore = host.getDatastores()[0];
        for (Datastore ds : host.getDatastores()) {
            if (ds.getName().equals(esxDesc.defaultDatastore)) {
                datastore = ds;
                break;
            }
        }
        log.info("Using datastore: " + datastore.getName());

        if (existsVm(templateVmName)) {
            log.warn("Template " + templateVmName + " already exists");
            return;
        }
		String shortName = getShortVmName(templateVmName);
        OvfCreateImportSpecParams importSpecParams = new OvfCreateImportSpecParams();
        importSpecParams.setHostSystem(host.getMOR());
        importSpecParams.setLocale("US");
        importSpecParams.setEntityName(shortName);
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
        if (ovfImportResult.error != null && ovfImportResult.error.length > 0) {
            for (LocalizedMethodFault error : ovfImportResult.error) {
                throw new RuntimeException(error.getLocalizedMessage());
            }
        }

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

        HttpNfcLease httpNfcLease = rp.importVApp(ovfImportResult.getImportSpec(), folder, host);

        // Wait until the HttpNfcLeaseState is ready
        HttpNfcLeaseState hls;
        for (; ; ) {
            hls = httpNfcLease.getState();
            if ((hls == HttpNfcLeaseState.ready) || (hls == HttpNfcLeaseState.error)) {
                break;
            }
        }

        if (hls.equals(HttpNfcLeaseState.ready)) {
            // log.info("HttpNfcLeaseState: ready ");
            HttpNfcLeaseInfo httpNfcLeaseInfo = httpNfcLease.getInfo();
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
                        log.info("DeviceURL: {}", deviceUrl.getUrl());
                        URL url = new URL(deviceUrl.getUrl());
                        // Use the configured esxHost Name/IP since the url name may not be resolvable
                        String urlToPost = deviceUrl.getUrl().replace("*", esxDesc.esxHost).replace(url.getHost(),
                                esxDesc.esxHost);
                        log.info("urlToPost: {}", urlToPost);

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
        } else {
            log.error("Error: {}", httpNfcLease.getError().getLocalizedMessage());
            throw new IllegalStateException("Lease in state " + httpNfcLease.getError().getLocalizedMessage());
        }

		VirtualMachine vm = getVm(templateVmName);

		Task task = vm.createSnapshot_Task("snap1", "Snapshot for linked clones", false, true);
		EsxUtils.waitForTask(task, "Create snapshot");

        markAsTemplate(vm);

    }

    private ResourcePool getResourcePool(HostSystem host) {
        try {
            ResourcePool targetResourcePool = null;
            Folder rootFolder = EsxConnectionManager.getConnection(esxDesc).getRootFolder();
            Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
            Object[] resourcePools = new InventoryNavigator(dc).searchManagedEntities("ResourcePool");
            for (Object resourcePool : resourcePools) {
                if (isSameManagedObject(((ResourcePool) resourcePool).getParent(),host.getParent())) {
                    targetResourcePool = (ResourcePool) resourcePool;
                }
            }
            if(targetResourcePool == null) {
                throw new RuntimeException("Could not find resource pool for host " + host.getName());
            }
            return targetResourcePool;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static boolean isSameManagedObject(ManagedObject a, ManagedObject b) {
        return a.getMOR().getType().equals(b.getMOR().getType()) &&
                a.getMOR().get_value().equals(b.getMOR().get_value());
    }

    private void uploadVmdkFile(boolean put, InputStream diskInputStream,
                                String urlStr, long bytesAlreadyWritten, long totalBytes)
            throws IOException {
        HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

        HttpsURLConnection conn = (HttpsURLConnection) new URL(urlStr).openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setChunkedStreamingMode(CHUCK_LEN);
        conn.setRequestMethod(put ? "PUT" : "POST"); // Use a post method to
        // write the file.
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Content-Type", "application/x-vnd.vmware-streamVmdk");
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

	public void exportVM(String vmName, String hostName, String targetFile) throws Exception {
		log.info("Export vm " + hostName + "("+vmName+") to " + targetFile);

		File exportDir = (new File(new File(targetFile).getParent() + "/" + hostName));
		FileUtils.forceMkdir(exportDir);

		ServiceInstance si = EsxConnectionManager.getConnection(esxDesc);
		VirtualMachine vm = getVm(vmName);
		if (vmName != hostName) {
			renameVm(vmName,hostName);
		}

		HttpNfcLease hnLease = getVm(hostName).exportVm();

		// Wait until the HttpNfcLeaseState is ready
		HttpNfcLeaseState hls;
		for(;;) {
			hls = hnLease.getState();
			if(hls == HttpNfcLeaseState.ready) {
				log.info("HttpNfcLeaseState: ready");
				break;
			}
			if(hls == HttpNfcLeaseState.error) {
				log.error("HttpNfcLeaseState error");
				return;
			}
		}

		HttpNfcLeaseInfo httpNfcLeaseInfo = hnLease.getInfo();
		httpNfcLeaseInfo.setLeaseTimeout(300*1000*1000);
		printHttpNfcLeaseInfo(httpNfcLeaseInfo);

		//Note: the diskCapacityInByte could be many time bigger than
		//the total size of VMDK files downloaded.
		//As a result, the progress calculated could be much less than reality.
		long diskCapacityInByte = httpNfcLeaseInfo.getTotalDiskCapacityInKB() * 1024;

		LeaseProgressUpdater leaseProgUpdater = new LeaseProgressUpdater(hnLease, 5000);
		leaseProgUpdater.start();

        long alredyWrittenBytes = 0;
        HttpNfcLeaseDeviceUrl[] deviceUrls = httpNfcLeaseInfo.getDeviceUrl();
        OvfFile[] ovfFiles = new OvfFile[deviceUrls.length];
        if (deviceUrls != null)
		{
			log.info("Downloading Files:");
			for (int i = 0; i < deviceUrls.length; i++)
			{
				String deviceId = deviceUrls[i].getKey();
				String deviceUrlStr = deviceUrls[i].getUrl();
				String diskFileName = deviceUrlStr.substring(deviceUrlStr.lastIndexOf("/") + 1);
				String diskUrlStr = deviceUrlStr.replace("*", esxDesc.esxHost);
				log.info("File Name: " + diskFileName);
				log.info("VMDK URL: " + diskUrlStr);
				String cookie = si.getServerConnection().getVimService().getWsc().getCookie();

				FileOutputStream fos = new FileOutputStream(exportDir + "/" + diskFileName);
				long lengthOfDiskFile = writeVMDKFile(fos, diskUrlStr, cookie, alredyWrittenBytes, diskCapacityInByte, leaseProgUpdater);
				fos.close();

				alredyWrittenBytes += lengthOfDiskFile;
				OvfFile ovfFile = new OvfFile();
				ovfFile.setPath(diskFileName);
				ovfFile.setDeviceId(deviceId);
				ovfFile.setSize(lengthOfDiskFile);
				ovfFiles[i] = ovfFile;
			}

			OvfCreateDescriptorParams ovfDescParams = new OvfCreateDescriptorParams();
			ovfDescParams.setOvfFiles(ovfFiles);
			OvfCreateDescriptorResult ovfCreateDescriptorResult = si.getOvfManager().createDescriptor(getVm(hostName), ovfDescParams);

			String ovfFileName = hostName + ".ovf";
			FileWriter out = new FileWriter(exportDir + "/" +ovfFileName);
			out.write(ovfCreateDescriptorResult.getOvfDescriptor());
			out.close();
			log.info("OVF Desriptor Written to file: " + exportDir + "/" + ovfFileName);
		}

		log.info("Completed Downloading the files, now make ova(tar)");
		leaseProgUpdater.interrupt();
		hnLease.httpNfcLeaseProgress(100);
		hnLease.httpNfcLeaseComplete();

		// tar the ovf to the ova
        // first entry has to be the ovf!
        TarOutputStream tos = new TarOutputStream(new FileOutputStream(targetFile));
        tos.setBigNumberMode(TarOutputStream.BIGNUMBER_STAR);
        tos.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
        for (File file : exportDir.listFiles()) {
            if (file.toString().toLowerCase().endsWith(".ovf")) {
                TarEntry entry = new TarEntry(file, file.getName());
                tos.putNextEntry(entry);
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bif = new BufferedInputStream(fis);
                IOUtils.copy(bif, tos);
                bif.close();
                fis.close();
                tos.closeEntry();
                break;
            }
        }

        // vmdks has to be in the same order as defined in ovf
        for (int i=0; i<ovfFiles.length; i++){
            File file = new File(exportDir.getPath() + "/" + ovfFiles[i].getPath());
            TarEntry entry = new TarEntry(file, file.getName());
            tos.putNextEntry(entry);
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bif = new BufferedInputStream(fis);
            IOUtils.copyLarge(bif, tos);
            bif.close();
            fis.close();
            tos.closeEntry();

        }
		tos.close();

		log.info("Created " + targetFile);

		if (vmName != hostName) {
			renameVm(hostName, vmName);
		}

		// remove the ovf directory
		FileUtils.forceDelete(exportDir);
	}

	private long writeVMDKFile(OutputStream outputStream, String diskUrlStr, String cookieStr,
	                                  long bytesAlreadyWritten, long totalBytes, LeaseProgressUpdater leaseProgUpdater) throws IOException
	{
		HostnameVerifier hv = (urlHostName, session) -> true;
		HttpsURLConnection.setDefaultHostnameVerifier(hv);
		URL url = new URL(diskUrlStr);
		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setAllowUserInteraction(true);
		conn.setRequestProperty("Cookie",  cookieStr);
		conn.connect();

		InputStream in = conn.getInputStream();
		byte[] buf = new byte[CHUCK_LEN];
		int len;
		long bytesWritten = 0;
		while ((len = in.read(buf)) > 0) {
			outputStream.write(buf, 0, len);
			bytesWritten += len;
            int percent = (int)(((bytesAlreadyWritten + bytesWritten) * 100) / totalBytes);
			leaseProgUpdater.setPercent(percent);
//			log.info("bytes written: " + bytesWritten);
		}
		in.close();
		return bytesWritten;
	}

	private void printHttpNfcLeaseInfo(HttpNfcLeaseInfo info)
	{
		log.info("########################  HttpNfcLeaseInfo  ###########################");
		log.info("Lease Timeout: " + info.getLeaseTimeout());
		log.info("Total Disk capacity: " + info.getTotalDiskCapacityInKB());
		HttpNfcLeaseDeviceUrl[] deviceUrlArr = info.getDeviceUrl();
		if (deviceUrlArr != null)
		{
			int deviceUrlCount = 1;
			for (HttpNfcLeaseDeviceUrl durl : deviceUrlArr) {
				log.info("HttpNfcLeaseDeviceUrl : " + deviceUrlCount++);
//				log.info("  Device URL Import Key: " + durl.getImportKey());
//				log.info("  Device URL Key: " + durl.getKey());
				log.info("  Device URL : " + durl.getUrl());
//				log.info("  SSL Thumbprint : " + durl.getSslThumbprint());
			}
		}
		else {
			log.warn("No Device URLS Found");
		}
	}
}
