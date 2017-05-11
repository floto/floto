package io.github.floto.builder;

import com.beust.jcommander.JCommander;
import com.google.common.base.Stopwatch;
import com.vmware.vim25.FloatOption;

import io.github.floto.core.FlotoService;
import io.github.floto.core.HostService;
import io.github.floto.core.ParameterReader;
import io.github.floto.core.FlotoService.DeploymentMode;
import io.github.floto.core.jobs.ExportVmJob;
import io.github.floto.core.jobs.RedeployVmJob;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.DocumentDefinition;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FlotoBuilder {

    private Logger log = LoggerFactory.getLogger(FlotoBuilder.class);
    private FlotoBuilderParameters parameters = new FlotoBuilderParameters();

    private FlotoService flotoService;
    private String[] arguments;

	public FlotoBuilder(String[] arguments) {
        this.arguments = arguments;
    }

    public static void main(String[] args) {
        new FlotoBuilder(args).run();
    }

    private void run() {
        try {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();

            JCommander jCommander = new JCommander();
            jCommander.setProgramName("FlotoServer");
            jCommander.addObject(parameters);
            if(arguments.length == 0) {
                jCommander.usage();
                return;
            }
            jCommander.parse(arguments);

            log.info("Starting Floto Builder");
            Stopwatch stopwatch = Stopwatch.createStarted();

            File flotoHome = ParameterReader.getFlotoHome(parameters);
            TaskService taskService = new TaskService(flotoHome);
            flotoService = new FlotoService(parameters, taskService, flotoHome);
			new HostService(flotoService);
			TaskInfo<Void> taskInfo = flotoService.compileManifest();
			taskInfo.getResultFuture().get();
			if(taskInfo.getNumberOfWarnings() > 0) {
				log.error("Aborting build due to {} warnings", taskInfo.getNumberOfWarnings());
				System.exit(2);
			}
			if(parameters.compileCheck) {
				log.info("Compile check completed successfully");
				System.exit(0);
			}

            flotoService.enableBuildOutputDump(true);

	        Manifest manifest = flotoService.getManifest();

            // Find host
            if (manifest.hosts.size() != 1) {
                log.error("Expected one host, found: " + manifest.hosts.size());
                return;
            }
			Host host = manifest.hosts.get(0);
			File documentsDirectory = new File(new File("vm/" + host.exportName).getParentFile(), "documents");

			log.info("Creating documents: {}", documentsDirectory.getAbsolutePath());
			FileUtils.forceMkdir(documentsDirectory);

			for(DocumentDefinition document: manifest.documents) {
				String documentString = flotoService.getDocumentString(document.id);
				String extension = "";
				String template = document.template;
				int lastDotIndex = template.lastIndexOf(".");
				if(lastDotIndex > 0) {
					extension = template.substring(lastDotIndex);
				}
				FileUtils.write(new File(documentsDirectory, document.title + extension), documentString);
			}



            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, host.name);
            redeployVmJob.execute();
//            flotoService.setExternalHostIp(host.name, "192.168.119.129");
            
            List<String> containers = manifest.containers.stream().map(c -> c.name).collect(Collectors.toList());
            flotoService.redeployContainers(containers, DeploymentMode.fromRootImage).getResultFuture().get();

	        ExportVmJob exportVmJob = new ExportVmJob(flotoService, host.name);
	        exportVmJob.execute();

            log.info("Build complete");
            stopwatch.stop();
            Duration duration = Duration.standardSeconds(stopwatch.elapsed(TimeUnit.SECONDS));
            log.info("Total time: {}", PeriodFormat.wordBased(Locale.ROOT).print(duration.toPeriod()));
        } catch (Throwable e) {
            log.error("Error running build", e);
            log.error("Build failed");
            System.exit(1);
        } finally {
            IOUtils.closeQuietly(flotoService);
        }
    }

}
