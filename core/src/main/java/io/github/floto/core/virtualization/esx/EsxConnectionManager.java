package io.github.floto.core.virtualization.esx;

import io.github.floto.dsl.model.EsxHypervisorDescription;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;

public class EsxConnectionManager {
	
	private static Logger log = LoggerFactory.getLogger(EsxConnectionManager.class);
	
	private static Map<EsxHypervisorDescription, ServiceInstance> connections = new HashMap<EsxHypervisorDescription, ServiceInstance>();

	private EsxConnectionManager() {
	}

	private static ServiceInstance createNewConnection(
			EsxHypervisorDescription esxDesc) throws RemoteException,
			MalformedURLException {
		
		log.info("create new connection for vcenter:" + esxDesc.vCenter + ", esx:" + esxDesc.esxHost);
		
		ServiceInstance si = new ServiceInstance(new URL("https://"
				+ esxDesc.vCenter + "/sdk"), esxDesc.username,
				esxDesc.password, true);
		
		return si;
	}

	public static ServiceInstance getConnection(
			EsxHypervisorDescription esxDesc) throws RemoteException,
			MalformedURLException {

		ServiceInstance si = null;

		if (connections.containsKey(esxDesc)) {
			si = connections.get(esxDesc);
			if (!checkConnection(si, esxDesc)) {
				connections.remove(esxDesc);
				si = null;
			}
		}

		if (si == null) {
			si = createNewConnection(esxDesc);
			connections.put(esxDesc, si);
		}

		return si;
	}


	private static boolean checkConnection(ServiceInstance si, EsxHypervisorDescription esxDesc) {
		try {
			si.getEventManager().getLatestEvent();
			return true;
		} catch (Exception e) {
			log.info("connection died to vcenter:" + esxDesc.vCenter + ", esx:" + esxDesc.esxHost);
			return false;
		}
	}
	
	public static void closeAllConnections() {
		for (EsxHypervisorDescription desc : connections.keySet()) {
			try {
				connections.get(desc).getServerConnection().logout();
			} catch (Exception e) {
				log.debug("error closing connection for vcenter:" + desc.vCenter + ", esx:" + desc.esxHost);
			}
		}
		connections.clear();
	}

	
	public static HostSystem getHost(EsxHypervisorDescription esxDesc) throws RemoteException, MalformedURLException {
		ServiceInstance si = getConnection(esxDesc);
		
        HostSystem host = (HostSystem) si.getSearchIndex().findByIp(null, esxDesc.esxHost, false);
        
        if (host == null) {
            ManagedEntity[] hosts = new InventoryNavigator(si.getRootFolder()).searchManagedEntities("HostSystem");
            for(int i=0; i<hosts.length; i++) {
            	if (hosts[i].getName().equals(esxDesc.esxHost)) {
            		host = (HostSystem)hosts[i];
            		break;
            	}
            }
        } 
        
        if (host != null) {
            return host;
        } else {        
        	throw new RuntimeException("host " + esxDesc.esxHost + " not found");
        }
	}
	
}
