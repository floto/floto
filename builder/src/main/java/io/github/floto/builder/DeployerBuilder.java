package io.github.floto.builder;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.beust.jcommander.JCommander;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

import io.github.floto.core.FlotoService;
import io.github.floto.core.ParameterReader;
import io.github.floto.core.jobs.ExportVmJob;
import io.github.floto.core.jobs.RedeployVmJob;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskService;

public class DeployerBuilder {
	
	private Logger log = LoggerFactory.getLogger(FlotoBuilder.class);
    private FlotoBuilderParameters parameters = new FlotoBuilderParameters();

    private FlotoService flotoService;
    private String[] arguments;

	public DeployerBuilder(String[] arguments) {
        this.arguments = arguments;
    }

    public static void main(String[] args) {
        new DeployerBuilder(args).run();
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
            flotoService.compileManifest().getResultFuture().get();

            flotoService.enableBuildOutputDump(true);

	        Manifest manifest = flotoService.getManifest();

            // Find floto container
	        Container flotoContainer = manifest.findContainer("floto");
	        if(flotoContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without floto");
	        }
	        Host deploymentHost = manifest.findHost(flotoContainer.host);

            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, deploymentHost.name);
            redeployVmJob.execute();
            
            
            
            flotoService.redeployDeployerContainer(deploymentHost, flotoContainer, true, false);

            manifest.containers.stream().filter(container -> container != flotoContainer).
        	forEach(container -> {
                try {
                    flotoService.redeployDeployerContainer(deploymentHost, container, false, false);
                } catch (Exception ex) {
                    throw Throwables.propagate(ex);
                }
            });
            flotoService.startContainer("floto");

	        ExportVmJob exportVmJob = new ExportVmJob(flotoService, deploymentHost.name);
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
