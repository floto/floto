package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

public class ProxyServletTest {

    @Rule
    public JettyRule destinationJettyRule = new JettyRule();

    @Rule
    public JettyRule proxyJettyRule = new JettyRule();

    private CloseableHttpClient httpClient;


    @Before
    public void setUp() throws Exception {
        httpClient = HttpClientBuilder.create().setProxy(new HttpHost("localhost", proxyJettyRule.getPort())).disableRedirectHandling().build();
        proxyJettyRule.addServlet(new ProxyServlet(HttpClientBuilder.create()), "/*");
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testSimpleGet() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.getOutputStream().print("foobaz");
            }
        }, "/foobar");

        CloseableHttpResponse response = httpClient.execute(new HttpGet(destinationJettyRule.createUri("foobar")));
        String body = IOUtils.toString(response.getEntity().getContent());

        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("foobaz", body);

    }

    @Test
    public void testDontFollowRedirects() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.sendRedirect("otherlocation");
            }
        }, "/foobar");

        CloseableHttpResponse response = httpClient.execute(new HttpGet(destinationJettyRule.createUri("foobar")));
        String body = IOUtils.toString(response.getEntity().getContent());

        assertEquals(302, response.getStatusLine().getStatusCode());
        assertEquals(destinationJettyRule.createUri("otherlocation"), response.getFirstHeader("Location").getValue());
        assertEquals("", body);

    }

}