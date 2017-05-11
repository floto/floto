package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.impl.cache.CachingHttpClientBuilder;
import org.apache.hc.client5.http.impl.cache.FileResourceFactory;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.sync.methods.HttpGet;
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

    @Test
    public void testPersistentCacheVaryHeader() throws Exception {
        buildPersistentCache();
        jettyRule.addServlet(new HttpServlet() {
            int invocations = 0;

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                invocations++;
                resp.setHeader("Vary", "foo");
                resp.setHeader("Cache-Control", "max-age=100");
                resp.getOutputStream().print("Invocation: " + invocations + " "+req.getHeader("foo"));
            }
        }, "/foobar");

        HttpGet requestA = new HttpGet(jettyRule.createUri("foobar"));
        requestA.addHeader("foo", "bar");
        HttpGet requestB = new HttpGet(jettyRule.createUri("foobar"));
        requestB.addHeader("foo", "baz");
        String responseA = IOUtils.toString(httpClient.execute(requestA).getEntity().getContent());
        String responseB = IOUtils.toString(httpClient.execute(requestB).getEntity().getContent());

        assertEquals("Invocation: 1 bar", responseA);
        assertEquals("Invocation: 2 baz", responseB);

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
