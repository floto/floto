package io.github.floto.core.proxy;

import com.google.common.base.Throwables;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.rules.ExternalResource;

import javax.servlet.Servlet;

public class JettyRule extends ExternalResource {

    private int port;
    private Server server;
    private ServletContextHandler context;

    @Override
    protected void before() throws Throwable {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        server.setHandler(context);

        server.start();
        port = connector.getLocalPort();
    }

    @Override
    protected void after() {
        try {
            server.stop();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    public int getPort() {
        return port;
    }

    public Server getServer() {
        return server;
    }

    public String createUri(String localPart) {
        return "http://localhost:" + port + "/" + localPart;
    }

    public void addServlet(Servlet servlet, String path) {
        context.addServlet(new ServletHolder(servlet), path);
    }
}
