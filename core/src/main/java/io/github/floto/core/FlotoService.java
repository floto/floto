package io.github.floto.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Throwables;
import io.github.floto.core.proxy.HttpProxy;
import io.github.floto.core.ssh.SshService;
import io.github.floto.core.tasks.ManifestTask;
import io.github.floto.core.util.DockerfileHelper;
import io.github.floto.core.util.ErrorClientResponseFilter;
import io.github.floto.core.util.MavenHelper;
import io.github.floto.core.util.TemplateUtil;
import io.github.floto.dsl.FlotoDsl;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Image;
import io.github.floto.dsl.model.Manifest;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;

public class FlotoService implements Closeable {
    private Logger log = LoggerFactory.getLogger(FlotoService.class);

	private FlotoDsl flotoDsl = new FlotoDsl();
	private File rootDefinitionFile;
	private String manifestString;
	private Manifest manifest = new Manifest();
	private SshService sshService = new SshService();
    private int proxyPort = 40005;
    private File flotoHome = new File(System.getProperty("user.home") + "/.floto");
    private final boolean useProxy;
    private String httpProxyUrl;
    private HttpProxy proxy;

    private Map<String, String> externalHostIpMap = new HashMap<>();

	private Client client;
	{
        ClientBuilder clientBuilder = JerseyClientBuilder.newBuilder();
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(new ApacheConnectorProvider());
        clientBuilder.withConfig(clientConfig);
        client = clientBuilder.build();
		client.register(new ErrorClientResponseFilter());
	}

	private boolean buildOutputDumpEnabled = false;

	public FlotoService(FlotoCommonParameters commonParameters) {
		this.rootDefinitionFile = new File(commonParameters.rootDefinitionFile).getAbsoluteFile();
        this.useProxy = !commonParameters.noProxy;
        try {
            this.manifestString = new ObjectMapper().writer().writeValueAsString(manifest);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
        if(this.useProxy) {
            proxy = new HttpProxy(proxyPort);
            proxy.start();
            try {
                String ownAddress = Inet4Address.getLocalHost().getHostAddress();
                if(ownAddress.startsWith("127.")) {
                    Enumeration e = NetworkInterface.getNetworkInterfaces();
                    while (e.hasMoreElements()) {
                        NetworkInterface n = (NetworkInterface) e.nextElement();
                        if(n.getDisplayName().startsWith("eth")) {
                            Enumeration ee = n.getInetAddresses();
                            while (ee.hasMoreElements()) {
                                InetAddress i = (InetAddress) ee.nextElement();
                                if(i instanceof Inet4Address) {
                                    ownAddress = i.getHostAddress();
                                    break;
                                }
                            }
                        }
                    }
                }
                log.info("Using proxy address: {}", ownAddress);
                httpProxyUrl = "http://" + ownAddress + ":" + proxyPort + "/";
                flotoDsl.setGlobal("httpProxy", httpProxyUrl);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }

        }
	}

	public void compileManifest() {
		log.info("Compiling manifest");
		String manifestString = flotoDsl.generateManifestString(rootDefinitionFile);
		manifest = flotoDsl.toManifest(manifestString);
        this.manifestString = manifestString;
		log.info("Compiled manifest");
	}

	public String getManifestString() {
		return manifestString;
	}

	public void redeployContainers(List<String> containers) {
		log.info("Redeploying containers {}", containers);
		containers.forEach(this::redeployContainer);
	}

	private void redeployContainer(String containerName) {
		File buildLogDirectory = new File(flotoHome, "buildLog");
		Manifest manifest = this.manifest;
		try {
			FileUtils.forceMkdir(buildLogDirectory);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		try (FileOutputStream buildLogStream = new FileOutputStream(getContainerBuildLogFile(containerName))) {
			Container container = findContainer(containerName, manifest);
			Image image = findImage(container.image, manifest);
			Host host = findHost(container.host, manifest);

			// Build base image
			String baseImageName = image.name + "-image";
			buildImage(baseImageName, image.buildSteps, host, manifest, Collections.emptyMap(), buildLogStream);

			// Build configured image
			List<JsonNode> configureSteps = new ArrayList<>(container.configureSteps);
			ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode().put("type", "FROM").put("line", baseImageName);

			configureSteps.add(0, fromBuildStep);
			Map<String, Object> globalConfig = createGlobalConfig(manifest);
			buildImage(container.name, configureSteps, host, manifest, globalConfig, buildLogStream);

			// destroy old container
			destroyContainer(container.name, host);

			// create container
			createContainer(container, host);

			// start container
			startContainer(containerName);
			log.info("Redeployed container {}", container.name);

		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	public Map<String, Object> createGlobalConfig(Manifest manifest) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> siteMap = mapper.reader(Map.class).readValue(manifest.site);
			HashMap<String, Object> globalConfig = new HashMap<>();
			globalConfig.put("site", siteMap);
			return globalConfig;
		} catch (IOException e) {
			throw Throwables.propagate(e);
		}
	}

	private File getContainerBuildLogFile(String containerName) {
		return new File(flotoHome, "buildLog/" + containerName);
	}

	private void startContainer(Image image, Container container, Host host) {
		WebTarget dockerTarget = createDockerTarget(host);
		Map<String, Object> startConfig = new HashMap<>();

		// Host networking
		startConfig.put("NetworkMode", "host");

		// Host mount directories
		ArrayList<String> binds = new ArrayList<>();
		for (Map.Entry<String, String> entry : getContainerVolumes(image, container).entrySet()) {
			binds.add(entry.getKey() + ":" + entry.getValue());
		}
		startConfig.put("Binds", binds);

		Response startResponse = dockerTarget.path("/containers/" + container.name + "/start").request().post(Entity.entity(startConfig, MediaType.APPLICATION_JSON_TYPE));
		startResponse.close();
	}

	private Map<String, String> getContainerVolumes(Image image, Container container) {
		HashMap<String, String> volumeMap = new HashMap<>();
		ArrayList<String> binds = new ArrayList<>();
		ArrayList<JsonNode> steps = new ArrayList<>(image.buildSteps);
		steps.addAll(container.configureSteps);
		for (JsonNode step : steps) {
			String type = step.path("type").asText();
			if (type.equals("VOLUME")) {
				String path = step.path("path").asText();
				String name = step.path("name").asText();
				volumeMap.put("/data/" + container.name + "/" + name, path);
			}
		}
		return volumeMap;
	}

	private void createContainer(Container container, Host host) {
		WebTarget dockerTarget = createDockerTarget(host);
		Map<String, Object> createConfig = new HashMap<>();
		createConfig.put("Image", container.name);
		Response createResponse = dockerTarget.path("/containers/create").queryParam("name", container.name).request().post(Entity.entity(createConfig, MediaType.APPLICATION_JSON_TYPE));
		createResponse.close();
	}

	private void destroyContainer(String containerName, Host host) {
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response killResponse = dockerTarget.path("/containers/" + containerName + "/kill").request().post(Entity.text(""));
			killResponse.close();
			Response removeResponse = dockerTarget.path("/containers/" + containerName).request().delete();
			removeResponse.close();
		} catch (Throwable t) {
			if (t.getMessage().contains("404")) {
				// Not there - ignore
			} else {
				throw new RuntimeException("Unable to destroy container " + containerName, t);
			}
		}
	}

	private void buildImage(String imageName, List<JsonNode> buildSteps, Host host, Manifest manifest, Map<String, Object> globalConfig, OutputStream buildLogStream) {
		PrintWriter buildLogWriter = new PrintWriter(buildLogStream);
		String dockerFile = createDockerFile(buildSteps);

		WebTarget dockerTarget = createDockerTarget(host);
		WebTarget buildTarget = dockerTarget.path("build");
		Response response = buildTarget.queryParam("t", imageName).request().post(Entity.entity(new StreamingOutput() {
			@Override
			public void write(OutputStream outputStream) throws IOException, WebApplicationException {
				try (TarOutputStream out = new TarOutputStream(outputStream)) {
					out.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
					TarEntry tarEntry = new TarEntry("Dockerfile");
					byte[] bytes = dockerFile.getBytes();
					tarEntry.setSize(bytes.length);
					out.putNextEntry(tarEntry);
					out.write(bytes);
					out.closeEntry();
					for (JsonNode step : buildSteps) {
						String type = step.path("type").asText();
						if ("ADD_TEMPLATE".equals(type)) {
							String destination = step.path("destination").asText();
							String source = destination;
							if (source.startsWith("/")) {
								source = source.substring(1);
							}
							String templated = new TemplateUtil().getTemplate(step, globalConfig);
							TarEntry templateTarEntry = new TarEntry(source);
							templateTarEntry.setModTime(0);
							templateTarEntry.setSize(templated.length());
							out.putNextEntry(templateTarEntry);
							IOUtils.write(templated, out);
							out.closeEntry();
						} else if ("ADD_MAVEN".equals(type)) {
							String coordinates = step.path("coordinates").asText();
							String destination = step.path("destination").asText();
							if (coordinates.contains(":tar.gz:")) {
								destination = destination + ".x";
							}
							if (destination.startsWith("/")) {
								destination = destination.substring(1);
							}

                            JsonNode repositories = manifest.site.findPath("maven").findPath("repositories");
                            File artifactFile = new MavenHelper(repositories).resolveMavenDependency(coordinates);
							TarEntry templateTarEntry = new TarEntry(artifactFile, destination);
							out.putNextEntry(templateTarEntry);
							FileUtils.copyFile(artifactFile, out);
							out.closeEntry();
                        } else if ("COPY_FILES".equals(type)) {
                            JsonNode fileList = step.path("fileList");
                            List<String> files = new ArrayList<String>();
                            Iterator<Map.Entry<String, JsonNode>> iterator = fileList.fields();
                            while(iterator.hasNext()) {
                                files.add(iterator.next().getKey());
                            }
                            String destination = step.path("destination").asText();

                            for (String filename : files) {
                                File file = new File(filename);
                                TarEntry templateTarEntry = new TarEntry(file, destination+filename);
                                out.putNextEntry(templateTarEntry);
                                FileUtils.copyFile(file, out);
                                out.closeEntry();
                            }

                        } else if ("COPY_DIRECTORY".equals(type)) {
                            String source = step.path("source").asText();
                            File sourceFile = new File(source);
                            Collection<File> files;
                            if(sourceFile.isDirectory()) {
                                files = FileUtils.listFiles(sourceFile, FileFileFilter.FILE, TrueFileFilter.INSTANCE);
                            } else {
                                files = Collections.singleton(sourceFile);
                            }
                            String destination = step.path("destination").asText();

                            for (File file : files) {
                                TarEntry templateTarEntry = new TarEntry(file, destination+file.getAbsolutePath().replaceAll("^.:", "").replaceAll("\\\\","/"));
                                out.putNextEntry(templateTarEntry);
                                FileUtils.copyFile(file, out);
                                out.closeEntry();
                            }



                        }
					}

					out.close();
				}

			}
		}, "application/tar"));
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			InputStream inputStream = (InputStream) response.getEntity();
			CloseShieldInputStream shieldInputStream = new CloseShieldInputStream(inputStream);
			MappingIterator<JsonNode> entries = objectMapper.reader(JsonNode.class).readValues(shieldInputStream);
			while (entries.hasNext()) {
				JsonNode node = entries.next();
				JsonNode errorNode = node.get("error");
				if (errorNode != null) {
					String errorText = errorNode.asText();
					buildLogWriter.println();
					buildLogWriter.println();
					buildLogWriter.println(errorText);
					if(buildOutputDumpEnabled) {
						System.out.println();
						System.out.println(errorText);
					}
					throw new RuntimeException("Error building image: " + errorText);
				}
				JsonNode streamNode = node.get("stream");
				if (streamNode != null) {
					String text = streamNode.asText();
					buildLogWriter.print(text);
					if(buildOutputDumpEnabled) {
						System.out.print(text);
					}
				}
                buildLogWriter.flush();
			}
			// Drain outputstream, wait for build completion
			IOUtils.copy(inputStream, System.out);
		} catch (IOException e) {
			Throwables.propagate(e);
		} finally {
			buildLogWriter.flush();
			response.close();
		}
	}

    private String createDockerFile(List<JsonNode> buildSteps) {
        DockerfileHelper dockerfileHelper = new DockerfileHelper();
        if(useProxy) {
            dockerfileHelper.setHttpProxy(httpProxyUrl);
        }
        return dockerfileHelper.createDockerfile(buildSteps);
    }

    private void tarFilesToOutputStream(List<String> files, OutputStream out) {
        try {
            TarOutputStream tarOutputStream = new TarOutputStream(out);
            for (String filename : files) {
                File file = new File(filename);
                TarEntry templateTarEntry = new TarEntry(file);
                tarOutputStream.putNextEntry(templateTarEntry);
                FileUtils.copyFile(file, out);
                tarOutputStream.closeEntry();
            }
            tarOutputStream.close();
        } catch(Exception e) {
            Throwables.propagate(e);
        }
    }

    private WebTarget createDockerTarget(Host host) {
		return client.target("http://" + getExternalHostIp(host) + ":2375");
	}

    public void setExternalHostIp(String hostName, String ip) {
        externalHostIpMap.put(hostName, ip);
    }

    public String getExternalHostIp(Host host) {
        String ip = externalHostIpMap.get(host.name);
        if(ip == null) {
            ip = host.ip;
        }
        return ip;
    }

	private Image findImage(String imageName, Manifest manifest) {
		for (Image candidate : manifest.images) {
			if (imageName.equals(candidate.name)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Unknown image: " + imageName);
	}

	private Host findHost(String hostName, Manifest manifest) {
        return manifest.findHost(hostName);
	}

	private Container findContainer(String containerName, Manifest manifest) {
		for (Container candidate : manifest.containers) {
			if (containerName.equals(candidate.name)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Unknown container: " + containerName);
	}

	public void stopContainers(List<String> containers) {
		containers.forEach(runContainerCommand("stop"));
	}

	public void restartContainers(List<String> containers) {
		containers.forEach(runContainerCommand("restart"));
	}

	public void startContainers(List<String> containers) {
		containers.forEach(this::startContainer);
	}

	private void startContainer(String containerName) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Host host = findHost(container.host, manifest);
		Image image = findImage(container.image, manifest);
		startContainer(image, container, host);
	}

	private Consumer<? super String> runContainerCommand(String command) {
		return (String containerName) -> {
			Manifest manifest = this.manifest;
			Container container = findContainer(containerName, manifest);
			Host host = findHost(container.host, manifest);
			WebTarget dockerTarget = createDockerTarget(host);
			try {
				Response response = dockerTarget.path("/containers/" + containerName + "/" + command).request().post(Entity.text(""));
				response.close();
			} catch (Throwable t) {
				Throwables.propagate(t);
			}
		};
	}

	public Map<String, String> getContainerStates() {
		HashMap<String, String> states = new HashMap<>();
		Manifest manifest = this.manifest;
		for (Host host : manifest.hosts) {
			WebTarget dockerTarget = createDockerTarget(host);
			try {
				JsonNode response = dockerTarget.path("/containers/json").queryParam("all", true).request().buildGet().submit(JsonNode.class).get();
				for (JsonNode container : response) {
					for (JsonNode nameNode : container.get("Names")) {
						String name = nameNode.textValue();
						name = name.substring(1);
						String status = container.get("Status").textValue();
						String state = "stopped";
						if (status.startsWith("Up ")) {
							state = "running";
						}
						states.put(name, state);
					}
				}

			} catch (Throwable ignored) {
                log.warn("Error getting container state for host {}: {}", host.name, ignored.getMessage());
			}


		}
		return states;
	}

	public void purgeContainerData(List<String> containers) {
		containers.forEach(this::purgeContainerData);
	}

	private void purgeContainerData(String containerName) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Host host = findHost(container.host, manifest);
		String dataDirectory = "/data/" + container.name;
		log.info("Deleting " + dataDirectory);
		sshService.execute(getExternalHostIp(host), "sudo rm -rf " + dataDirectory);
	}

	public String getDockerfile(String containerName, String type) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		List<JsonNode> buildSteps;
		if ("container".equals(type)) {
			String baseImageName = container.image + "-image";
			buildSteps = new ArrayList<>(container.configureSteps);
			ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode().put("type", "FROM").put("line", baseImageName);

			buildSteps.add(0, fromBuildStep);
		} else if ("image".equals(type)) {
			Image image = findImage(container.image, manifest);
			buildSteps = image.buildSteps;
		} else {
			throw new IllegalArgumentException("Unknown image type " + type);
		}
		String dockerFile = createDockerFile(buildSteps);
		return dockerFile;
	}

	public void getLog(String containerName, OutputStream output) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Host host = findHost(container.host, manifest);

		WebTarget dockerTarget = createDockerTarget(host);

		try (InputStream inputStream = dockerTarget.path("/containers/" + containerName + "/logs")
				.queryParam("stdout", true)
				.queryParam("stderr", true)
				.queryParam("timestamps", false)
				.request().buildGet().invoke(InputStream.class)) {
			DataInputStream dataInputStream = new DataInputStream(inputStream);
			while (true) {
				int flags = dataInputStream.readInt();
				int size = dataInputStream.readInt();
				IOUtils.copyLarge(inputStream, output, 0, size);
			}
		} catch (EOFException expected) {

		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	public void getBuildLog(String containerName, OutputStream output) {
		try (FileInputStream input = new FileInputStream(getContainerBuildLogFile(containerName))) {
			IOUtils.copy(input, output);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	public String getTemplate(String containerName, String path) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Image image = findImage(container.image, manifest);
		List<JsonNode> steps = new ArrayList<>();
		steps.addAll(container.configureSteps);
		steps.addAll(image.buildSteps);
		JsonNode templateStep = null;
		for (JsonNode step : steps) {
			String type = step.path("type").asText();
			if ("ADD_TEMPLATE".equals(type)) {
				String destination = step.path("destination").asText();
				if (destination.equals(path)) {
					templateStep = step;
					break;
				}

			}
		}
		if (templateStep == null) {
			throw new IllegalArgumentException("Template path not found: " + path);
		}
		Map<String, Object> globalConfig = createGlobalConfig(manifest);
		return new TemplateUtil().getTemplate(templateStep, globalConfig);
	}

	public Manifest getManifest() {
		return manifest;
	}

	public void enableBuildOutputDump(boolean enabled) {
		buildOutputDumpEnabled = enabled;
	}

    public String getHostTemplate(String hostName, String path) {
        Manifest manifest = this.manifest;
        Host host = findHost(hostName, manifest);
        JsonNode templateStep = null;
        // TODO: Refactor (getTemplate())
        for (JsonNode step : host.postDeploySteps) {
            String type = step.path("type").asText();
            if ("ADD_TEMPLATE".equals(type)) {
                String destination = step.path("destination").asText();
                if (destination.equals(path)) {
                    templateStep = step;
                    break;
                }

            }
        }
        if (templateStep == null) {
            throw new IllegalArgumentException("Template path not found: " + path);
        }
        Map<String, Object> globalConfig = createGlobalConfig(manifest);
        return new TemplateUtil().getTemplate(templateStep, globalConfig);
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(proxy);
    }

    public void verifyTemplates() {
        try {
            new ManifestTask<Void>(manifest) {
                
                @Override
                public Void execute() throws Exception {
                    for(Image image: manifest.images) {
                        verifyTemplates(image.buildSteps);             
                    }

                    for(Container container: manifest.containers) {
                        verifyTemplates(container.configureSteps);
                    }

                    for(Host host: manifest.hosts) {
                        verifyTemplates(host.postDeploySteps);
                        verifyTemplates(host.reconfigureSteps);
                    }
                    return null;
                }

                private void verifyTemplates(Iterable<JsonNode> steps) {
                    Map<String, Object> globalConfig = createGlobalConfig(manifest);
                    for (JsonNode step : steps) {
                        String type = step.path("type").asText();
                        if ("ADD_TEMPLATE".equals(type)) {
                            new TemplateUtil().getTemplate(step, globalConfig);
                        }
                    }
                }
            }.execute();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
}
