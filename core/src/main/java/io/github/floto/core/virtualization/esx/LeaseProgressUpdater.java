package io.github.floto.core.virtualization.esx;

import com.vmware.vim25.mo.HttpNfcLease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaseProgressUpdater extends Thread {
	private HttpNfcLease httpNfcLease = null;
	private int progressPercent = 0;
	private int updateInterval;

	public LeaseProgressUpdater(HttpNfcLease httpNfcLease, int updateInterval) {
		this.httpNfcLease = httpNfcLease;
		this.updateInterval = updateInterval;
	}

	public void run() {
		while (true) {
			try {
				httpNfcLease.httpNfcLeaseProgress(progressPercent);
				Thread.sleep(updateInterval);
			} catch (InterruptedException ie) {
				break;
			} catch (Exception ignored) {
			}
		}
	}

	public void setPercent(int percent) {
		this.progressPercent = percent;
	}
}
