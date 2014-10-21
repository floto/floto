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
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.beust.jcommander.JCommander;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
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
	        Container registryContainer = manifest.findContainer("registry");
	        if(registryContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without registry");
	        }
	        Host deploymentHost = manifest.findHost(registryContainer.host);
	        
            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, deploymentHost.name);
            redeployVmJob.execute();
            
            flotoService.buildDeployerVM(deploymentHost);


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
	        List<Container> deploymentList = Lists.newArrayList();
	        Container registryContainer = manifest.findContainer("registry");
	        if(registryContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without registry");
	        }
	        deploymentList.add(registryContainer);
	        Host deploymentHost = manifest.findHost(registryContainer.host);
	        Container flotoContainer = manifest.findContainer("floto");
	        if(flotoContainer == null) {
	        	throw new IllegalStateException("Cannot create deployer-VM without floto");
	        }
	        if(!flotoContainer.host.equals(deploymentHost.name)) {
	        	throw new IllegalStateException("registry and floto must run on the same host");
	        }
	        deploymentList.add(flotoContainer);
	        // registryUi is optional but if it is present in config it must run on same host
	        Container registryUiContainer = manifest.findContainer("registry-ui");
	        if(registryUiContainer != null) {
	        	if(!registryUiContainer.host.equals(deploymentHost.name)) {
	        		throw new IllegalStateException("if registryUI is present it must run on same host");
	        	}
	        	deploymentList.add(registryUiContainer);
	        }
	        
            RedeployVmJob redeployVmJob = new RedeployVmJob(flotoService, deploymentHost.name);
            redeployVmJob.execute();

            log.info("Will deploy={}", deploymentList.stream().map(c -> c.name).collect(Collectors.toList()));
            flotoService.redeployContainers(deploymentList.stream().map(c -> c.name).collect(Collectors.toList()), DeploymentMode.fromScratch, true, true).getResultFuture().get();
            
            // build remaining images and deploy them to registry
            
            manifest.containers.stream().filter(c -> !deploymentList.contains(c)).
            	forEach(c -> {
            		try {
            			flotoService.redeployContainers(Arrays.asList(c.name), DeploymentMode.fromScratch, false, true).getResultFuture().get();
            		}
            		catch(Exception ex) {
            			throw Throwables.propagate(ex);
            		}
            	});

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
