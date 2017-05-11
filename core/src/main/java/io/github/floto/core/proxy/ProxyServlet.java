package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.impl.cache.CachingHttpClientBuilder;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ProxyServlet extends HttpServlet {
    private Logger log = LoggerFactory.getLogger(HttpProxy.class);

    private static Set<String> HOP_BY_HOP_HEADERS = new HashSet<>();

    static {
        HOP_BY_HOP_HEADERS.add("Connection");
        HOP_BY_HOP_HEADERS.add("Keep-Alive");
        HOP_BY_HOP_HEADERS.add("Proxy-Authenticate");
        HOP_BY_HOP_HEADERS.add("Proxy-Authorization");
        HOP_BY_HOP_HEADERS.add("TE");
        HOP_BY_HOP_HEADERS.add("Trailers");
        HOP_BY_HOP_HEADERS.add("Transfer-Encoding");
        HOP_BY_HOP_HEADERS.add("Upgrade");
    }

    private CloseableHttpClient httpClient;

    public ProxyServlet(HttpClientBuilder httpClientBuilder) {
        httpClientBuilder.disableCookieManagement();
        httpClientBuilder.disableRedirectHandling();
//        httpClientBuilder.setMaxConnPerRoute(100);
//        httpClientBuilder.setMaxConnTotal(1000);
        this.httpClient = httpClientBuilder.build();

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.debug("Proxying GET: {}", req.getRequestURL());
        String uri = req.getRequestURL().toString();
        if (req.getQueryString() != null) {
            uri += "?" + req.getQueryString();
        }
        HttpGet proxyRequest = new HttpGet(uri);
        for (String headerName : Collections.list(req.getHeaderNames())) {
            if (HOP_BY_HOP_HEADERS.contains(headerName)) {
                continue;
            }
            for (String value : Collections.list(req.getHeaders(headerName))) {
                proxyRequest.setHeader(new BasicHeader(headerName, value));
            }
        }
        CloseableHttpResponse proxyResponse = httpClient.execute(proxyRequest);
        resp.setStatus(proxyResponse.getCode());
        for (Header header : proxyResponse.getAllHeaders()) {
            if (HOP_BY_HOP_HEADERS.contains(header.getName())) {
                continue;
            }
            resp.setHeader(header.getName(), header.getValue());
        }
        if(proxyResponse.getEntity() != null && proxyResponse.getEntity().getContent() != null) {
            IOUtils.copy(proxyResponse.getEntity().getContent(), resp.getOutputStream());
        }
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.error("Unhandled HEAD request: {}", req.getRequestURL());
        super.doHead(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.error("Unhandled POST request: {}", req.getRequestURL());
        super.doPost(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.error("Unhandled PUT request: {}", req.getRequestURL());
        super.doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.error("Unhandled DELETE request: {}", req.getRequestURL());
        super.doDelete(req, resp);
    }
}
