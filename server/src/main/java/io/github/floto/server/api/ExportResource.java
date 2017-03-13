package io.github.floto.server.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.commons.FileUtils;
import org.zeroturnaround.zip.commons.IOUtils;

import com.google.common.collect.Lists;

import io.github.floto.core.FlotoService;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskService;

@Path("export")
public class ExportResource {
	private static final Logger log = LoggerFactory.getLogger(ExportResource.class);
	DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");
	private FlotoService flotoService;
	private TaskService taskService;

	public ExportResource(FlotoService flotoService, TaskService taskService) {
		this.flotoService = flotoService;
		this.taskService = taskService;
	}

	@GET
	@Path("container-logs")
	@Produces("application/zip")
	public Response getContainerLogs() {
		Manifest manifest = flotoService.getManifest();

		
		String newLine = System.lineSeparator();
		String zipFileName = "container-logs-" + manifest.site.path("domainName").asText() + "-"
				+ dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + ".zip";
		String zipFilePath = System.getProperty("java.io.tmpdir") + File.pathSeparator + zipFileName;

		Response.ResponseBuilder response = Response.ok(new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream printStream = new PrintStream(baos);

				ExecutorService executor = Executors.newFixedThreadPool(16);
				List<Future<String>> taskList = new ArrayList<>();

				try (FileSystem zipFileSystem = createZipFileSystem(zipFilePath)) {
					final java.nio.file.Path root = zipFileSystem.getPath("/");
					
					// shuffle list to prevent multiple connections to the same
					// host at the same time
					List<Container> containerList = Lists.newArrayList(manifest.containers);
					Collections.shuffle(containerList);

					containerList.forEach(container -> {
						Future<String> task = executor.submit(() -> {
							StringBuilder sb = new StringBuilder();
							sb.append("Exporting logs for container ").append(container.name).append(newLine);
							final java.nio.file.Path dest = zipFileSystem.getPath(root.toString(),
									container.name + ".log");
							try (OutputStream out = Files.newOutputStream(dest)) {
								flotoService.getLog(container.name, out);
								sb.append("Exported logs for container ").append(container.name).append(newLine);
							} catch (Throwable throwable) {
								sb.append("Error exporting logs for container ").append(container.name).append(newLine);
								throwable.printStackTrace(printStream);
								log.warn("Error getting log for {}", container.name, throwable);
							}

							return sb.toString();
						});
						taskList.add(task);
					});

					executor.shutdown();

					for (Future<String> task : taskList) {
						try {
							String value = task.get();
							printStream.append(value);
						} catch (Throwable throwable) {
							throwable.printStackTrace(printStream);
						}
					}

					try (OutputStream out = Files
							.newOutputStream(zipFileSystem.getPath(root.toString(), "export.log.txt"))) {
						printStream.close();
						baos.writeTo(out);
					} catch (Throwable throwable) {
						log.warn("Error writing log", throwable);
					}
				}

				File zipFile = new File(zipFilePath);

				try (FileInputStream fis = new FileInputStream(zipFile)) {
					IOUtils.copy(fis, output);
				} finally {
					FileUtils.deleteQuietly(zipFile);
				}
			}
		});
		response.header("Content-Disposition", "attachment; filename=" + zipFileName);
		return response.build();
	}

	@GET
	@Path("build-logs")
	@Produces("application/zip")
	public Response getBuildLogs() {
		Manifest manifest = flotoService.getManifest();

		Response.ResponseBuilder response = Response.ok(new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream printStream = new PrintStream(baos);
				ZipOutputStream zipOutputStream = new ZipOutputStream(output);
				for (Container container : manifest.containers) {
					printStream.append("Exporting build logs for container ").append(container.name).println();
					zipOutputStream.putNextEntry(new ZipEntry(container.name + "-build.log"));
					try {
						flotoService.getBuildLog(container.name, zipOutputStream);
						printStream.append("Exported build logs for container ").append(container.name).println();
					} catch (Throwable throwable) {
						printStream.append("Error exporting build logs for container ").append(container.name).println();
						throwable.printStackTrace(printStream);
						log.warn("Error getting log for {}", container.name, throwable);
					} finally {
						zipOutputStream.closeEntry();
					}
				}
				zipOutputStream.putNextEntry(new ZipEntry("build-export.log.txt"));
				try {
					printStream.close();
					baos.writeTo(zipOutputStream);
				} catch (Throwable throwable) {
					log.warn("Error writing log", throwable);
				} finally {
					zipOutputStream.closeEntry();
				}
				zipOutputStream.close();
			}
		});
		response.header("Content-Disposition",
			"attachment; filename=container-build-logs-" + manifest.site.path("domainName").asText() + "-" + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + ".zip");
		return response.build();
	}

	@GET
	@Path("task-logs")
	@Produces("application/zip")
	public Response getTaskLogs() {
		Manifest manifest = flotoService.getManifest();

		Response.ResponseBuilder response = Response.ok(new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream printStream = new PrintStream(baos);
				ZipOutputStream zipOutputStream = new ZipOutputStream(output);
				for (int taskNumber : taskService.getTaskNumbers()) {
					printStream.append("Exporting task " + taskNumber).println();
					String taskId = "" + taskNumber;
					try {
						zipOutputStream.putNextEntry(new ZipEntry(taskNumber+"/info.json"));
						FileUtils.copy(taskService.getTaskInfoFile(taskId), zipOutputStream);
						printStream.append("Exported build logs for task ").append(taskId).println();
					} catch (Throwable throwable) {
						printStream.append("Error exporting build logs for task ").append(taskId).println();
						throwable.printStackTrace(printStream);
						log.warn("Error getting log for task {}", taskId, throwable);
					} finally {
						zipOutputStream.closeEntry();
					}
					try {
						zipOutputStream.putNextEntry(new ZipEntry(taskNumber+"/log.json"));
						File logFile = taskService.getLogFile(taskId);
						FileUtils.copy(logFile, zipOutputStream);
						printStream.append("Exported task logs for task ").append(taskId).println();
					} catch (Throwable throwable) {
						printStream.append("Error exporting build logs for task ").append(taskId).println();
						throwable.printStackTrace(printStream);
						log.warn("Error getting log for task {}", taskId, throwable);
					} finally {
						zipOutputStream.closeEntry();
					}
				}
				zipOutputStream.putNextEntry(new ZipEntry("build-export.log.txt"));
				try {
					printStream.close();
					baos.writeTo(zipOutputStream);
				} catch (Throwable throwable) {
					log.warn("Error writing log", throwable);
				} finally {
					zipOutputStream.closeEntry();
				}
				zipOutputStream.close();
			}
		});
		response.header("Content-Disposition",
			"attachment; filename=task-logs-" + manifest.site.path("domainName").asText() + "-" + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + ".zip");
		return response.build();
	}

	private static FileSystem createZipFileSystem(String zipFileName) throws IOException {
		final java.nio.file.Path path = Paths.get(zipFileName);
		final URI uri = URI.create("jar:file:" + path.toUri().getPath());

		final Map<String, String> env = new HashMap<>();
		env.put("create", "true");
		return FileSystems.newFileSystem(uri, env);
	}
	
}
