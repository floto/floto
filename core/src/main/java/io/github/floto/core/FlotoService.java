package io.github.floto.core;

import com.google.common.collect.Sets;
import io.github.floto.core.jobs.HostJob;
import io.github.floto.core.jobs.ManifestJob;
import io.github.floto.core.proxy.HttpProxy;
import io.github.floto.core.registry.ImageRegistry;
import io.github.floto.core.ssh.SshService;
import io.github.floto.core.util.DockerfileHelper;
import io.github.floto.core.util.ErrorClientResponseFilter;
import io.github.floto.core.util.MavenHelper;
import io.github.floto.core.util.TemplateUtil;
import io.github.floto.dsl.FlotoDsl;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Image;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Maps;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;

public class FlotoService implements Closeable {
	private Logger log = LoggerFactory.getLogger(FlotoService.class);

	private FlotoDsl flotoDsl = new FlotoDsl();
	private File rootDefinitionFile;
	private String environment;
	private String manifestString;
	private Manifest manifest = new Manifest();
	private SshService sshService = new SshService();
	private int proxyPort = 40005;
	private File flotoHome = new File(System.getProperty("user.home") + "/.floto");
	private boolean useProxy;
	private String httpProxyUrl;
	private HttpProxy proxy;

	private Map<String, String> externalHostIpMap = new HashMap<>();
    Set<String> DEPLOYMENT_CONTAINER_NAMES = Sets.newHashSet("registry", "floto");


//	private ImageRegistry imageRegistry;

	public enum DeploymentMode {
		fromRootImage, fromBaseImage, containerRebuild
	}

	private Client client;
	{
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.property(ClientProperties.READ_TIMEOUT, 0);
		clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 2000);
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(100);
		connectionManager.setDefaultMaxPerRoute(20);
		clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);

		ClientBuilder clientBuilder = JerseyClientBuilder.newBuilder();
		clientConfig.connectorProvider(new ApacheConnectorProvider());
		clientBuilder.withConfig(clientConfig);
		client = clientBuilder.build();
		client.register(new ErrorClientResponseFilter());
	}

	private boolean buildOutputDumpEnabled = false;
	private TaskService taskService;

	// for unit-tests only
	FlotoService() {}

	public FlotoService(FlotoCommonParameters commonParameters, TaskService taskService) {
		this.taskService = taskService;
		this.rootDefinitionFile = new File(commonParameters.rootDefinitionFile).getAbsoluteFile();
		this.environment = commonParameters.environment;
		this.useProxy = !commonParameters.noProxy;
		try {
			this.manifestString = new ObjectMapper().writer().writeValueAsString(manifest);
		} catch (JsonProcessingException e) {
			throw Throwables.propagate(e);
		}
		if (this.useProxy) {
			proxy = new HttpProxy(proxyPort);
			proxy.setCacheDirectory(new File(flotoHome, "cache/http"));
			proxy.start();
			try {
				String ownAddress = commonParameters.proxyUrl;
				if (ownAddress == null || ownAddress.isEmpty()) {
					try {
						ownAddress = Inet4Address.getLocalHost().getHostAddress();
					} catch (Throwable throwable) {
						log.warn("Unable to get own address", throwable);
					}
					if (ownAddress == null || ownAddress.startsWith("127.")) {
						Enumeration e = NetworkInterface.getNetworkInterfaces();
						while (e.hasMoreElements()) {
							NetworkInterface n = (NetworkInterface) e.nextElement();
							if (n.getDisplayName().startsWith("eth") || n.getDisplayName().startsWith("wlan")) {
								List<InetAddress> addresses = Collections.list(n.getInetAddresses());
								// Force deterministic address order, highest first
								addresses.sort(new Comparator<InetAddress>() {
									@Override
									public int compare(InetAddress o1, InetAddress o2) {
										return -o1.getHostAddress().compareTo(o2.getHostAddress());
									}
								});
                                if(commonParameters.proxyPrefix != null) {
                                    // Use first address with prefix
                                    for(InetAddress address: addresses) {
                                        if(address.getHostAddress().startsWith(commonParameters.proxyPrefix)) {
                                            ownAddress = address.getHostAddress();
                                            break;
                                        }
                                    }
                                } else {
                                    // Use first ipv4 address
                                    for (InetAddress address : addresses) {
                                        if (address instanceof Inet4Address) {
                                            ownAddress = address.getHostAddress();
                                            break;
                                        }
                                    }
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

	public TaskInfo<Void> compileManifest() {
		return taskService.startTask("Compile manifest", () -> {
			log.info("Compiling manifest");
			String manifestString = flotoDsl.generateManifestString(rootDefinitionFile, environment);
			manifest = flotoDsl.toManifest(manifestString);
            ImageRegistry imageRegistry = this.getImageRegistry();
            boolean useRegistry = imageRegistry != null;
            if (useRegistry) {
                log.info("Will use image-registry=" + imageRegistry);
                // manipulate FROM instructions to point to registry images
                manifest.images.forEach(new Consumer<Image>() {
                    @Override
                    public void accept(Image image) {
                        if (!DEPLOYMENT_CONTAINER_NAMES.contains(image.name)) {
                            String originalRootImageName = getRootImage(image);
                            setRootImage(image, constructPrivateImageName(originalRootImageName));
                        }
                    }
                });
            } else {
                log.info("Will run without image-registry");
            }

            this.manifestString = manifestString;
			log.info("Compiled manifest");
			validateTemplates();
			return null;
		});
	}

	public String getManifestString() {
		return manifestString;
	}

	public TaskInfo<Void> redeployContainers(List<String> requestedContainers, DeploymentMode deploymentMode) {
		final List<String> containers = new ArrayList<>(requestedContainers);
		return taskService.startTask("Redeploy containers "	+ Joiner.on(", ").join(requestedContainers) + " in mode '" + deploymentMode + "'", () -> {
			boolean excludeDeploymentContainers = false;
			String registryContainerName = null;
			if(this.getImageRegistry() != null) {
				Host registryHost = this.findRegistryHost(this.manifest);

				// Check that registry is running, if we are going to use it
				// TODO: only get registry state
				registryContainerName = this.getImageRegistry().getContainerName();
				ContainerState containerState = getContainerStates().get(registryContainerName);
				if(containerState != null && containerState.status.equals(ContainerState.Status.running)) {
					// Registry is running, go ahead
				} else {
					// Deploy registry first
					log.info("Deploying registry first");
					containers.remove(registryContainerName);
					containers.add(0, registryContainerName);
				}

				// Compare hostnames
				if(registryHost.name.equals(InetAddress.getLocalHost().getHostName())) {
					// Exclude deployment containers from being redeployed from their own hosts, prevent bricking the system
					excludeDeploymentContainers = true;
				};
			}

			int numberOfContainersDeployed = 0;

			for (String containerName : containers) {
				if(excludeDeploymentContainers && DEPLOYMENT_CONTAINER_NAMES.contains(containerName)) {
					log.warn("Excluding container {} from deployment to prevent rendering system unusable", containerName);
					continue;
				}

				log.info("Will deploy container='{}'", containerName);
				boolean useRegistry = this.getImageRegistry() != null;
				if(DEPLOYMENT_CONTAINER_NAMES.contains(containerName)) {
					// Deployment container - Do not use registry
					useRegistry = false;
				}


				File buildLogDirectory = new File(flotoHome, "buildLog");
				try {
					FileUtils.forceMkdir(buildLogDirectory);
				} catch (IOException e) {
					Throwables.propagate(e);
				}
				Container container = this.findContainer(containerName, this.manifest);
				// In bootstrap mode only deploy to registry host to upload images
				boolean isBootStrapMode = false;
				try (FileOutputStream buildLogStream = new FileOutputStream(getContainerBuildLogFile(containerName))) {
					Image image = this.findImage(container.image, this.manifest);
					Host host = isBootStrapMode ? this.findRegistryHost(this.manifest) : this.findHost(container.host, this.manifest);
					if(containerName.equals(registryContainerName)) {
						// Deploy registry always from root, always from public registry
						this.redeployFromRootImage(host, container,  buildLogStream, false);
					} else if (DeploymentMode.fromRootImage.equals(deploymentMode)) {
						this.redeployFromRootImage(host, container, buildLogStream, useRegistry);
					} else if (DeploymentMode.fromBaseImage.equals(deploymentMode)) {
						String baseImageName = useRegistry ? this.constructPrivateImageName(this.createBaseImageName(image)) : this.createBaseImageName(image);
						this.redeployFromBaseImage(host, container, baseImageName, buildLogStream);
					} else if (DeploymentMode.containerRebuild.equals(deploymentMode)) {
						this.rebuildContainer(container, true);
					} else {
						throw new IllegalStateException("Unknown deploymentMode=" + deploymentMode);
					}
					numberOfContainersDeployed++;
				} catch (Throwable t) {
					Throwables.propagate(t);
				}
				if(containerName.equals(registryContainerName)) {
					// Registry container - deploy root images

					// registry root image is already in docker at this point
					String registryRootImage = this.getRootImage(container.image);
					Host registryHost = this.findRegistryHost(this.manifest);
					this.pushToRegistry(registryHost, registryRootImage, true, false, true);
					log.info("Uploading root image {} to registry", registryRootImage);
					// Also push the additional root-images
					this.manifest.containers.stream().map(c -> this.getRootImage(c.image)).filter(ri -> !ri.equals(registryRootImage)).distinct()
                            .forEach(ri -> {
                                try {
                                    log.info("Download root image {} to registry host", ri);
                                    this.createImage(registryHost, ri);
                                    log.info("Uploading root image {} to registry", ri);
                                    this.pushToRegistry(registryHost, ri, true, false, false);
                                } catch (Throwable t) {
                                    throw Throwables.propagate(t);
								}
							});
				}
			}
			if(numberOfContainersDeployed == 0) {
				throw new IllegalStateException("No containers were deployed");
			}
			return null;
		});
	}

	public TaskInfo<Void> redeployDeployerContainer(Host host, Container container, boolean usePrivateRootImage, boolean pushRootImage, boolean pushBaseImage,
													boolean createContainer, boolean deleteCreatedImages, boolean startContainer) {

		File buildLogDirectory = new File(flotoHome, "buildLog");
		List<String> images2Delete = Lists.newArrayList();
		try {
			FileUtils.forceMkdir(buildLogDirectory);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		try (FileOutputStream buildLogStream = new FileOutputStream(getContainerBuildLogFile(container.name))) {

			// Create Base-Image
			String baseImageName = this.createBaseImage(host, container, buildLogStream);
			if (pushBaseImage) {
				baseImageName = this.pushToRegistry(host, baseImageName, true, false, false);
				if (deleteCreatedImages && !createContainer) {
					images2Delete.add(baseImageName);
				}
			}
			// Create Final image
			this.createFinalImage(host, container, baseImageName, buildLogStream);

			if (createContainer) {
				this.rebuildContainer(container, startContainer);
			}

			if (pushRootImage) {
				this.pushToRegistry(host, this.getRootImage(container.image), true, false, true);
				// Also push the additional root-images
				this.manifest.containers.stream().map(c -> this.getRootImage(c.image)).filter(ri -> !ri.equals(this.getRootImage(container.image))).collect(Collectors.toSet()).stream()
                        .forEach(ri -> {
                            try {
                                this.createImage(host, ri);
                                this.pushToRegistry(host, ri, true, false, false);
                            } catch (Throwable t) {
                                throw Throwables.propagate(t);
							}
						});
			}

			images2Delete.stream().forEach(s -> this.deleteImage(host, s));

			log.info("Redeployed container {}", container.name);

		} catch (Throwable t) {
			Throwables.propagate(t);
		}
		return null;

	}

	private void redeployFromRootImage(Host host, Container container, FileOutputStream buildLogStream, boolean pushImage) throws Exception {
		String baseImageName = this.createBaseImage(host, container, buildLogStream);
		if (pushImage) {
			baseImageName = this.pushToRegistry(host, baseImageName, true, false, false);
		}
		this.redeployFromBaseImage(host, container, baseImageName, buildLogStream);
	}

	private void redeployFromBaseImage(Host host, Container container, String baseImageName, FileOutputStream buildLogStream) throws Exception {
		this.createFinalImage(host, container, baseImageName, buildLogStream);
		this.rebuildContainer(container, true);
	}

	private void rebuildContainer(Container container, boolean startContainer) throws Exception {
		Host executingHost = this.findHost(container.host, this.manifest);
		destroyContainer(container.name, executingHost);
		createContainer(container, executingHost, container.name);
		if(startContainer) {
			startContainer(container.name);
		}
	}

	private String createBaseImage(Host host, Container container, FileOutputStream buildLogStream) {
		Image image = findImage(container.image, this.manifest);
        log.info("Will use root-image={}", this.getRootImage(image));
        String baseImageName = this.createBaseImageName(image);
        this.buildImage(baseImageName, image.buildSteps, host, this.manifest, Collections.emptyMap(), buildLogStream);
        return baseImageName;
	}

	private void createFinalImage(Host host, Container container, String baseImageName, FileOutputStream buildLogStream) {
		log.info("Will use base-image={}", baseImageName);
		List<JsonNode> configureSteps = new ArrayList<>(container.configureSteps);
		ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode().put("type", "FROM").put("line", baseImageName);

		configureSteps.add(0, fromBuildStep);
		Map<String, Object> globalConfig = createGlobalConfig(this.manifest);
		buildImage(container.name, configureSteps, host, this.manifest, globalConfig, buildLogStream);
	}

	private String pushToRegistry(Host host, String imageName, boolean deleteImage, boolean deleteImageInPrivateRegistry, boolean waitBeforePush) throws Exception {
		Pair<String, String> splitted = this.splitImageName(imageName);
		String name = splitted.getLeft();
		String tag = splitted.getRight();
		String privateName = this.constructPrivateImageName(name);
		log.info("Will tag/push='{}' as='{}', tag='{}'", imageName, privateName, tag);
		this.tagImage(host, imageName, privateName, tag);
		// Wait a bit to ensure, registry is up
		if (waitBeforePush) {
			Thread.sleep(5000L);
		}
		this.pushImage(host, imageName, privateName, tag);
		if (deleteImage) {
			this.deleteImage(host, imageName);
		}
		if (deleteImageInPrivateRegistry) {
			this.deleteImage(host, this.constructPrivateImageName(imageName));
		}
		return privateName;
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

		WebTarget dockerTarget = createDockerTarget(host).path("/containers/" + container.name + "/start");
		Map<String, Object> startConfig = Maps.newHashMap();

        // Set volume ownerships if necessary
        setVolumeOwnership(image, container, host);


		// Host mount directories
		ArrayList<String> binds = new ArrayList<>();
		for (Map.Entry<String, String> entry : getContainerVolumes(image, container).entrySet()) {
			binds.add(entry.getKey() + ":" + entry.getValue());
		}
		startConfig.put("Binds", binds);
		startConfig.put("Privileged", container.priviledged);
		startConfig.put("NetworkMode", "host");
		Map<String, String> restartPolicyMap = Maps.newHashMap();
		restartPolicyMap.put("Name", "always");
		startConfig.put("RestartPolicy", restartPolicyMap);

		Builder request = dockerTarget.request();
		Response response = request.post(Entity.json(startConfig));
		response.close();
	}

    private void setVolumeOwnership(Image image, Container container, Host host) {
        ArrayList<JsonNode> steps = new ArrayList<>(image.buildSteps);
        steps.addAll(container.configureSteps);
        for (JsonNode step : steps) {
            String type = step.path("type").asText();
            if (type.equals("VOLUME")) {
                String path = step.path("path").asText();
                String name = step.path("name").asText();
                JsonNode uidNode = step.path("options").path("uid");
                if (!uidNode.isMissingNode()) {
                    String uid = uidNode.asText();
                    String hostPath = "/data/" + container.name + "/" + name;
                    sshService.execute(getExternalHostIp(host), "sudo mkdir -p " + hostPath + " && sudo chown " + uid + " " + hostPath);
                }
            }
        }
    }

    private Map<String, String> getContainerVolumes(Image image, Container container) {
		HashMap<String, String> volumeMap = new HashMap<>();
		ArrayList<JsonNode> steps = new ArrayList<>(image.buildSteps);
		steps.addAll(container.configureSteps);
		for (JsonNode step : steps) {
			String type = step.path("type").asText();
			if (type.equals("VOLUME")) {
				String path = step.path("path").asText();
				String name = step.path("name").asText();
				volumeMap.put("/data/" + container.name + "/" + name, path);
			} else if (type.equals("MOUNT")) {
				String hostPath = step.path("hostPath").asText();
				String containerPath = step.path("containerPath").asText();
				volumeMap.put(hostPath, containerPath);
			}
		}
		return volumeMap;
	}

	private void createContainer(Container container, Host host, String imageName) {
		WebTarget dockerTarget = createDockerTarget(host).path("/containers/create").queryParam("name", container.name);
		Map<String, Object> createConfig = Maps.newHashMap();
		createConfig.put("Image", imageName);
		createConfig.put("PortSpecs", null);
		createConfig.put("ExposedPorts", Maps.newHashMap());

		Builder request = dockerTarget.request();
		Response response = request.post(Entity.json(createConfig));
		response.close();
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
							byte[] templateBytes = templated.getBytes(Charsets.UTF_8);
							templateTarEntry.setSize(templateBytes.length);
							out.putNextEntry(templateTarEntry);
							IOUtils.write(templateBytes, out);
							out.closeEntry();
						}else if ("ADD_FILE".equals(type)) {
							String destination = step.path("destination").asText();
							String source = destination;
							if (source.startsWith("/")) {
								source = source.substring(1);
							}
							File file = new File(step.path("file").asText());
							TarEntry fileTarEntry = new TarEntry(file, source);
							out.putNextEntry(fileTarEntry);
							FileUtils.copyFile(file, out);
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
						} else if ("ADD_MANIFEST_JSON".equals(type)) {
							String destination = step.path("destination").asText();
							TarEntry manifestTarEntry = new TarEntry(destination);
							out.putNextEntry(manifestTarEntry);
							byte[] manifestBytes = new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsBytes(manifest);
							manifestTarEntry.setSize(manifestBytes.length);
							out.putNextEntry(manifestTarEntry);
							IOUtils.write(manifestBytes, out);

							out.closeEntry();
						} else if ("COPY_FILES".equals(type)) {
							JsonNode fileList = step.path("fileList");
							List<String> files = new ArrayList<String>();
							Iterator<Map.Entry<String, JsonNode>> iterator = fileList.fields();
							while (iterator.hasNext()) {
								files.add(iterator.next().getKey());
							}
							String destination = step.path("destination").asText();

							for (String filename : files) {
								File file = new File(filename);
								TarEntry templateTarEntry = new TarEntry(file, destination + filename);
								out.putNextEntry(templateTarEntry);
								FileUtils.copyFile(file, out);
								out.closeEntry();
							}

						} else if ("COPY_DIRECTORY".equals(type)) {
							String source = step.path("source").asText();
							JsonNode options = step.path("options");
							JsonNode excludeDirectories = options.path("excludeDirectories");
							JsonNode excludeFiles = options.path("excludeFiles");
							JsonNode newName = options.path("newName");

							List<IOFileFilter> dirFilters = new ArrayList<IOFileFilter>();
							for (JsonNode node : excludeDirectories) {
								dirFilters.add(new NotFileFilter(new NameFileFilter(node.asText())));
							}
							IOFileFilter directoryFilter = new AndFileFilter(dirFilters);
							if (dirFilters.isEmpty()) {
								directoryFilter = TrueFileFilter.INSTANCE;
							}

							List<IOFileFilter> fileFilters = new ArrayList<IOFileFilter>();
							for (JsonNode node : excludeFiles) {
								fileFilters.add(new NotFileFilter(new NameFileFilter(node.asText())));
							}
							IOFileFilter fileFilter = new AndFileFilter(fileFilters);
							if (fileFilters.isEmpty()) {
								fileFilter = TrueFileFilter.INSTANCE;
							}

							File sourceFile = new File(source);
							Collection<File> files;
							if (sourceFile.isDirectory()) {
								files = FileUtils.listFiles(sourceFile, fileFilter, directoryFilter);
							} else {
								files = Collections.singleton(sourceFile);
							}
							String destination = step.path("destination").asText();

							for (File file : files) {
//								TarEntry templateTarEntry = new TarEntry(file, destination + file.getAbsolutePath().replaceAll("^.:", "").replaceAll("\\\\", "/"));
								String targetDirName = newName != null && !newName.isMissingNode() ? newName.asText() : sourceFile.getName();
								String relative = targetDirName + "/" + FilenameUtils.separatorsToUnix(sourceFile.toURI().relativize(file.toURI()).getPath());
								TarEntry templateTarEntry = new TarEntry(file, destination + "/" + relative);
								out.putNextEntry(templateTarEntry);
								FileUtils.copyFile(file, out);
								out.closeEntry();
							}
						} else if ("MOUNT".equals(type)) {
							String hostPath = step.path("hostPath").asText();
							String containerPath = step.path("containerPath").asText();
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
					if (buildOutputDumpEnabled) {
						System.out.println();
						System.out.println(errorText);
					}
					throw new RuntimeException("Error building image: " + errorText);
				}
				JsonNode streamNode = node.get("stream");
				if (streamNode != null) {
					String text = streamNode.asText();
					buildLogWriter.print(text);
					if (buildOutputDumpEnabled) {
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
		if (useProxy) {
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
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}

	private WebTarget createDockerTarget(Host host) {
        String url;
        if(host.dockerUrl == null) {
            url = "http://" + getExternalHostIp(host) + ":2375";
        } else {
            url = host.dockerUrl;
        }
        return client.target(url);
	}

	private WebTarget createRegistryTarget() {
		return client.target("http://" + this.getExternalHostIp(this.findRegistryHost(this.manifest)) + ":" + this.getImageRegistry().getPort());
	}

	public void setExternalHostIp(String hostName, String ip) {
		externalHostIpMap.put(hostName, ip);
	}

	public String getExternalHostIp(Host host) {
		String ip = externalHostIpMap.get(host.name);
		if (ip == null) {
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

	private ImageRegistry getImageRegistry() {
		JsonNode registryNode = this.manifest.site.get("imageRegistry");
		if (registryNode == null) {
			return null;
		}
		String containerName = registryNode.get("containerName").textValue();
		Integer port = registryNode.get("port").intValue();
		Container registryContainer = this.findContainer(containerName, manifest);
		Host registryHost = this.findHost(registryContainer.host, manifest);
		String ip = registryHost.ip;
		return new ImageRegistry(containerName, ip, port);
	}

	private String getRootImage(String imageName) {
		return this.getRootImage(this.findImage(imageName, this.manifest));
	}

	public String getRootImage(Image image) {
		return image.buildSteps.stream().filter(node -> node.get("type").textValue().equals("FROM"))
		// .peek(node -> log.info(node.toString()))
                .map(node -> node.get("line").textValue()).findFirst().get();
	}

	private void setRootImage(Image image, String newRootImageName) {
		image.buildSteps.stream().filter(node -> node.get("type").textValue().equals("FROM")).forEach(n -> {
			ObjectNode on = (ObjectNode) n;
			on.remove("line");
			on.put("line", newRootImageName);
		});

	}

	private String constructPrivateImageName(String imageName) {
		return this.getRegistryName() + "/" + imageName;
	}

	private String deconstructPrivateImageName(String imageName) {
		if (imageName.startsWith(this.getRegistryName())) {
			return imageName.replace(this.getRegistryName() + "/", "");
		}
		return imageName;
	}

	private String createBaseImageName(Image image) {
		return image.name + "-image";
	}

	String getRegistryName() {
		return this.getImageRegistry() != null ? this.getImageRegistry().getIp() + ":" + this.getImageRegistry().getPort() : null;
	}

	private Container findRegistryContainer(Manifest manifest) {
		return this.findContainer(this.getImageRegistry().getContainerName(), manifest);
	}

	private Host findRegistryHost(Manifest manifest) {
		return this.findHost(this.findRegistryContainer(manifest).host, manifest);
	}

	private boolean hostHasImage(String imageName, Host host) throws Exception {
		// Pair<String, String> splittedName = this.splitImageName(imageName);
		WebTarget dockerTarget = this.createDockerTarget(host);
		JsonNode response = null;
		try {
			response = dockerTarget.path("images").path(imageName).path("json").request().buildGet().submit(JsonNode.class).get();
		} catch (Throwable t) {
			// 'Hacky' workaround...
			if (!t.getMessage().contains("404")) {
				Throwables.propagate(t);
			}
		}
		if (response != null && response.size() > 0 && response.get("Container") != null) {
			return true;
		}
		return false;
	}

	private boolean registryHasImage(String imageName) throws Exception {
		String imgNam = this.deconstructPrivateImageName(imageName);
		Pair<String, String> splittedName = this.splitImageName(imgNam);
		WebTarget registryTarget = this.createRegistryTarget();

		JsonNode response = null;
		try {
			response = registryTarget.path("v1").path("search").queryParam("q", splittedName.getLeft()).request().buildGet().submit(JsonNode.class).get();
		} catch (Throwable t) {
			// 'Hacky' workaround...
			if (!t.getMessage().contains("404")) {
				// Throwables.propagate(t);
			}
		}
		if (response != null && response.size() > 0 && response.get("results") != null && response.get("results").size() > 0) {
			for (Iterator<JsonNode> it = response.get("results").iterator(); it.hasNext();) {
				JsonNode result = it.next();
				if (!imgNam.contains("/")) {
					imgNam = "library/" + imgNam;
				}
				if (result.get("name") != null && result.get("name").textValue().equals(imgNam)) {
					Map<String, String> tags = this.getTags(imgNam);
					if (tags.containsKey(splittedName.getRight())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	Pair<String, String> splitImageName(String imageName) {
		String header = "";
		if(this.getRegistryName() != null && imageName.startsWith(this.getRegistryName())) {
			header = this.getRegistryName();
			imageName = imageName.substring(this.getRegistryName().length());
		}
		List<String> splittedName = Splitter.on(":").splitToList(imageName);
		String name = header + splittedName.get(0);
		String tag = splittedName.size() > 1 ? splittedName.get(1) : "latest";
		return Pair.of(name, tag);
	}

	private void createImage(Host host, String imageName) {
		Pair<String, String> splittedName = this.splitImageName(imageName);
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/images/create").queryParam("fromImage", splittedName.getLeft()).queryParam("tag", splittedName.getRight()).request()
					.post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private void tagImage(Host host, String imageName, String privateName, String tag) {
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/images/" + imageName + "/tag").queryParam("repo", privateName).queryParam("tag", tag).queryParam("force", "0").request()
					.post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private void pushImage(Host host, String imageName, String privateName, String tag) {
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/images/" + privateName + "/push").queryParam("tag", tag).request().header("X-Registry-Auth", "aaa").post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private void deleteImage(Host host, String imageName) {
		log.info("Will delete='{}'", imageName);
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/images/" + imageName).request().delete();
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private Map<String, String> getTags(String imageName) throws Exception {
		WebTarget registryTarget = this.createRegistryTarget();
		JsonNode response = registryTarget.path("v1").path("repositories").path(imageName).path("tags").request().buildGet().submit(JsonNode.class).get();
		return new ObjectMapper().treeToValue(response, Map.class);

	}

	public TaskInfo<Void> stopContainers(List<String> containers) {
		return taskService.startTask("Stop containers " + Joiner.on(", ").join(containers), () -> {
			log.info("Stopping containers {}", containers);

			List<Exception> errors = new ArrayList<>();
			for (String container: containers) {
				try {
					stopContainer(container);
				} catch (Exception e) {
					errors.add(e);
				}
			}
			if (!errors.isEmpty()) {
				// TODO: handle the whole list of exception instead of only the first one
				throw errors.get(0);
			} else {
				return null;
			}
		});
	}

	private void stopContainer(String containerName) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Host host = findHost(container.host, manifest);
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/containers/" + containerName + "/stop").queryParam("t", "10").request().post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}

	}

	public TaskInfo<Void> restartContainers(List<String> containers) {
		return taskService.startTask("Restart containers " + Joiner.on(", ").join(containers), () -> {
			log.info("Restarting containers {}", containers);

			List<Exception> errors = new ArrayList<>();
			for (String container: containers) {
				try {
					runContainerCommand("restart").accept(container);
				} catch (Exception e) {
					errors.add(e);
				}
			}
			if (!errors.isEmpty()) {
				// TODO: handle the whole list of exception instead of only the first one
				throw errors.get(0);
			} else {
				return null;
			}
		});
	}

	public TaskInfo<Void> startContainers(List<String> containers) {
		return taskService.startTask("Start containers " + Joiner.on(", ").join(containers), () -> {
			log.info("Starting containers {}", containers);
//			containers.forEach(this::startContainer);

			List<Exception> errors = new ArrayList<>();
			for (String container: containers) {
				try {
					startContainer(container);
				} catch (Exception e) {
					errors.add(e);
				}
			}
			if (!errors.isEmpty()) {
				// TODO: handle the whole list of exception instead of only the first one
				throw errors.get(0);
			} else {
				return null;
			}
		});
	}

	public void startContainer(String containerName) {
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

	public Map<String, ContainerState> getContainerStates() {
		HashMap<String, ContainerState> states = new HashMap<>();
		Manifest manifest = this.manifest;
		for (Host host : manifest.hosts) {
			WebTarget dockerTarget = createDockerTarget(host);
			try {
				JsonNode response = dockerTarget.path("/containers/json").queryParam("all", true).request().buildGet().submit(JsonNode.class).get();
				for (JsonNode container : response) {
					for (JsonNode nameNode : container.get("Names")) {
						ContainerState state = new ContainerState();
						String name = nameNode.textValue();
						name = name.substring(1);
						String dockerStatus = container.get("Status").textValue();
						state.status = ContainerState.Status.stopped;
						if (dockerStatus.startsWith("Up ")) {
							state.status = ContainerState.Status.running;
						}
						state.containerName = name;
						state.hostName = host.name;
						states.put(name, state);
					}
				}

			} catch (Throwable ignored) {
				log.warn("Error getting container state for host {}: {}", host.name, ignored.getMessage());
			}

		}
		return states;
	}

	public TaskInfo<Void> purgeContainerData(List<String> containers) {
		return taskService.startTask("Purge container data in " + Joiner.on(", ").join(containers), () -> {
			log.info("Purging data in containers {}", containers);
			containers.forEach(this::purgeContainerData);
			return null;
		});

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

		try (InputStream inputStream = dockerTarget.path("/containers/" + containerName + "/logs").queryParam("stdout", true).queryParam("stderr", true)
                .queryParam("timestamps", true).request().buildGet().invoke(InputStream.class)) {
			DataInputStream dataInputStream = new DataInputStream(inputStream);
			while (true) {
				int flags = dataInputStream.readInt();
				int size = dataInputStream.readInt();
				IOUtils.copyLarge(inputStream, output, 0, size);
			}
		} catch (EOFException expected) {

		} catch (RuntimeException runtimeException) {
			if (runtimeException.getMessage().contains(" 404 ")) {
				throw new NotFoundException("Container " + containerName + " not found");
			}
		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

    public InputStream getContainerLogStream(String containerName) {
        Manifest manifest = this.manifest;
        Container container = findContainer(containerName, manifest);
        Host host = findHost(container.host, manifest);

        WebTarget dockerTarget = createDockerTarget(host);


        URI uri = dockerTarget.path("/containers/" + containerName + "/logs").queryParam("stdout", true).queryParam("stderr", true)
                .queryParam("timestamps", true).queryParam("follow", 1).queryParam("tail", 1000).getUri();
        try {
            // Note: an URL connection is used to properly terminate the streaming connection once we are done with it.
            // Since it may stream forever, there is no way within the HTTP(1) protocol to signal we are no longer interested.
            // This is why  we need to issue a TCP RST,at least until we get HTTP2 support
            URLConnection urlConnection = uri.toURL().openConnection();
            return urlConnection.getInputStream();
        } catch (IOException e) {
            throw Throwables.propagate(e);
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

	public void validateTemplates() {
		log.info("Validating templates");
		try {
			new ManifestJob<Void>(manifest) {
				@Override
				public Void execute() throws Exception {
					for (Image image : manifest.images) {
						verifyTemplates(image.buildSteps);
					}

					for (Container container : manifest.containers) {
						verifyTemplates(container.configureSteps);
					}

					for (Host host : manifest.hosts) {
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
		log.info("Templates validated");
	}

	public boolean isUseProxy() {
		return useProxy;
	}

	public String getHttpProxyUrl() {
		return httpProxyUrl;
	}

	public String getHostScript(String hostName, String type) {
		try {
			return new HostJob<String>(manifest, hostName) {

				@Override
				public String execute() throws Exception {
					List<JsonNode> steps = null;
					if (type.equals("postDeploy")) {
						steps = host.postDeploySteps;
					} else if (type.equals("reconfigure")) {
						steps = host.reconfigureSteps;
					}

					if (steps == null) {
						return "- No script for " + type;
					}
					return generateScript(steps);
				}
			}.execute();
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}

	private String generateScript(List<JsonNode> steps) {
		StringBuilder scriptBuilder = new StringBuilder();
		for (JsonNode step : steps) {
			String type = step.path("type").asText();
			String line = step.path("line").asText();
			if ("ADD_TEMPLATE".equals(type)) {
				type = "ADD";
				String destination = step.path("destination").asText();
				String source = destination;
				if (source.startsWith("/")) {
					source = source.substring(1);
				}
				line = source + " " + destination;
			} else if ("RUN".equals(type)) {
				type = null;
			} else if ("DETERMINE_IP".equals(type)) {
				line = step.path("command").asText();
			}
			if (type != null) {
				scriptBuilder.append(type).append(" ");
			}
			scriptBuilder.append(line);
			scriptBuilder.append("\n");
		}
		return scriptBuilder.toString();

	}

	public TaskInfo<Void> destroyUnmanagedContainer(String containerName, String hostName) {
		return taskService.startTask("Destroy container " + containerName + "@" + hostName, () -> {
			Manifest manifest = this.manifest;
			Host host = findHost(hostName, manifest);
			log.info("Destroying container");
			destroyContainer(containerName, host);
			return null;
		});
	}

	public URL getTemplateUrl(Host host) throws Exception {
		URL url = new URL(host.vmConfiguration.ovaUrl);
		if(this.getImageRegistry() != null) {
			Host registryHost = this.findRegistryHost(this.manifest);
            if(registryHost.name.equals(InetAddress.getLocalHost().getHostName())) {
                // Running on registry host, deploy templates locally
                url = new URL("http://" + registryHost.ip + ":40004/api/vmtemplate/" + FilenameUtils.getName(host.vmConfiguration.ovaUrl));
            };
		}
		return url;
	}
}
