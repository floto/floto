package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpUriRequest;
import org.apache.hc.client5.http.sync.methods.RequestBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

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

        assertEquals(200, response.getCode());
        assertEquals("foobaz", body);

    }

    @Test
    public void testSimpleGetQueryString() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                assertEquals("foo=bar", req.getQueryString());
            }
        }, "/foobar");

        CloseableHttpResponse response = httpClient.execute(new HttpGet(destinationJettyRule.createUri("foobar?foo=bar")));
        assertEquals(200, response.getCode());

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

        assertEquals(302, response.getCode());
        assertEquals(destinationJettyRule.createUri("otherlocation"), response.getFirstHeader("Location").getValue());
        assertEquals("", body);

    }

    @Test
    public void testClientHeaders() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                assertEquals("bar", req.getHeader("foo"));
            }
        }, "/foobar");

//        HttpUriRequest request = RequestBuilder.get().setUri(destinationJettyRule.createUri("foobar")).setHeader("foo", "bar").build();
//        CloseableHttpResponse response = httpClient.execute(request);
//
//        assertEquals(200, response.getCode());

    }

    @Test
    public void testStripHopByHopClientHeaders() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                assertEquals(null, req.getHeader("Proxy-Authenticate"));
            }
        }, "/foobar");

//        HttpUriRequest request = RequestBuilder.get().setUri(destinationJettyRule.createUri("foobar")).setHeader("Proxy-Authenticate", "bar").build();
//        CloseableHttpResponse response = httpClient.execute(request);
//
//        assertEquals(200, response.getCode());

    }

    @Test
    public void testStripHopByHopServerHeaders() throws Exception {
        destinationJettyRule.addServlet(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.setHeader("Proxy-Authenticate", "foobar");
            }
        }, "/foobar");

//        HttpUriRequest request = RequestBuilder.get().setUri(destinationJettyRule.createUri("foobar")).build();
//        CloseableHttpResponse response = httpClient.execute(request);
//
//        assertEquals(200, response.getStatusLine().getStatusCode());
//        assertEquals(null, response.getFirstHeader("Proxy-Authenticate"));

    }

}
