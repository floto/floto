package io.github.floto.server.api;

import io.github.floto.core.FlotoService;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskService;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.commons.FileUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

        Response.ResponseBuilder response = Response.ok(new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(baos);
                ZipOutputStream zipOutputStream = new ZipOutputStream(output);
                for (Container container : manifest.containers) {
                    printStream.append("Exporting logs for container ").append(container.name).println();
                    zipOutputStream.putNextEntry(new ZipEntry(container.name + ".log"));
                    try {
                        flotoService.getLog(container.name, zipOutputStream);
                        printStream.append("Exported logs for container ").append(container.name).println();
                    } catch (Throwable throwable) {
                        printStream.append("Error exporting logs for container ").append(container.name).println();
                        throwable.printStackTrace(printStream);
                        log.warn("Error getting log for {}", container.name, throwable);
                    } finally {
                        zipOutputStream.closeEntry();
                    }
                }
                zipOutputStream.putNextEntry(new ZipEntry("export.log.txt"));
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
                "attachment; filename=container-logs-" + manifest.site.path("domainName").asText() + "-" + dateTimeFormatter.format(Instant.now().atOffset(ZoneOffset.UTC)) + ".zip");
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

}
