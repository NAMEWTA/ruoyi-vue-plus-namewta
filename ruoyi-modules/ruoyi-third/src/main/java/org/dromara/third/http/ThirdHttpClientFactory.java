package org.dromara.third.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/** Builds outbound clients; no provider selection or request orchestration belongs here. */
public class ThirdHttpClientFactory {
    public RestClient create(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(100, readTimeoutMs)));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public <T> T createTyped(RestClient client, Class<T> contract) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(contract);
    }
}
