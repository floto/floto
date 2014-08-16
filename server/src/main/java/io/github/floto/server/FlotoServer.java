package io.github.floto.server;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import io.github.floto.core.FlotoService;
import io.github.floto.core.HostService;
import io.github.floto.server.api.ContainersResource;
import io.github.floto.server.api.HostsResource;
import io.github.floto.server.api.ManifestResource;
import io.github.floto.server.api.TasksResource;
import io.github.floto.server.util.ThrowableExceptionMapper;

import io.github.floto.server.websocket.EventSocket;
import io.github.floto.util.task.TaskService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
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

// create custom ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JSR310Module());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // create JsonProvider to provide custom ObjectMapper
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        provider.setMapper(mapper);
        resourceConfig.register(provider);

        TaskService taskService = new TaskService();
        taskService.startTask("Endless task", () -> {
                    synchronized (this) {
                        this.wait();
                        return null;
                    }
                });
        FlotoService flotoService = new FlotoService(parameters, taskService);
        try {
            flotoService.compileManifest();
        } catch(Throwable throwable) {
            // Error compiling manifest, continue anyway
            log.error("Error compiling manifest", throwable);
        }
        HostService hostService = new HostService(flotoService);

		resourceConfig.register(new TasksResource(taskService));
		resourceConfig.register(new ManifestResource(flotoService));
		resourceConfig.register(new ContainersResource(flotoService));
		resourceConfig.register(new HostsResource(flotoService, hostService));

		resourceConfig.register(new ThrowableExceptionMapper());
		ServletContainer servletContainer = new ServletContainer(resourceConfig);

		context.addServlet(new ServletHolder(servletContainer), "/api/*");
		try {
            ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(context);
            // Add WebSocket endpoint to javax.websocket layer
            wscontainer.addEndpoint(EventSocket.class);
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
