package io.github.floto.builder;

import com.beust.jcommander.JCommander;
import com.google.common.base.Stopwatch;
import io.github.floto.core.FlotoService;
import io.github.floto.core.ssh.SshService;
import io.github.floto.core.tasks.HypervisorTask;
import io.github.floto.core.tasks.RedeployVmTask;
import io.github.floto.core.util.TemplateHelper;
import io.github.floto.core.virtualization.VmDescription;
import io.github.floto.core.virtualization.workstation.WorkstationHypervisorService;
import io.github.floto.dsl.model.Container;
import io.github.floto.dsl.model.Host;
import io.github.floto.dsl.model.Manifest;
import io.github.floto.dsl.model.VmConfiguration;
import io.github.floto.util.GitHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.Duration;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class FlotoBuilder {

    private Logger log = LoggerFactory.getLogger(FlotoBuilder.class);
    private FlotoBuilderParameters parameters = new FlotoBuilderParameters();

    private FlotoService flotoService;
    private String[] arguments;
    private TemplateHelper templateHelper = new TemplateHelper(FlotoBuilder.class, "templates");
    private Manifest manifest;

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

            File rootDefinitionFile = new File(parameters.rootDefinitionFile);
            log.info("Root definition file: {}", rootDefinitionFile);
            String gitDescription = new GitHelper(rootDefinitionFile.getParentFile()).describe();
            log.info("Git Description {}", gitDescription);
            flotoService = new FlotoService(parameters);
            flotoService.reload();

            flotoService.enableBuildOutputDump(true);

            manifest = flotoService.getManifest();

            // Find host
            if (manifest.hosts.size() != 1) {
                log.error("Expected one host, found: " + manifest.hosts.size());
                return;
            }

            Host host = manifest.hosts.get(0);
            RedeployVmTask redeployVmTask = new RedeployVmTask(flotoService, host.name);
            redeployVmTask.execute();

            for (Container container : manifest.containers) {
                flotoService.redeployContainers(Arrays.asList(container.name));
            }

            // TODO: delete intermediate images?
            // TODO: Zerofill disk?

            new HypervisorTask<Void>(manifest, host.name) {

                @Override
                public Void execute() throws Exception {
                    // Stop VM
                    hypervisorService.stopVm(host.name);
                    // Export Image
                    String exportName = host.exportName;
                    if(exportName == null) {
                        exportName = host.name + "_" + gitDescription + ".ova";
                    }

                    hypervisorService.exportVm(host.name, new File("vm/" + exportName).getAbsolutePath());
                    log.info("Exported to: {}", exportName);
                    return null;
                }
            }.execute();

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
