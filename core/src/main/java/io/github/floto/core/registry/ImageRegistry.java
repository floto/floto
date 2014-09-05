package io.github.floto.core.registry;


public class ImageRegistry {
	
	private String containerName;
	private String ip;
	private int port;
	
	public ImageRegistry(String name, String ip, int port) {
		this.containerName = name;
		this.ip = ip;
		this.port = port;
	}

	public String getContainerName() {
		return containerName;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}
}
