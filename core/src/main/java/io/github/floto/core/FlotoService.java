package io.github.floto.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.inject.util.Types;
import freemarker.template.*;
import io.github.floto.core.jobs.HostJob;
import io.github.floto.core.jobs.ManifestJob;
import io.github.floto.core.patch.PatchDescription;
import io.github.floto.core.patch.PatchInfo;
import io.github.floto.core.proxy.HttpProxy;
import io.github.floto.core.registry.DockerImageDescription;
import io.github.floto.core.registry.ImageRegistry;
import io.github.floto.core.ssh.SshService;
import io.github.floto.core.util.*;
import io.github.floto.dsl.FlotoDsl;
import io.github.floto.dsl.model.*;
import io.github.floto.util.VersionUtil;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Maps;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.*;
import org.apache.commons.io.input.CloseShieldInputStream;
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

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public class FlotoService implements Closeable {
	private Logger log = LoggerFactory.getLogger(FlotoService.class);

	private FlotoDsl flotoDsl = new FlotoDsl();
	private final boolean patchMakerMode;
	private ImageRegistry imageRegistry;
	private File rootDefinitionFile;
	private String environment;
	private String manifestString;
	private Manifest manifest = new Manifest();
	private Throwable manifestCompilationError = new Throwable("Manifest is being compiled");
	private SshService sshService = new SshService();
	private File flotoHome;
	private boolean useProxy;
	private String httpProxyUrl;
	private HttpProxy proxy;
	private FlotoSettings settings = new FlotoSettings();

	private Map<String, String> urlImageIdMap = new HashMap<>();
	private Map<String, String> externalHostIpMap = new HashMap<>();
	Set<String> DEPLOYMENT_CONTAINER_NAMES = Sets.newHashSet("floto");
	private PatchInfo activePatch;
	DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH:mm:ssX");

	private final ObjectMapper objectMapper;
	private PatchDescription patchDescription;
	private FlotoCommonParameters commonParameters;

	{
		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		objectMapper.registerModule(new JSR310Module());
	}

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
	FlotoService() {
		this.patchMakerMode = false;
	}

	public FlotoService(FlotoCommonParameters commonParameters, TaskService taskService) {
		this.commonParameters = commonParameters;

		this.patchMakerMode = commonParameters.patchMaker;
		if (commonParameters.patchMaker) {
			commonParameters.patchMode = "create";
		}


		// default
		this.flotoHome = new File(System.getProperty("user.home") + "/.floto");
		// override through environment variable
		String envFlotoHome = System.getenv("FLOTO_HOME");
		if (envFlotoHome != null) {
			this.flotoHome = new File(envFlotoHome);
		}
		// override through command line
		if (commonParameters.flotoHome != null) {
			this.flotoHome = new File(commonParameters.flotoHome);
		}
		log.info("Using floto home: {}", this.flotoHome);
		try {
			FileUtils.forceMkdir(this.flotoHome);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create floto home " + this.flotoHome, e);
		}
		loadSettings();

		this.taskService = taskService;
		this.environment = commonParameters.environment;
		if (commonParameters.rootDefinitionFile != null) {
			this.rootDefinitionFile = new File(commonParameters.rootDefinitionFile).getAbsoluteFile();
		}
		this.useProxy = !commonParameters.noProxy;
		if (this.useProxy) {
			proxy = new HttpProxy(commonParameters.proxyPort);
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
							if (n.getDisplayName().startsWith("eth") || n.getDisplayName().startsWith("wlan") || n.getDisplayName().startsWith("en")) {
								List<InetAddress> addresses = Collections.list(n.getInetAddresses());
								// Force deterministic address order, highest first
								addresses.sort(new Comparator<InetAddress>() {
									@Override
									public int compare(InetAddress o1, InetAddress o2) {
										return o2.getHostAddress().compareTo(o1.getHostAddress());
									}
								});
								if (commonParameters.proxyPrefix != null) {
									// Use first address with prefix
									for (InetAddress address : addresses) {
										if (address.getHostAddress().startsWith(commonParameters.proxyPrefix)) {
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
				httpProxyUrl = "http://" + ownAddress + ":" + commonParameters.proxyPort + "/";
				log.info("Proxy URL: {}", httpProxyUrl);
				flotoDsl.setGlobal("httpProxy", httpProxyUrl);
				flotoDsl.setGlobal("flotoVersion", VersionUtil.version);
				flotoDsl.setGlobal("patchMakerMode", patchMakerMode);
			} catch (Exception e) {
				throw Throwables.propagate(e);
			}

			this.imageRegistry = new ImageRegistry(new File(flotoHome, "images"));

		}
	}

	private void generateContainerHashes(Manifest manifest) {
		FileHashCache fileHashCache = new FileHashCache();
		HashSet<Image> activeImages = new HashSet<Image>();
		manifest.containers.forEach(container ->
		{
			activeImages.add(findImage(container.image, manifest));
		});
		if (activePatch != null) {
			// use patch image hash
			activeImages.forEach(image -> {
				image.buildHash = activePatch.imageMap.get(image.name);
			});
		} else {
			activeImages.forEach(image -> image.buildHash = generateBuildHash(fileHashCache, image.buildSteps));
		}
		manifest.containers.forEach(container ->
		{
			Image image = findImage(container.image, manifest);
			container.buildHash = generateBuildHash(fileHashCache, container.configureSteps, image.buildHash);
		});
	}

	public TaskInfo<Void> compileManifest() {
		return taskService.startTask("Compile manifest", () -> {
			try {
				log.info("Compiling manifest");
				if (activePatch != null) {
					flotoDsl.setGlobal("PATCH_INFO", objectMapper.writeValueAsString(activePatch));
				} else if(patchDescription != null) {
					flotoDsl.setGlobal("PATCH_INFO", objectMapper.writeValueAsString(patchDescription));
				} else {
					flotoDsl.setGlobal("PATCH_INFO", null);
				}
				String manifestString = flotoDsl.generateManifestString(rootDefinitionFile, environment);
				manifest = flotoDsl.toManifest(manifestString);

				String projectRevision = manifest.site.get("projectRevision").asText();
				manifest.projectRevision = projectRevision;
				manifest.containers.forEach(container -> container.projectRevision = projectRevision);
				log.info("Compiled manifest");

				log.info("Generating container hashes");
				generateContainerHashes(manifest);
				validateTemplates();
				this.manifestString = manifestString;
				validateDocuments();
			} catch (Throwable compilationError) {
				this.manifestCompilationError = compilationError;
				throw compilationError;
			}
			return null;
		});
	}

	private void validateDocuments() {
		log.info("Validating documents");
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectReader reader = mapper.reader(Map.class);
			Map<String, Object> manifestJson = reader.readValue(manifestString);
			new ManifestJob<Void>(manifest) {
				@Override
				public Void execute() throws Exception {
					for (DocumentDefinition document : manifest.documents) {
						try {
							FlotoService.this.getDocumentString(document.id, manifestJson);
						} catch (Throwable throwable) {
							log.warn("Unable to generate document " + document.id, throwable);
						}
					}
					return null;
				}
			}.execute();
		} catch (Exception e) {
			Throwables.propagate(e);
		}
		log.info("Documents validated");

	}

	private void digestFile(FileHashCache fileHashCache, MessageDigest messageDigest, File file) {
		messageDigest.update(fileHashCache.getHash(file));
	}

	private String generateBuildHash(FileHashCache fileHashCache, List<JsonNode> buildSteps, String... additionalInputs) {
		Map<String, Object> globalConfig = createGlobalConfig(manifest);
		try {
			MessageDigest md = MessageDigest.getInstance("SHA");
			for (String additionalInput : additionalInputs) {
				md.update(additionalInput.getBytes(Charsets.UTF_8));
			}
			buildSteps.forEach(step -> {
				md.update(step.toString().getBytes(Charsets.UTF_8));

				String type = step.path("type").asText();
				if ("ADD_TEMPLATE".equals(type)) {
					String template = new TemplateUtil().getTemplate(step, globalConfig);
					md.update(template.getBytes(Charsets.UTF_8));
				} else if ("ADD_FILE".equals(type)) {
					File file = new File(step.path("file").asText());
					digestFile(fileHashCache, md, file);
				} else if ("COPY_DIRECTORY".equals(type)) {
					JsonNode options = step.path("options");
					JsonNode newName = options.path("newName");

					String source = step.path("source").asText();
					JsonNode excludeDirectories = options.path("excludeDirectories");
					JsonNode excludeFiles = options.path("excludeFiles");

					File sourceFile = new File(source);
					List<File> files = new ArrayList<File>(getCopyDirectoryFiles(excludeDirectories, excludeFiles, sourceFile));
					Collections.sort(files);
					for (File file : files) {
						md.update(sourceFile.toURI().relativize(file.toURI()).getPath().toString().getBytes(Charsets.UTF_8));
						digestFile(fileHashCache, md, file);
					}
				} else if ("ADD_MAVEN".equals(type)) {
					String coordinates = step.path("coordinates").asText();
					if (coordinates.endsWith("-SNAPSHOT")) {
						// We assume that non-SNAPSHOT artifacts are immutable
						JsonNode repositories = manifest.site.findPath("maven").findPath("repositories");
						File artifactFile = new MavenHelper(repositories).resolveMavenDependency(coordinates);
						digestFile(fileHashCache, md, artifactFile);
					}
				} else if ("COPY_FILES".equals(type)) {
					JsonNode fileList = step.path("fileList");
					List<String> files = new ArrayList<String>();
					Iterator<Map.Entry<String, JsonNode>> iterator = fileList.fields();
					while (iterator.hasNext()) {
						files.add(iterator.next().getKey());
					}
					for (String filename : files) {
						File file = new File(filename);
						md.update(file.toURI().relativize(file.toURI()).getPath().toString().getBytes(Charsets.UTF_8));
						digestFile(fileHashCache, md, file);
					}
				}

			});
			return Hex.encodeHexString(md.digest());
		} catch (Throwable e) {
			log.warn("Unable to generate buildHash", e);
			// Fallback to UUID
			return UUID.randomUUID().toString();
		}
	}

	public String getManifestString() {
		return manifestString;
	}

	public TaskInfo<Void> redeployContainers(List<String> requestedContainers, DeploymentMode deploymentMode) {
		final List<String> containers = new ArrayList<>(requestedContainers);
		String taskName = "Redeploy containers " + Joiner.on(", ").join(requestedContainers) + " in mode '" + deploymentMode + "'";
		if (requestedContainers.size() > 3) {
			taskName = "Redeploy " + requestedContainers.size() + " containers in mode '" + deploymentMode + "'";
		}
		return taskService.startTask(taskName, () -> {
			log.info("Redeploying containers " + Joiner.on(", ").join(requestedContainers));

			int numberOfContainersDeployed = 0;

			for (String containerName : containers) {
				log.info("Will deploy container='{}' ({}/{})", containerName, numberOfContainersDeployed + 1, containers.size());

				File buildLogDirectory = new File(flotoHome, "buildLog");
				try {
					FileUtils.forceMkdir(buildLogDirectory);
				} catch (IOException e) {
					Throwables.propagate(e);
				}
				Container container = this.findContainer(containerName, this.manifest);
				try (FileOutputStream buildLogStream = new FileOutputStream(getContainerBuildLogFile(containerName))) {
					buildLogStream.write(("Build started at " + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + "\n").getBytes());
					try {
						Image image = this.findImage(container.image, this.manifest);
						Host host = this.findHost(container.host, this.manifest);
						if (DeploymentMode.fromRootImage.equals(deploymentMode)) {
							this.redeployFromRootImage(host, container, buildLogStream);
						} else if (DeploymentMode.fromBaseImage.equals(deploymentMode)) {
							String baseImageName = this.createBaseImageName(image);
							this.redeployFromBaseImage(host, container, baseImageName, buildLogStream);
						} else if (DeploymentMode.containerRebuild.equals(deploymentMode)) {
							this.rebuildContainer(container, true);
						} else {
							throw new IllegalStateException("Unknown deploymentMode=" + deploymentMode);
						}
						numberOfContainersDeployed++;
					} catch (Throwable throwable) {
						buildLogStream.write(("Build failed at " + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + "\n").getBytes());
						PrintStream ps = new PrintStream(buildLogStream);
						throwable.printStackTrace(ps);
						ps.flush();
						throw throwable;
					}
					buildLogStream.write(("Build completed at " + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + "\n").getBytes());
				} catch (Throwable t) {
					Throwables.propagate(t);
				}
			}
			if (numberOfContainersDeployed == 0) {
				throw new IllegalStateException("No containers were deployed");
			}
			return null;
		});
	}

	public TaskInfo<Void> redeployDeployerContainer(Host host, Container container,
													boolean createContainer, boolean startContainer) {
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
			// Create Final image
			this.createFinalImage(host, container, baseImageName, buildLogStream);

			if (createContainer) {
				this.rebuildContainer(container, startContainer);
			}

			images2Delete.stream().forEach(s -> this.deleteImage(host, s));

			log.info("Redeployed container {}", container.name);

		} catch (Throwable t) {
			Throwables.propagate(t);
		}
		return null;

	}

	private void redeployFromRootImage(Host host, Container container, FileOutputStream buildLogStream) throws Exception {
		String baseImageName = this.createBaseImage(host, container, buildLogStream);
		this.redeployFromBaseImage(host, container, baseImageName, buildLogStream);
	}

	private void redeployFromBaseImage(Host host, Container container, String baseImageName, FileOutputStream buildLogStream) throws Exception {
		// verify that the correct base image is on the host
		if (activePatch != null) {
			log.info("Deploying from patch: {}", activePatch.revision);
			String strippedBaseImageName = baseImageName.replaceFirst("-image$", "");
			String baseImageId = activePatch.imageMap.get(strippedBaseImageName);
			String repoName = baseImageName;
			baseImageName = baseImageName + ":" + baseImageId;
			log.info("Deploying image <{}> from layer {}", strippedBaseImageName, baseImageId);
			WebTarget dockerTarget = createDockerTarget(host);
			List<DockerImageDescription> imageDescriptions = dockerTarget.path("/images/json").queryParam("all", "1").request().buildGet().submit(new GenericType<List<DockerImageDescription>>(Types.listOf(DockerImageDescription.class))).get();
			Map<String, DockerImageDescription> presentImages = new HashMap<>();
			for (DockerImageDescription imageDescription : imageDescriptions) {
				presentImages.put(imageDescription.Id, imageDescription);
			}

			DockerImageDescription imageDescription = presentImages.get(baseImageId);
			if (imageDescription == null || !imageDescription.RepoTags.contains(baseImageName)) {
				log.info("Base Image <{}> not found, uploading to host", baseImageName);
				List<String> imageIds = new ArrayList<>();
				String currentImageId = baseImageId;
				// find all parents
				while (currentImageId != null && !currentImageId.isEmpty()) {
					if (!presentImages.containsKey(currentImageId)) {
						// not present on host, add it
						imageIds.add(currentImageId);
					}
					currentImageId = imageRegistry.getImageDescription(currentImageId).parent;
				}
				// add images in reverse order
				Lists.reverse(imageIds);
				log.info("Uploading image layers: {}", imageIds);

				final String finalImageId = baseImageId;
				Response createResponse = null;
				try {
					createResponse = createDockerTarget(host).path("/images/load").request().buildPost(Entity.entity(new StreamingOutput() {
						@Override
						public void write(OutputStream output) throws IOException, WebApplicationException {
							try (TarOutputStream tarBallOutputStream = new TarOutputStream(output)) {
								for (String imageId : imageIds) {
									File imageDirectory = imageRegistry.getImageDirectory(imageId);
									Path imagePath = imageDirectory.toPath();
									for (File file : FileUtils.listFiles(imageDirectory, TrueFileFilter.TRUE, TrueFileFilter.TRUE)) {
										Path filePath = file.toPath();
										Path relativePath = imagePath.relativize(filePath);
										String tarFilename = imageId + "/" + relativePath.toString();
										tarBallOutputStream.putNextEntry(new TarEntry(file, tarFilename));
										try (FileInputStream fileInputStream = new FileInputStream(file)) {
											IOUtils.copy(fileInputStream, tarBallOutputStream);
										}
										tarBallOutputStream.closeEntry();
									}
								}
								Map<String, Object> repository = new HashMap<String, Object>();
								HashMap<String, String> tags = new HashMap<String, String>();
								tags.put("latest", finalImageId);
								tags.put(finalImageId, finalImageId);
								repository.put(repoName, tags);
								ObjectMapper mapper = new ObjectMapper();
								byte[] repositoryBytes = mapper.writeValueAsBytes(repository);

								TarEntry repositoriesTarEntry = new TarEntry("repositories");
								repositoriesTarEntry.setSize(repositoryBytes.length);
								tarBallOutputStream.putNextEntry(repositoriesTarEntry);
								IOUtils.write(repositoryBytes, tarBallOutputStream);
								tarBallOutputStream.closeEntry();

							}
						}
					}, "application/octet-stream")).invoke();
				} finally {
					if (createResponse != null) {
						createResponse.close();
					}
				}

				log.info("Base Image <{}> uploaded", baseImageName);
			} else {
				log.info("Base Image <{}> already on host", baseImageName);
			}

		}

		this.createFinalImage(host, container, baseImageName, buildLogStream);
		this.rebuildContainer(container, true);
	}

	private void rebuildContainer(Container container, boolean startContainer) throws Exception {
		Host executingHost = this.findHost(container.host, this.manifest);
		destroyContainer(container.name, executingHost);
		createContainer(container, executingHost, container.name);
		if (startContainer) {
			startContainer(container.name);
		}
	}

	public String createBaseImage(Host host, Container container, FileOutputStream buildLogStream) {
		Image image = findImage(container.image, this.manifest);
		return createImage(host, image, buildLogStream);
	}

	public String createImage(Host host, Image image, OutputStream buildLogStream) {
		String rootImage = this.getRootImage(image);
		log.info("Will use root-image={}", rootImage);
		List<JsonNode> buildSteps = new ArrayList<>(image.buildSteps);
		String repoName = this.createRootImageName(rootImage);
		if (rootImage.startsWith("http://")) {
			boolean needToImport = true;
			int index = repoName.indexOf(":");
			String shortRepoName = repoName.substring(0, index);
			String tagName = repoName.substring(index + 1);
			;
			try {
				WebTarget webTarget = createDockerTarget(host).path("/images/{imageName}/json").resolveTemplate("imageName", repoName);
				webTarget.request().get().close();
				log.info("Root image found, skipping download ({})", repoName);
				needToImport = false;
			} catch (Throwable t) {
				log.info("Unable to locate image: {}", t.getMessage());
			}
			if (needToImport) {
				log.info("Root image not found, importing {}", repoName);
				int separatorIndex = rootImage.indexOf("|");
				if (separatorIndex > 0) {
					// Image in the form url|repoName -> repository image
					String downloadUrl = rootImage.substring(0, separatorIndex);
					String imageName = rootImage.substring(separatorIndex + 1);
					log.info("Importing layered root-image {} from {}", imageName, downloadUrl);
					// Download file
					try {
						File cacheDirectory = new File(flotoHome, "cache/repositories");
						File repoFile = new File(cacheDirectory, downloadUrl.replaceAll("[^A-Za-z0-9_.-]", "_"));
						if (!repoFile.exists()) {
							log.info("Downloading repo file from {} to {}", downloadUrl, repoFile);
							// Download repoFile
							FileUtils.forceMkdir(cacheDirectory);

							File tempFile = new File("download-" + UUID.randomUUID().toString());
							FileUtils.copyURLToFile(new URL(downloadUrl), tempFile);
							FileUtils.moveFile(tempFile, repoFile);
						} else {
							log.info("Using cached repo file {}", repoFile);
						}
						createDockerTarget(host).path("/images/load").request().post(Entity.entity(repoFile, "application/gzip")).close();
						log.info("Repository uploaded", repoFile);

						// now tag the new image

						createDockerTarget(host).path("/images/{imageName}/tag").resolveTemplate("imageName", imageName).queryParam("repo", shortRepoName).queryParam("tag", tagName).request().post(null).close();
						log.info("Image tagged as {}:{}", shortRepoName, tagName);
					} catch (IOException e) {
						throw Throwables.propagate(e);
					}
				} else {
					log.info("Importing flat root-image via HTTP");
					WebTarget dockerTarget = createDockerTarget(host).path("/images/create").queryParam("fromSrc", rootImage).queryParam("repo", repoName);
					dockerTarget.request().post(null).close();
				}
			}

			ObjectNode fromStep = new ObjectNode(new JsonNodeFactory(true));
			fromStep.put("type", "FROM");
			fromStep.put("line", repoName);
			buildSteps.set(0, fromStep);

		}
		String baseImageName = this.createBaseImageName(image);
		this.buildImage(baseImageName, buildSteps, host, this.manifest, Collections.emptyMap(), buildLogStream);
		return baseImageName;
	}

	public String createRootImageName(String rootImage) {
		String repoName = null;
		if (rootImage.startsWith("http://")) {
			String shortRepoName = "root-image";
			String tagName = rootImage.replaceAll("[^A-Za-z0-9_.-]", "_");
			repoName = shortRepoName + ":" + tagName;
		} else {
			repoName = rootImage;
		}
		return repoName;
	}

	private void createFinalImage(Host host, Container container, String baseImageName, FileOutputStream buildLogStream) {
		log.info("Will use base-image={}", baseImageName);
		List<JsonNode> configureSteps = new ArrayList<>(container.configureSteps);
		ObjectNode fromBuildStep = JsonNodeFactory.instance.objectNode().put("type", "FROM").put("line", baseImageName);

		configureSteps.add(0, fromBuildStep);
		Map<String, Object> globalConfig = createGlobalConfig(this.manifest);
		buildImage(container.name, configureSteps, host, this.manifest, globalConfig, buildLogStream);
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

	private void restartContainer(String containerName) {
		Manifest manifest = this.manifest;
		Container container = findContainer(containerName, manifest);
		Host host = findHost(container.host, manifest);
		WebTarget dockerTarget = createDockerTarget(host).path("/containers/" + container.name + "/restart").queryParam("t", "10");
		Builder request = dockerTarget.request();
		Response response = request.post(Entity.text(""));
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

		HashMap<Object, Object> labels = Maps.newHashMap();
		labels.put("projectRevision", container.projectRevision);
		labels.put("buildHash", container.buildHash);
		createConfig.put("Labels", labels);

		Builder request = dockerTarget.request();
		Response response = request.post(Entity.json(createConfig));
		response.close();
	}

	private void destroyContainer(String containerName, Host host) {
		WebTarget dockerTarget = createDockerTarget(host);
		try {
			try {
				Response killResponse = dockerTarget.path("/containers/" + containerName + "/kill").request().post(Entity.text(""));
				killResponse.close();
			} catch (Throwable t) {
				if (t.getMessage().contains("notrunning")) {
					// already stopped
				} else {
					throw new RuntimeException(t);
				}

			}
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
		Map<String, String> buildArgs = new HashMap<>();
		if (useProxy) {
			buildArgs.put("http_proxy", httpProxyUrl);
			buildArgs.put("https_proxy", httpProxyUrl);
		}
		String buildArgsString = null;
		try {
			buildArgsString = URLEncoder.encode(new ObjectMapper().writeValueAsString(buildArgs));
		} catch (JsonProcessingException e) {
			Throwables.propagate(e);
		}
		WebTarget webTarget = buildTarget.queryParam("t", imageName).queryParam("forcerm", "true").queryParam("buildargs", buildArgsString);
		Response response = webTarget.request().post(Entity.entity(new StreamingOutput() {
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
							templateTarEntry.setModTime(0);
							out.putNextEntry(templateTarEntry);
							IOUtils.write(templateBytes, out);
							out.closeEntry();
						} else if ("ADD_FILE".equals(type)) {
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
							JsonNode options = step.path("options");
							JsonNode newName = options.path("newName");

							String source = step.path("source").asText();
							JsonNode excludeDirectories = options.path("excludeDirectories");
							JsonNode excludeFiles = options.path("excludeFiles");

							File sourceFile = new File(source);
							Collection<File> files = getCopyDirectoryFiles(excludeDirectories, excludeFiles, sourceFile);
							String destination = step.path("destination").asText();

							for (File file : files) {
								String targetDirName = newName != null && !newName.isMissingNode() ? newName.asText() : sourceFile.getName();
								String relative = targetDirName + "/" + FilenameUtils.separatorsToUnix(sourceFile.toURI().relativize(file.toURI()).getPath());
								TarEntry templateTarEntry = new TarEntry(file, "." + destination + "/" + relative);
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

	private Collection<File> getCopyDirectoryFiles(JsonNode excludeDirectories, JsonNode excludeFiles, File sourceFile) {
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
		Collection<File> files;
		if (sourceFile.isDirectory()) {
			files = FileUtils.listFiles(sourceFile, fileFilter, directoryFilter);
		} else {
			files = Collections.singleton(sourceFile);
		}
		return files;
	}

	private String createDockerFile(List<JsonNode> buildSteps) {
		DockerfileHelper dockerfileHelper = new DockerfileHelper();
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

	public WebTarget createDockerTarget(Host host) {
		String url;
		if (host.dockerUrl == null) {
			url = "http://" + getExternalHostIp(host) + ":2375";
		} else {
			url = host.dockerUrl;
		}
		return client.target(url);
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
		Container containerMaybe = findContainerMaybe(containerName, manifest);
		if (containerMaybe == null) {
			throw new IllegalArgumentException("Unknown container: " + containerName);
		}
		return containerMaybe;
	}

	private Container findContainerMaybe(String containerName, Manifest manifest) {
		for (Container candidate : manifest.containers) {
			if (containerName.equals(candidate.name)) {
				return candidate;
			}
		}
		return null;
	}

	public String getRootImage(Image image) {
		return image.buildSteps.stream().filter(node -> node.get("type").textValue().equals("FROM"))
			.map(node -> node.get("line").textValue()).findFirst().get();
	}

	private String createBaseImageName(Image image) {
		return image.name + "-image";
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

	public TaskInfo<Void> stopContainers(List<String> containers) {
		return taskService.startTask("Stop containers " + Joiner.on(", ").join(containers), () -> {
			log.info("Stopping containers {}", containers);

			List<Exception> errors = new ArrayList<>();
			for (String container : containers) {
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
			for (String container : containers) {
				try {
					restartContainer(container);
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
			for (String container : containers) {
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

						state.needsRedeploy = true;
						String projectRevision = container.path("Labels").path("projectRevision").textValue();
						state.projectRevision = projectRevision;
						Container manifestContainer = findContainerMaybe(name, manifest);
						if (manifestContainer != null && manifestContainer.buildHash != null) {
							String buildHash = container.path("Labels").path("buildHash").textValue();
							if (buildHash != null) {
								if (buildHash.equals(manifestContainer.buildHash)) {
									state.needsRedeploy = false;
								}
							}
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

	public String getDocumentString(String documentId) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectReader reader = mapper.reader(Map.class);
			Map<String, Object> manifestJson = reader.readValue(manifestString);
			return getDocumentString(documentId, manifestJson);
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}

	}

	public String getDocumentString(String documentId, Map<String, Object> manifestJson) {
		Manifest manifest = this.manifest;
		List<DocumentDefinition> documents = manifest.documents;
		DocumentDefinition foundDocument = null;
		for (DocumentDefinition document : documents) {
			if (document.id.equals(documentId)) {
				foundDocument = document;
				break;
			}
		}
		if (foundDocument == null) {
			throw new WebApplicationException("The document could not be found", Response.Status.NOT_FOUND);
		}
		try {
			Configuration cfg = new Configuration();
			File inputFile = new File(foundDocument.template);
			Path rootPath = rootDefinitionFile.toPath().getRoot();
			cfg.setDirectoryForTemplateLoading(rootPath.toFile());
			cfg.setLogTemplateExceptions(false);
			cfg.setObjectWrapper(new DefaultObjectWrapper());
			cfg.setNumberFormat("0.######");

			cfg.setDefaultEncoding("UTF-8");
			cfg.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
			cfg.setIncompatibleImprovements(new Version(2, 3, 20));


			Path relativePath = rootPath.relativize(inputFile.toPath());
			Template template = cfg.getTemplate(relativePath.toString().replaceAll("\\\\", "/"));
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			OutputStreamWriter writer = new OutputStreamWriter(baos);

			Map<String, Object> root = new HashMap<>();

			root.put("manifest", manifestJson);
			Map<String, Object> containerMap = new HashMap<>();
			List<Map<String, Object>> containers = (List<Map<String, Object>>) manifestJson.get("containers");
			for (Map<String, Object> container : containers) {
				containerMap.put((String) container.get("name"), container);
			}
			root.put("containers", containerMap);

			template.process(root, writer);
			return new String(baos.toByteArray());
		} catch (Exception e) {
			throw Throwables.propagate(e);
		}

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
					// Image and container templates are validated during buildHash generation


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
							try {
								new TemplateUtil().getTemplate(step, globalConfig);
							} catch (Throwable throwable) {
								String template = step.path("template").asText();
								log.warn("Unable to generate template " + template, throwable);
							}
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
		File overrideVmTemplate = new File("/floto/vmtemplates/vmtemplate.ova");
		if (overrideVmTemplate.exists()) {
			// Use override vm template
			url = overrideVmTemplate.toURI().toURL();
		}
		return url;
	}

	public ImageRegistry getImageRegistry() {
		return imageRegistry;
	}

	public File getFlotoHome() {
		return flotoHome;
	}

	public Throwable getManifestCompilationError() {
		return manifestCompilationError;
	}

	public File getRootDefinitionFile() {
		return rootDefinitionFile;
	}

	public void setRootDefinitionFile(File rootDefinitionFile) {
		this.rootDefinitionFile = rootDefinitionFile;
	}

	public void setActivePatch(PatchInfo activePatch) {
		this.activePatch = activePatch;
		this.settings.activePatchId = activePatch.id;
		this.settings.activeSite = activePatch.siteName;
		saveSettings();
	}


	public void saveSettings() {
		try {
			File tmpSettingsFile = new File(flotoHome, ".tmp-settings-" + UUID.randomUUID().toString() + ".json");
			new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(tmpSettingsFile, settings);
			Files.move(tmpSettingsFile.toPath(), new File(flotoHome, "settings.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			if (tmpSettingsFile.exists()) {
				FileUtils.forceDelete(tmpSettingsFile);
			}
		} catch (Throwable e) {
			Throwables.propagate(e);
		}
	}

	public void loadSettings() {
		try {
			File settingsFile = new File(flotoHome, "settings.json");
			if (settingsFile.exists()) {
				this.settings = new ObjectMapper().readValue(settingsFile, FlotoSettings.class);
			}
		} catch (Throwable e) {
			Throwables.propagate(e);
		}
	}

	public FlotoSettings getSettings() {
		return settings;
	}

	public void setManifestCompilationError(Throwable manifestCompilationError) {
		this.manifestCompilationError = manifestCompilationError;
	}

	public void setPatchDescription(PatchDescription patchDescription) {
		this.patchDescription = patchDescription;
	}

	public PatchDescription getPatchDescription() {
		return patchDescription;
	}

	public FlotoCommonParameters getCommonParameters() {
		return commonParameters;
	}
}
