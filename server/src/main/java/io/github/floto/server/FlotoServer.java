package io.github.floto.server;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.SimpleType;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import io.github.floto.core.FlotoService;
import io.github.floto.core.HostService;
import io.github.floto.server.api.*;
import io.github.floto.server.util.ThrowableExceptionMapper;
import io.github.floto.server.websocket.TasksWebSocket;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.jsr356.server.AnnotatedServerEndpointConfig;
import org.eclipse.jetty.websocket.jsr356.server.BasicServerEndpointConfigurator;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.woelker.jimix.servlet.JimixServlet;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpoint;

import java.io.IOException;

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
        mapper.registerModule(new Module() {
            @Override
            public String getModuleName() {
                return "floto";
            }

            @Override
            public Version version() {
                return Version.unknownVersion();
            }

            @Override
            public void setupModule(SetupContext context) {
                SimpleSerializers simpleSerializers = new SimpleSerializers();
                simpleSerializers.addSerializer(new TaskInfoSerializer());
                context.addSerializers(simpleSerializers);
            }
        });

        // create JsonProvider to provide custom ObjectMapper
        JacksonJaxbJsonProvider provider = new JacksonJaxbJsonProvider();
        provider.setMapper(mapper);
        resourceConfig.register(provider);

        TaskService taskService = new TaskService();
        FlotoService flotoService = new FlotoService(parameters, taskService);
        HostService hostService = new HostService(flotoService);
        try {
            flotoService.compileManifest().getCompletionStage().thenAccept((x)->{
                hostService.reconfigureVms();
            });
        } catch(Throwable throwable) {
            // Error compiling manifest, continue anyway
            log.error("Error compiling manifest", throwable);
        }

		resourceConfig.register(new TasksResource(taskService));
		resourceConfig.register(new ManifestResource(flotoService));
		resourceConfig.register(new ContainersResource(flotoService));
		resourceConfig.register(new HostsResource(flotoService, hostService, taskService));
		resourceConfig.register(new ExportResource(flotoService));
		resourceConfig.register(new InfoResource());
		resourceConfig.register(new ConfigResource());
		resourceConfig.register(new VmTemplateResource());

		resourceConfig.register(new ThrowableExceptionMapper());
		ServletContainer servletContainer = new ServletContainer(resourceConfig);

		context.addServlet(new ServletHolder(servletContainer), "/api/*");
		try {
            registerWebsockets(context, taskService);
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

    private void registerWebsockets(ServletContextHandler context, final TaskService taskService) throws DeploymentException {
        ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(context);
        // Add WebSocket endpoint to javax.websocket layer
//            wscontainer.addEndpoint(EventSocket.class);
        ServerEndpoint anno = TasksWebSocket.class.getAnnotation(ServerEndpoint.class);
        wscontainer.addEndpoint(new AnnotatedServerEndpointConfig(TasksWebSocket.class, anno) {
            @Override
            public Configurator getConfigurator() {
                return new BasicServerEndpointConfigurator() {
                    @Override
                    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
                        return (T) new TasksWebSocket(taskService);
                    }
                };
            }
        });
    }

    private static class TaskInfoSerializer extends StdSerializer<TaskInfo<?>> {
        public TaskInfoSerializer() {
            super(SimpleType.construct(TaskInfo.class));
        }

        @Override
        public void serialize(TaskInfo taskInfo, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
            jgen.writeStartObject();
            jgen.writeStringField("taskId", taskInfo.getId());
            jgen.writeStringField("title", taskInfo.getTitle());
            jgen.writeEndObject();
        }
    }
}
