package io.github.floto.core.proxy;

import com.google.common.base.Throwables;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class HttpProxy implements Closeable {
    private Logger log = LoggerFactory.getLogger(HttpProxy.class);
    private int port;
    private Server server;

    public HttpProxy(int port) {
        this.port = port;
    }

    public void start() {
        server = new Server(port);
        HandlerCollection handlers = new HandlerCollection();
        server.setHandler(handlers);

        // Setup proxy servlet
        ServletContextHandler context = new ServletContextHandler(handlers, "/", ServletContextHandler.SESSIONS);
        ProxyServlet proxyServlet = new ProxyServlet();
        ServletHolder proxyServletHolder = new ServletHolder(proxyServlet);
        context.addServlet(proxyServletHolder, "/*");

        proxyServletHolder.setInitParameter("maxThreads", "16");
        ConnectHandler proxy = new ConnectHandler();
        handlers.addHandler(proxy);
        try {
            server.start();
        } catch (Exception e) {
            Throwables.propagate(e);
        }

    }

    public void close() {
        try {
            server.stop();
        } catch (Throwable e) {
            log.error("Error stopping HTTP proxy", e);
        }
    }
}
