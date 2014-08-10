package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class HttpCacheTest {

    @Rule
    public JettyRule jettyRule = new JettyRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private CloseableHttpClient httpClient;
    private PersistentHttpCacheStorage storage;
    private File storageFolder;
    private File cacheFolder;

    @Before
    public void before() throws IOException {
        storageFolder = temporaryFolder.newFolder("storage");
        cacheFolder = temporaryFolder.newFolder("cache");
    }


    @Test
    public void testSimpleCache304() throws Exception {
        buildSimpleCache();
        verifyCaching304();
    }


    @Test
    public void testSimpleCacheMaxAge() throws Exception {
        buildSimpleCache();
        verifyCachingMaxAge();
    }

    @Test
    public void testPersistentCache304() throws Exception {
        buildPersistentCache();
        verifyCaching304();
    }

    @Test
    public void testPersistentCacheMaxAge() throws Exception {
        buildPersistentCache();
        verifyCaching304();
    }

    @Test
    public void testPersistentCachePersistence() throws Exception {
        buildPersistentCache();
        verifyCaching304();
        storage.close();
        buildPersistentCache();
        String responseC = IOUtils.toString(httpClient.execute(new HttpGet(jettyRule.createUri("foobar"))).getEntity().getContent());

        assertEquals("Invocation: 1", responseC);
    }

    private void buildSimpleCache() throws IOException {
        CachingHttpClientBuilder builder = CachingHttpClientBuilder.create();
        builder.setCacheDir(cacheFolder);
        httpClient = builder.build();
    }

    private void buildPersistentCache() throws IOException {
        CachingHttpClientBuilder builder = CachingHttpClientBuilder.create();
        storage = new PersistentHttpCacheStorage(storageFolder);
        builder.setHttpCacheStorage(storage);
        builder.setResourceFactory(new FileResourceFactory(cacheFolder) {
            @Override
            public Resource copy(String requestId, Resource resource) throws IOException {
                return resource;
            }
        });
        httpClient = builder.build();
    }


    private void verifyCaching304() throws IOException {
        jettyRule.addServlet(new HttpServlet() {
            int invocations = 0;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                if(Collections.list(req.getHeaders("If-None-Match")).contains("abc")) {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
                invocations++;
                resp.setHeader("ETag", "abc");
                resp.getOutputStream().print("Invocation: " + invocations);
            }
        }, "/foobar");

        String responseA = IOUtils.toString(httpClient.execute(new HttpGet(jettyRule.createUri("foobar"))).getEntity().getContent());
        String responseB = IOUtils.toString(httpClient.execute(new HttpGet(jettyRule.createUri("foobar"))).getEntity().getContent());

        assertEquals("Invocation: 1", responseA);
        assertEquals("Invocation: 1", responseB);
    }

    private void verifyCachingMaxAge() throws IOException {
        jettyRule.addServlet(new HttpServlet() {
            int invocations = 0;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                invocations++;
                resp.setHeader("Cache-Control", "max-age=100");
                resp.getOutputStream().print("Invocation: " + invocations);
            }
        }, "/foobar");

        String responseA = IOUtils.toString(httpClient.execute(new HttpGet(jettyRule.createUri("foobar"))).getEntity().getContent());
        String responseB = IOUtils.toString(httpClient.execute(new HttpGet(jettyRule.createUri("foobar"))).getEntity().getContent());

        assertEquals("Invocation: 1", responseA);
        assertEquals("Invocation: 1", responseB);
    }

}
