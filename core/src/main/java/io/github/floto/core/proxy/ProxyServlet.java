package io.github.floto.core.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ProxyServlet extends HttpServlet {

    private HttpClient httpClient;

    public ProxyServlet(HttpClientBuilder httpClientBuilder) {
        httpClientBuilder.disableCookieManagement();
        httpClientBuilder.disableRedirectHandling();
        this.httpClient = httpClientBuilder.build();

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpGet proxyRequest = new HttpGet(req.getRequestURL().toString());
        HttpResponse proxyResponse = httpClient.execute(proxyRequest);
        resp.setStatus(proxyResponse.getStatusLine().getStatusCode());
        for (Header header : proxyResponse.getAllHeaders()) {
            // TODO: filter hop-by-hop headers
            resp.setHeader(header.getName(), header.getValue());
        }
        IOUtils.copy(proxyResponse.getEntity().getContent(), resp.getOutputStream());
    }

}
