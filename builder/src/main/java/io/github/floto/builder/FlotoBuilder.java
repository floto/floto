package io.github.floto.builder;

import com.beust.jcommander.JCommander;
import com.google.common.base.Stopwatch;

import io.github.floto.core.FlotoService;
import io.github.floto.core.FlotoService.DeploymentMode;
import io.github.floto.core.jobs.ExportVmJob;
import io.github.floto.core.jobs.RedeployVmJob;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskService;

import org.apache.commons.io.IOUtils;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

            TaskService taskService = new TaskService();
            flotoService = new FlotoService(parameters, taskService);
            flotoService.compileManifest().getResultFuture().get();
            flotoService.validateTemplates();

            flotoService.enableBuildOutputDump(true);

	        Manifest manifest = flotoService.getManifest();

            // Find host
            if (manifest.hosts.size() != 1) {
                log.error("Expected one host, found: " + manifest.hosts.size());
                return;
            }

            Host host = manifest.hosts.get(0);
            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, host.name);
            redeployVmJob.execute();

            for (Container container : manifest.containers) {
                flotoService.redeployContainers(Arrays.asList(container.name), DeploymentMode.fromScratch).getResultFuture().get();
            }

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
