package io.github.floto.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
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
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.*;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
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
	private File flotoHome = new File(System.getProperty("user.home")
			+ "/.floto");
	private final boolean useProxy;
	private String httpProxyUrl;
	private HttpProxy proxy;

	// --------- JUST A WORKAROUND!!! ---------
	private CloseableHttpClient httpClient = HttpClients.createDefault();
	// -----------------------------------------

	private Map<String, String> externalHostIpMap = new HashMap<>();

	private ImageRegistry imageRegistry;

	public enum DeploymentMode {
		fromScratch, fromRootImage, fromBaseImage
	}

	private Client client;
	{
		ClientConfig clientConfig = new ClientConfig();
		clientConfig.property(ClientProperties.READ_TIMEOUT, 0);
		clientConfig.property(ClientProperties.CONNECT_TIMEOUT, 2000);
		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
		connectionManager.setMaxTotal(100);
		connectionManager.setDefaultMaxPerRoute(20);
		clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER,
				connectionManager);

		ClientBuilder clientBuilder = JerseyClientBuilder.newBuilder();
		clientConfig.connectorProvider(new ApacheConnectorProvider());
		clientBuilder.withConfig(clientConfig);
		client = clientBuilder.build();
		client.register(new ErrorClientResponseFilter());
	}

	private boolean buildOutputDumpEnabled = false;
	private TaskService taskService;

	public FlotoService(FlotoCommonParameters commonParameters,
			TaskService taskService) {
		this.taskService = taskService;
		this.rootDefinitionFile = new File(commonParameters.rootDefinitionFile)
				.getAbsoluteFile();
		this.useProxy = !commonParameters.noProxy;
		try {
			this.manifestString = new ObjectMapper().writer()
					.writeValueAsString(manifest);
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
						ownAddress = Inet4Address.getLocalHost()
								.getHostAddress();
					} catch (Throwable throwable) {
						log.warn("Unable to get own address", throwable);
					}
					if (ownAddress == null || ownAddress.startsWith("127.")) {
						Enumeration e = NetworkInterface.getNetworkInterfaces();
						while (e.hasMoreElements()) {
							NetworkInterface n = (NetworkInterface) e
									.nextElement();
							if (n.getDisplayName().startsWith("eth")) {
								Enumeration ee = n.getInetAddresses();
								while (ee.hasMoreElements()) {
									InetAddress i = (InetAddress) ee
											.nextElement();
									if (i instanceof Inet4Address) {
										ownAddress = i.getHostAddress();
										break;
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
		return taskService.startTask(
				"Compile manifest",
				() -> {
					log.info("Compiling manifest");
					String manifestString = flotoDsl
							.generateManifestString(rootDefinitionFile);
					manifest = flotoDsl.toManifest(manifestString);
					this.manifestString = manifestString;
					log.info("Compiled manifest");
					validateTemplates();
					this.setImageRegistry(manifest);
					return null;
				});
	}

	public String getManifestString() {
		return manifestString;
	}

	public TaskInfo<Void> redeployContainers(List<String> containers,
			DeploymentMode deploymentMode) {
		
		List<String> actualContainers = new ArrayList<>(containers);
		Manifest manifest = this.manifest;
		if (this.imageRegistry != null) {
			Container registryContainer = this.findRegistryContainer(manifest);
			boolean registryRedploymentInstructed = actualContainers.stream()
					.filter(c -> registryContainer.name.equals(c)).findFirst()
					.isPresent();
			if (registryRedploymentInstructed) {
				actualContainers.remove(actualContainers
						.indexOf(registryContainer.name));
				this.redeployContainer(registryContainer.name, deploymentMode);
			} else {
				ContainerState registryContainerState = this
						.getContainerStates().get(registryContainer.name);
				if (registryContainerState == null) {
					this.redeployContainer(registryContainer.name,
							deploymentMode);
				} else if (registryContainerState.status == ContainerState.Status.stopped) {
					log.info("Starting registry");
					this.startContainers(Lists
							.newArrayList(registryContainer.name));
				} else if (registryContainerState.status == ContainerState.Status.running) {
					log.info("Registry already running");
				} else {
					throw new RuntimeException("Unknown status="
							+ registryContainerState.status);
				}
			}
		}

		return taskService
				.startTask(
						"Redeploy containers "
								+ Joiner.on(", ").join(actualContainers),
						() -> {
							log.info("Redeploying containers {} in mode='{}'",
									actualContainers, deploymentMode);
							actualContainers.forEach(container -> this
									.redeployContainer(container,
											deploymentMode));
							return null;
						});
	}

	private void redeployContainer(String containerName,
			DeploymentMode deploymentMode) {
		File buildLogDirectory = new File(flotoHome, "buildLog");
		Manifest manifest = this.manifest;
		try {
			FileUtils.forceMkdir(buildLogDirectory);
		} catch (IOException e) {
			Throwables.propagate(e);
		}
		try (FileOutputStream buildLogStream = new FileOutputStream(
				getContainerBuildLogFile(containerName))) {
			Container container = findContainer(containerName, manifest);
			Image image = findImage(container.image, manifest);
			String rootImageName = this.deconstructPrivateImageName(this
					.getRootImage(image));
			Host host = findHost(container.host, manifest);
			if (this.imageRegistry != null) {
				if (!this.findRegistryContainer(manifest).name
						.equalsIgnoreCase(container.name)) {
					if (!this.registryHasImage(rootImageName)
							|| DeploymentMode.fromScratch
									.equals(deploymentMode)) {
						if (!this.hostHasImage(rootImageName, host)
								|| DeploymentMode.fromScratch
										.equals(deploymentMode)) {
							this.createImage(host, rootImageName);
						}
						this.tagImage(host, rootImageName);
						this.pushImage(host, rootImageName);
					}
					this.setRootImage(image, rootImageName);
				}
				log.info("Will use root-image={}", this.getRootImage(image));
			}

			// Build base image
			String baseImageName = image.name + "-image";
			if (this.imageRegistry != null) {
				if (!this.findRegistryContainer(manifest).name
						.equalsIgnoreCase(container.name)) {
					if (!this.registryHasImage(baseImageName)
							|| DeploymentMode.fromScratch
									.equals(deploymentMode)
							|| DeploymentMode.fromRootImage
									.equals(deploymentMode)) {
						if (!this.hostHasImage(baseImageName, host)
								|| DeploymentMode.fromScratch
										.equals(deploymentMode)
								|| DeploymentMode.fromRootImage
										.equals(deploymentMode)) {
							buildImage(baseImageName, image.buildSteps, host,
									manifest, Collections.emptyMap(),
									buildLogStream);
						}
						this.tagImage(host, baseImageName);
						this.pushImage(host, baseImageName);
					}
					baseImageName = this
							.constructPrivateImageName(baseImageName);
				} else {
					buildImage(baseImageName, image.buildSteps, host, manifest,
							Collections.emptyMap(), buildLogStream);
				}
			} else {
				buildImage(baseImageName, image.buildSteps, host, manifest,
						Collections.emptyMap(), buildLogStream);
			}

			// Build configured image
			List<JsonNode> configureSteps = new ArrayList<>(
					container.configureSteps);
			ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode()
					.put("type", "FROM").put("line", baseImageName);

			configureSteps.add(0, fromBuildStep);
			Map<String, Object> globalConfig = createGlobalConfig(manifest);
			if (this.imageRegistry != null) {
				if (!this.findRegistryContainer(manifest).name
						.equalsIgnoreCase(container.name)) {
					if (!this.registryHasImage(container.name)) {
						if (!this.hostHasImage(container.name, host)) {
							buildImage(container.name, configureSteps, host,
									manifest, globalConfig, buildLogStream);
						}
						this.tagImage(host, container.name);
						this.pushImage(host, container.name);
					}
				} else {
					buildImage(container.name, configureSteps, host, manifest,
							globalConfig, buildLogStream);
				}
			} else {
				buildImage(container.name, configureSteps, host, manifest,
						globalConfig, buildLogStream);
			}

			// destroy old container
			destroyContainer(container.name, host);

			// create container
			createContainer(container, host);

			// start container
			startContainer(containerName);
			if (this.imageRegistry != null) {
				if (this.findRegistryContainer(manifest).name
						.equalsIgnoreCase(container.name)) {
					this.tagImage(host, rootImageName);
					this.pushImage(host, rootImageName);
				}
			}

			log.info("Redeployed container {}", container.name);

		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	public Map<String, Object> createGlobalConfig(Manifest manifest) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> siteMap = mapper.reader(Map.class).readValue(
					manifest.site);
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

	// private void startContainer(Image image, Container container, Host host)
	// {
	// WebTarget dockerTarget = createDockerTarget(host);
	// Map<String, Object> startConfig = new HashMap<>();
	//
	// // Host networking
	// startConfig.put("NetworkMode", "host");
	//
	// // Host mount directories
	// ArrayList<String> binds = new ArrayList<>();
	// for (Map.Entry<String, String> entry : getContainerVolumes(image,
	// container).entrySet()) {
	// binds.add(entry.getKey() + ":" + entry.getValue());
	// }
	// startConfig.put("Binds", binds);
	//
	// Response startResponse = dockerTarget.path("/containers/" +
	// container.name + "/start").request().post(Entity.entity(startConfig,
	// MediaType.APPLICATION_JSON_TYPE));
	// startResponse.close();
	// }

	// private void startContainer(Image image, Container container, Host host)
	// {
	//
	// WebTarget dockerTarget = createDockerTarget(host).path(
	// "/containers/" + container.name + "/start");
	// Map<String, Object> startConfig = Maps.newHashMap();
	//
	// // Host mount directories
	// ArrayList<String> binds = new ArrayList<>();
	// for (Map.Entry<String, String> entry : getContainerVolumes(image,
	// container).entrySet()) {
	// binds.add(entry.getKey() + ":" + entry.getValue());
	// }
	// startConfig.put("Binds", binds);
	//
	// // Host networking
	// startConfig.put("NetworkMode", "host");
	//
	// Builder request = dockerTarget.request();
	// Response response = request.post(Entity.json(startConfig));
	// System.out.println(response.getStatusInfo());
	// response.close();
	// }

	private void startContainer(Image image, Container container, Host host) {

		WebTarget dockerTarget = createDockerTarget(host);
		URI uri = dockerTarget.path("/containers/" + container.name + "/start")
				.getUri();

		try {
			HttpPost start = new HttpPost(uri);
			start.setHeader(new BasicHeader("Accept-Encoding", "gzip,deflate"));

			Map<String, Object> startConfig = Maps.newHashMap();
			ArrayList<String> binds = Lists.newArrayList();
			for (Map.Entry<String, String> entry : getContainerVolumes(image,
					container).entrySet()) {
				binds.add(entry.getKey() + ":" + entry.getValue());
			}
			startConfig.put("Binds", binds);
			startConfig.put("Privileged", container.priviledged);
			startConfig.put("NetworkMode", "host");

			start.setEntity(new StringEntity(new ObjectMapper()
					.writeValueAsString(startConfig), ContentType
					.create("application/json")));
			httpClient.execute(start, new BasicResponseHandler());
		} catch (Exception ex) {
			Throwables.propagate(ex);
		}

	}

	private Map<String, String> getContainerVolumes(Image image,
			Container container) {
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

	// private void createContainer(Container container, Host host) {
	// WebTarget dockerTarget = createDockerTarget(host);
	// Map<String, Object> createConfig = new HashMap<>();
	// createConfig.put("Image", container.name);
	// Response createResponse =
	// dockerTarget.path("/containers/create").queryParam("name",
	// container.name).request().post(Entity.entity(createConfig,
	// MediaType.APPLICATION_JSON_TYPE));
	// createResponse.close();
	// }

	private void createContainer(Container container, Host host) {
		WebTarget dockerTarget = createDockerTarget(host).path(
				"/containers/create").queryParam("name", container.name);
		Map<String, Object> createConfig = Maps.newHashMap();
		createConfig.put("Image", container.name);
		createConfig.put("PortSpecs", null);
		createConfig.put("ExposedPorts", Maps.newHashMap());

		Builder request = dockerTarget.request();
		Response response = request.post(Entity.json(createConfig));
		response.close();
	}

	// private void createContainer(Container container, Host host) {
	//
	// ObjectMapper objectMapper = new ObjectMapper();
	//
	// WebTarget dockerTarget = createDockerTarget(host);
	//
	// try {
	// URI uri = new URI(dockerTarget.path("/containers/create").getUri()
	// .toString()
	// + "?name=" + container.name);
	// System.out.println("CREATE-URI=" + uri);
	// HttpPost create = new HttpPost(uri);
	// create.setHeader(new BasicHeader("Accept-Encoding", "gzip,deflate"));
	// Map<String, Object> createConfig = Maps.newHashMap();
	// createConfig.put("Image", container.name);
	// createConfig.put("PortSpecs", null);
	// createConfig.put("ExposedPorts", Maps.newHashMap());
	// create.setEntity(new StringEntity(objectMapper
	// .writeValueAsString(createConfig), ContentType
	// .create("application/json")));
	//
	// CloseableHttpResponse response = httpClient.execute(create);
	// System.out.println(response.getStatusLine());
	// response.close();
	// } catch (Exception e) {
	// Throwables.propagate(e);
	// }
	// }

	private void destroyContainer(String containerName, Host host) {
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response killResponse = dockerTarget
					.path("/containers/" + containerName + "/kill").request()
					.post(Entity.text(""));
			killResponse.close();
			Response removeResponse = dockerTarget
					.path("/containers/" + containerName).request().delete();
			removeResponse.close();
		} catch (Throwable t) {
			if (t.getMessage().contains("404")) {
				// Not there - ignore
			} else {
				throw new RuntimeException("Unable to destroy container "
						+ containerName, t);
			}
		}
	}

	private void buildImage(String imageName, List<JsonNode> buildSteps,
			Host host, Manifest manifest, Map<String, Object> globalConfig,
			OutputStream buildLogStream) {
		PrintWriter buildLogWriter = new PrintWriter(buildLogStream);
		String dockerFile = createDockerFile(buildSteps);

		WebTarget dockerTarget = createDockerTarget(host);
		WebTarget buildTarget = dockerTarget.path("build");
		Response response = buildTarget.queryParam("t", imageName).request()
				.post(Entity.entity(new StreamingOutput() {
					@Override
					public void write(OutputStream outputStream)
							throws IOException, WebApplicationException {
						try (TarOutputStream out = new TarOutputStream(
								outputStream)) {
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
									String destination = step.path(
											"destination").asText();
									String source = destination;
									if (source.startsWith("/")) {
										source = source.substring(1);
									}
									String templated = new TemplateUtil()
											.getTemplate(step, globalConfig);
									TarEntry templateTarEntry = new TarEntry(
											source);
									byte[] templateBytes = templated
											.getBytes(Charsets.UTF_8);
									templateTarEntry
											.setSize(templateBytes.length);
									out.putNextEntry(templateTarEntry);
									IOUtils.write(templateBytes, out);
									out.closeEntry();
								} else if ("ADD_MAVEN".equals(type)) {
									String coordinates = step.path(
											"coordinates").asText();
									String destination = step.path(
											"destination").asText();
									if (coordinates.contains(":tar.gz:")) {
										destination = destination + ".x";
									}
									if (destination.startsWith("/")) {
										destination = destination.substring(1);
									}

									JsonNode repositories = manifest.site
											.findPath("maven").findPath(
													"repositories");
									File artifactFile = new MavenHelper(
											repositories)
											.resolveMavenDependency(coordinates);
									TarEntry templateTarEntry = new TarEntry(
											artifactFile, destination);
									out.putNextEntry(templateTarEntry);
									FileUtils.copyFile(artifactFile, out);
									out.closeEntry();
								} else if ("ADD_MANIFEST_JSON".equals(type)) {
									String destination = step.path(
											"destination").asText();
									TarEntry manifestTarEntry = new TarEntry(
											destination);
									out.putNextEntry(manifestTarEntry);
									byte[] manifestBytes = new ObjectMapper()
											.writer()
											.withDefaultPrettyPrinter()
											.writeValueAsBytes(manifest);
									manifestTarEntry
											.setSize(manifestBytes.length);
									out.putNextEntry(manifestTarEntry);
									IOUtils.write(manifestBytes, out);

									out.closeEntry();
								} else if ("COPY_FILES".equals(type)) {
									JsonNode fileList = step.path("fileList");
									List<String> files = new ArrayList<String>();
									Iterator<Map.Entry<String, JsonNode>> iterator = fileList
											.fields();
									while (iterator.hasNext()) {
										files.add(iterator.next().getKey());
									}
									String destination = step.path(
											"destination").asText();

									for (String filename : files) {
										File file = new File(filename);
										TarEntry templateTarEntry = new TarEntry(
												file, destination + filename);
										out.putNextEntry(templateTarEntry);
										FileUtils.copyFile(file, out);
										out.closeEntry();
									}

								} else if ("COPY_DIRECTORY".equals(type)) {
									String source = step.path("source")
											.asText();
									JsonNode options = step.path("options");
									JsonNode excludeDirectories = options
											.path("excludeDirectories");
									List<IOFileFilter> filters = new ArrayList<IOFileFilter>();
									for (JsonNode node : excludeDirectories) {
										filters.add(new NotFileFilter(
												new NameFileFilter(node
														.asText())));
									}
									IOFileFilter directoryFilter = new AndFileFilter(
											filters);
									if (filters.isEmpty()) {
										directoryFilter = TrueFileFilter.INSTANCE;
									}
									File sourceFile = new File(source);
									Collection<File> files;
									if (sourceFile.isDirectory()) {
										files = FileUtils.listFiles(sourceFile,
												FileFileFilter.FILE,
												directoryFilter);
									} else {
										files = Collections
												.singleton(sourceFile);
									}
									String destination = step.path(
											"destination").asText();

									for (File file : files) {
										TarEntry templateTarEntry = new TarEntry(
												file,
												destination
														+ file.getAbsolutePath()
																.replaceAll(
																		"^.:",
																		"")
																.replaceAll(
																		"\\\\",
																		"/"));
										out.putNextEntry(templateTarEntry);
										FileUtils.copyFile(file, out);
										out.closeEntry();
									}
								} else if ("MOUNT".equals(type)) {
									String hostPath = step.path("hostPath")
											.asText();
									String containerPath = step.path(
											"containerPath").asText();
								}
							}

							out.close();
						}

					}
				}, "application/tar"));
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			InputStream inputStream = (InputStream) response.getEntity();
			CloseShieldInputStream shieldInputStream = new CloseShieldInputStream(
					inputStream);
			MappingIterator<JsonNode> entries = objectMapper.reader(
					JsonNode.class).readValues(shieldInputStream);
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
					throw new RuntimeException("Error building image: "
							+ errorText);
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
		return client.target("http://" + getExternalHostIp(host) + ":2375");
	}

	private WebTarget createRegistryTarget() {
		return client.target("http://"
				+ this.getExternalHostIp(this.findRegistryHost(this.manifest))
				+ ":" + this.imageRegistry.getPort());
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
		throw new IllegalArgumentException("Unknown container: "
				+ containerName);
	}

	private void setImageRegistry(Manifest manifest) {
		JsonNode registryNode = manifest.site.get("imageRegistry");
		if (registryNode == null) {
			return;
		}
		String containerName = registryNode.get("containerName").textValue();
		Integer port = registryNode.get("port").intValue();
		Container registryContainer = this.findContainer(containerName,
				manifest);
		Host registryHost = this.findHost(registryContainer.host, manifest);
		String ip = registryHost.ip;
		this.imageRegistry = new ImageRegistry(containerName, ip, port);
	}

	private String getRootImage(Image image) {
		return image.buildSteps.stream()
				.filter(node -> node.get("type").textValue().equals("FROM"))
				.peek(node -> log.info(node.toString()))
				.map(node -> node.get("line").textValue()).findFirst().get();
	}

	private void setRootImage(Image image, String newRootImageName) {
		image.buildSteps
				.stream()
				.filter(node -> node.get("type").textValue().equals("FROM"))
				.forEach(
						n -> {
							ObjectNode on = (ObjectNode) n;
							on.remove("line");
							on.put("line",
									this.constructPrivateImageName(newRootImageName));
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

	private String getRegistryName() {
		return this.imageRegistry.getIp() + ":" + this.imageRegistry.getPort();
	}

	private Container findRegistryContainer(Manifest manifest) {
		return this.findContainer(this.imageRegistry.getContainerName(),
				manifest);
	}

	private Host findRegistryHost(Manifest manifest) {
		return this.findHost(this.findRegistryContainer(manifest).host,
				manifest);
	}

	private boolean hostHasImage(String imageName, Host host) throws Exception {
		// Pair<String, String> splittedName = this.splitImageName(imageName);
		WebTarget dockerTarget = this.createDockerTarget(host);
		JsonNode response = null;
		try {
			response = dockerTarget.path("images").path(imageName).path("json")
					.request().buildGet().submit(JsonNode.class).get();
		} catch (Throwable t) {
			// 'Hacky' workaround...
			if (!t.getMessage().contains("404")) {
				Throwables.propagate(t);
			}
		}
		if (response != null && response.size() > 0
				&& response.get("Container") != null) {
			return true;
		}
		return false;
	}

	private boolean registryHasImage(String imageName) throws Exception {
		String imgNam = this.deconstructPrivateImageName(imageName);
		Pair<String, String> splittedName = this.splitImageName(imgNam);
		WebTarget registryTarget = this.createRegistryTarget();
		// JsonNode response =
		// dockerTarget.path("/containers/json").queryParam("all",
		// true).request().buildGet().submit(JsonNode.class).get();

		JsonNode response = null;
		try {
			response = registryTarget.path("v1").path("search")
					.queryParam("q", splittedName.getLeft()).request()
					.buildGet().submit(JsonNode.class).get();
		} catch (Throwable t) {
			// 'Hacky' workaround...
			if (!t.getMessage().contains("404")) {
				// Throwables.propagate(t);
			}
		}
		if (response != null && response.size() > 0
				&& response.get("results") != null
				&& response.get("results").size() > 0) {
			for (Iterator<JsonNode> it = response.get("results").iterator(); it
					.hasNext();) {
				JsonNode result = it.next();
				if (!imgNam.contains("/")) {
					imgNam = "library/" + imgNam;
				}
				if (result.get("name") != null
						&& result.get("name").textValue().equals(imgNam)) {
					Map<String, String> tags = this.getTags(imgNam);
					if (tags.containsKey(splittedName.getRight())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private Pair<String, String> splitImageName(String imageName) {
		List<String> splittedName = Splitter.on(":").splitToList(imageName);
		String name = splittedName.get(0);
		String tag = splittedName.size() > 1 ? splittedName.get(1) : "latest";
		return Pair.of(name, tag);
	}

	private void createImage(Host host, String imageName) {
		Pair<String, String> splittedName = this.splitImageName(imageName);
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget.path("/images/create")
					.queryParam("fromImage", splittedName.getLeft())
					.queryParam("tag", splittedName.getRight()).request()
					.post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private void tagImage(Host host, String imageName) {
		Pair<String, String> splitted = this.splitImageName(imageName);
		String name = splitted.getLeft();
		String tag = splitted.getRight();
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget
					.path("/images/" + imageName + "/tag")
					.queryParam("repo", this.getRegistryName() + "/" + name)
					.queryParam("tag", tag).queryParam("force", "0").request()
					.post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private void pushImage(Host host, String imageName) {
		Pair<String, String> splitted = this.splitImageName(imageName);
		String name = splitted.getLeft();
		String tag = splitted.getRight();
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			Response response = dockerTarget
					.path("/images/" + this.constructPrivateImageName(name)
							+ "/push").queryParam("tag", tag).request()
					.header("X-Registry-Auth", "aaa").post(Entity.text(""));
			response.close();
		} catch (Throwable t) {
			Throwables.propagate(t);
		}
	}

	private Map<String, String> getTags(String imageName) throws Exception {
		WebTarget registryTarget = this.createRegistryTarget();
		JsonNode response = registryTarget.path("v1").path("repositories")
				.path(imageName).path("tags").request().buildGet()
				.submit(JsonNode.class).get();
		return new ObjectMapper().treeToValue(response, Map.class);

	}

	public TaskInfo<Void> stopContainers(List<String> containers) {
		return taskService.startTask(
				"Stop containers " + Joiner.on(", ").join(containers), () -> {
					log.info("Stopping containers {}", containers);
					containers.forEach(runContainerCommand("stop"));
					return null;
				});

	}

	public TaskInfo<Void> restartContainers(List<String> containers) {
		return taskService.startTask("Restart containers "
				+ Joiner.on(", ").join(containers), () -> {
			log.info("Restarting containers {}", containers);
			containers.forEach(runContainerCommand("restart"));
			return null;
		});
	}

	public TaskInfo<Void> startContainers(List<String> containers) {
		return taskService.startTask("Start containers "
				+ Joiner.on(", ").join(containers), () -> {
			log.info("Starting containers {}", containers);
			containers.forEach(this::startContainer);
			return null;
		});
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
				Response response = dockerTarget
						.path("/containers/" + containerName + "/" + command)
						.request().post(Entity.text(""));
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
				JsonNode response = dockerTarget.path("/containers/json")
						.queryParam("all", true).request().buildGet()
						.submit(JsonNode.class).get();
				for (JsonNode container : response) {
					for (JsonNode nameNode : container.get("Names")) {
						ContainerState state = new ContainerState();
						String name = nameNode.textValue();
						name = name.substring(1);
						String dockerStatus = container.get("Status")
								.textValue();
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
				log.warn("Error getting container state for host {}: {}",
						host.name, ignored.getMessage());
			}

		}
		return states;
	}

	public TaskInfo<Void> purgeContainerData(List<String> containers) {
		return taskService.startTask(
				"Purge container data in " + Joiner.on(", ").join(containers),
				() -> {
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
		sshService.execute(getExternalHostIp(host), "sudo rm -rf "
				+ dataDirectory);
	}

	public String getDockerfile(String containerName, String type) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		List<JsonNode> buildSteps;
		if ("container".equals(type)) {
			String baseImageName = container.image + "-image";
			buildSteps = new ArrayList<>(container.configureSteps);
			ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode()
					.put("type", "FROM").put("line", baseImageName);

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

		try (InputStream inputStream = dockerTarget
				.path("/containers/" + containerName + "/logs")
				.queryParam("stdout", true).queryParam("stderr", true)
				.queryParam("timestamps", false).request().buildGet()
				.invoke(InputStream.class)) {
			DataInputStream dataInputStream = new DataInputStream(inputStream);
			while (true) {
				int flags = dataInputStream.readInt();
				int size = dataInputStream.readInt();
				IOUtils.copyLarge(inputStream, output, 0, size);
			}
		} catch (EOFException expected) {

		} catch (RuntimeException runtimeException) {
			if (runtimeException.getMessage().contains(" 404 ")) {
				throw new NotFoundException("Container " + containerName
						+ " not found");
			}
		} catch (IOException e) {
			Throwables.propagate(e);
		}
	}

	public void getBuildLog(String containerName, OutputStream output) {
		try (FileInputStream input = new FileInputStream(
				getContainerBuildLogFile(containerName))) {
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
			throw new IllegalArgumentException("Template path not found: "
					+ path);
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
			throw new IllegalArgumentException("Template path not found: "
					+ path);
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

	public TaskInfo<Void> destroyUnmanagedContainer(String containerName,
			String hostName) {
		return taskService.startTask("Destroy container " + containerName + "@"
				+ hostName, () -> {
			Manifest manifest = this.manifest;
			Host host = findHost(hostName, manifest);
			log.info("Destroying container");
			destroyContainer(containerName, host);
			return null;
		});
	}
}
