package io.github.floto.server;

import com.beust.jcommander.JCommander;
import io.github.floto.core.FlotoService;
import io.github.floto.core.HostService;
import io.github.floto.server.api.ContainersResource;
import io.github.floto.server.api.HostsResource;
import io.github.floto.server.api.ManifestResource;
import io.github.floto.server.util.ThrowableExceptionMapper;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.woelker.jimix.servlet.JimixServlet;

public class FlotoServer {
	private Logger log = LoggerFactory.getLogger(FlotoServer.class);
    private String[] args;
    private FlotoServerParameters parameters = new FlotoServerParameters();

    public FlotoServer(String[] args) {
        this.args = args;
    }


    public static void main(String[] args) {
		new FlotoServer(args).run();

	}

	private void run() {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

        JCommander jCommander = new JCommander();
        jCommander.setProgramName("FlotoServer");
        jCommander.addObject(parameters);
        if(args.length == 0) {
            jCommander.usage();
            return;
        }
        jCommander.parse(args);

		Server server = new Server(parameters.port);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		String resourceBase = FlotoServer.class.getResource("assets").toExternalForm();
		if(!resourceBase.startsWith("jar:")) {
			// Load assets from src directory during development, do not require recompilation
			resourceBase = "src/main/resources/io/github/floto/server/assets";
		}
		context.setResourceBase(resourceBase);
		context.setContextPath("/");
		server.setHandler(context);

		context.addServlet(new ServletHolder(new JimixServlet()), "/jimix/*");
		context.addServlet(new ServletHolder(new DefaultServlet()), "/*");
		ResourceConfig resourceConfig = new ResourceConfig();
		FlotoService flotoService = new FlotoService(parameters);
        try {
            flotoService.compileManifest();
        } catch(Throwable throwable) {
            // Error compiling manifest, continue anyway
            log.error("Error compiling manifest", throwable);
        }
        HostService hostService = new HostService(flotoService);

		resourceConfig.register(new ManifestResource(flotoService));
		resourceConfig.register(new ContainersResource(flotoService));
		resourceConfig.register(new HostsResource(flotoService, hostService));

		resourceConfig.register(new ThrowableExceptionMapper());
		ServletContainer servletContainer = new ServletContainer(resourceConfig);
		context.addServlet(new ServletHolder(servletContainer), "/api/*");
		try {
			server.start();
			log.info("Floto Server started on port {}", parameters.port);
            if(parameters.developmentMode) {
                log.info("Open your browser to http://localhost:{}/", parameters.port);
            }
			server.join();
		} catch (Exception e) {
			log.error("Could not start Floto Server", e);

		}
	}
}
