package io.github.floto.builder;

import io.github.floto.core.FlotoService;
import io.github.floto.core.FlotoService.DeploymentMode;
import io.github.floto.core.jobs.ExportVmJob;
import io.github.floto.core.jobs.RedeployVmJob;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.util.task.TaskService;

import java.util.Arrays;
import java.util.List;
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
import com.google.common.collect.Lists;

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

            TaskService taskService = new TaskService();
            flotoService = new FlotoService(parameters, taskService);
            flotoService.compileManifest().getResultFuture().get();
            flotoService.validateTemplates();

            flotoService.enableBuildOutputDump(true);

	        Manifest manifest = flotoService.getManifest();

            // Find host
	        List<String> deploymentList = Lists.newArrayList();
	        Container mainContainer = manifest.findContainer("registry");
	        if(mainContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without registry");
	        }
	        deploymentList.add(mainContainer.name);
	        Host deploymentHost = manifest.findHost(mainContainer.host);
	        Container flotoContainer = manifest.findContainer("deployment");
	        if(flotoContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without floto");
	        }
	        if(!flotoContainer.host.equals(deploymentHost.name)) {
	        	throw new IllegalStateException("registry and floto must run on the same host");
	        }
	        deploymentList.add(flotoContainer.name);
	        // registryUi is optional but if it is present in config it must run on same host
	        Container registryUiContainer = manifest.findContainer("registry-ui");
	        if(registryUiContainer != null) {
	        	if(!registryUiContainer.host.equals(deploymentHost.name)) {
	        		throw new IllegalStateException("if registryUI is present it must run on same host");
	        	}
	        	deploymentList.add(registryUiContainer.name);
	        }
	        

            log.info("Will deploy={}", deploymentList);
            flotoService.setExternalHostIp(deploymentHost.name, "192.168.119.129");
            flotoService.redeployContainers(deploymentList, DeploymentMode.fromScratch).getResultFuture().get();

//	        ExportVmJob exportVmJob = new ExportVmJob(flotoService, deploymentHost.name);
//	        exportVmJob.execute();

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
    
    private void run2() {
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
	        List<String> deploymentList = Lists.newArrayList();
	        Container mainContainer = manifest.findContainer("registry");
	        if(mainContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without registry");
	        }
	        deploymentList.add(mainContainer.name);
	        Host deploymentHost = manifest.findHost(mainContainer.host);
	        Container flotoContainer = manifest.findContainer("deployment");
	        if(flotoContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without floto");
	        }
	        if(!flotoContainer.host.equals(deploymentHost.name)) {
	        	throw new IllegalStateException("registry and floto must run on the same host");
	        }
	        deploymentList.add(flotoContainer.name);
	        // registryUi is optional but if it is present in config it must run on same host
	        Container registryUiContainer = manifest.findContainer("registry-ui");
	        if(registryUiContainer != null) {
	        	if(!registryUiContainer.host.equals(deploymentHost.name)) {
	        		throw new IllegalStateException("if registryUI is present it must run on same host");
	        	}
	        	deploymentList.add(registryUiContainer.name);
	        }
	        
            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, deploymentHost.name);
            redeployVmJob.execute();

            log.info("Will deploy={}", deploymentList);
            flotoService.redeployContainers(deploymentList, DeploymentMode.fromScratch).getResultFuture().get();

//	        ExportVmJob exportVmJob = new ExportVmJob(flotoService, deploymentHost.name);
//	        exportVmJob.execute();

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
