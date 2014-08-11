package io.github.floto.core.proxy;

import com.google.common.base.Throwables;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.eclipse.jetty.proxy.ConnectHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class HttpProxy implements Closeable {
    private Logger log = LoggerFactory.getLogger(HttpProxy.class);
    private int port;
    private Server server;
    private boolean cachingEnabled = false;
    private File cacheDirectory;

    public HttpProxy(int port) {
        this.port = port;
    }

    public void setCacheDirectory(File cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        cachingEnabled = true;
        try {
            FileUtils.forceMkdir(cacheDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public void start() {
        server = new Server(port);
        HandlerCollection handlers = new HandlerCollection();
        server.setHandler(handlers);

        // Setup proxy servlet
        ServletContextHandler context = new ServletContextHandler(handlers, "/", ServletContextHandler.SESSIONS);
        Servlet proxyServlet = createProxyServlet();
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

    private Servlet createProxyServlet() {
        if(cachingEnabled) {
            return createCachingProxyServlet();
        }
        return new org.eclipse.jetty.proxy.ProxyServlet();
    }

    private ProxyServlet createCachingProxyServlet() {
        CacheConfig.Builder configBuilder = CacheConfig.custom();
        configBuilder.setMaxCacheEntries(10000);
        configBuilder.setMaxObjectSize(2L*1024*1024*1024L);

        CachingHttpClientBuilder clientBuilder = CachingHttpClientBuilder.create();
        clientBuilder.setCacheConfig(configBuilder.build());

        File storageDirectory = new File(cacheDirectory, "storage");
        File resourceDirectory = new File(cacheDirectory, "resources");
        try {
            FileUtils.forceMkdir(storageDirectory);
            FileUtils.forceMkdir(resourceDirectory);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        PersistentHttpCacheStorage storage = new PersistentHttpCacheStorage(storageDirectory);
        clientBuilder.setHttpCacheStorage(storage);

        clientBuilder.setResourceFactory(new FileResourceFactory(resourceDirectory) {
            // Do not actually copy files
            @Override
            public Resource copy(String requestId, Resource resource) throws IOException {
                return resource;
            }
        });

        return new ProxyServlet(clientBuilder);
    }

    public void close() {
        try {
            server.stop();
        } catch (Throwable e) {
            log.error("Error stopping HTTP proxy", e);
        }
    }
}
