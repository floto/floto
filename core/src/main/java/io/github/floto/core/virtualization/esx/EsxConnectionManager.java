package io.github.floto.core.virtualization.esx;

import com.vmware.vim25.mo.*;
import io.github.floto.dsl.model.EsxHypervisorDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

public class EsxConnectionManager {

	private static Logger log = LoggerFactory.getLogger(EsxConnectionManager.class);

	private static Map<String, ServiceInstance> connections = new HashMap<>();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread("EsxConnectionManager.shutdownHook") {
			public void run() {
				closeAllConnections();
			}
		});
	}

	private EsxConnectionManager() {
	}

	private static ServiceInstance createNewConnection(
		EsxHypervisorDescription esxDesc) throws RemoteException,
		MalformedURLException {

		log.info("Create new connection for vCenter: " + esxDesc.vCenter);

		return new ServiceInstance(new URL("https://"
			+ esxDesc.vCenter + "/sdk"), esxDesc.username,
			esxDesc.password, true);
	}

	public static ServiceInstance getConnection(
		EsxHypervisorDescription esxDesc) throws RemoteException,
		MalformedURLException {

		ServiceInstance si = null;

		if (connections.containsKey(esxDesc.vCenter)) {
			si = connections.get(esxDesc.vCenter);
			if (!checkConnection(si, esxDesc)) {
				connections.remove(esxDesc.vCenter);
				si = null;
			}
		}

		if (si == null) {
			si = createNewConnection(esxDesc);
			connections.put(esxDesc.vCenter, si);
		}

		return si;
	}


	private static boolean checkConnection(ServiceInstance si, EsxHypervisorDescription esxDesc) {
		try {
			si.getEventManager().getLatestEvent();
			Folder rootFolder = si.getRootFolder();
			Datacenter dc = (Datacenter) new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter")[0];
			ResourcePool rp = (ResourcePool) new InventoryNavigator(dc).searchManagedEntities("ResourcePool")[0];
			return true;
		} catch (Exception e) {
			log.info("Connection died to vCenter: " + esxDesc.vCenter);
			return false;
		}
	}

	public static void closeAllConnections() {
		for (String vCenter : connections.keySet()) {
			try {
				log.info("Close connection to " + vCenter);
				connections.get(vCenter).getServerConnection().logout();
			} catch (Exception e) {
				log.debug("Error closing connection for vCenter: " + vCenter);
			}
		}
		connections.clear();
	}


	public static HostSystem getHost(EsxHypervisorDescription esxDesc) throws RemoteException, MalformedURLException {
		ServiceInstance si = getConnection(esxDesc);

		HostSystem host = (HostSystem) si.getSearchIndex().findByIp(null, esxDesc.esxHost, false);

		if (host == null) {
			ManagedEntity[] hosts = new InventoryNavigator(si.getRootFolder()).searchManagedEntities("HostSystem");
			for (ManagedEntity host1 : hosts) {
				if (host1.getName().equals(esxDesc.esxHost)) {
					host = (HostSystem) host1;
					break;
				}
			}
		}
		if (host != null) {
			return host;
		} else {
			throw new RuntimeException("Host " + esxDesc.esxHost + " not found");
		}
	}

}
