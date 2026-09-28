package com.cryptoportfoliohub.provider.hyperliquid;

import java.net.http.HttpClient;
import java.time.Duration;
import com.cryptoportfoliohub.provider.http.BoundedProviderRetryInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HyperliquidHttpConfiguration {

    @Bean
    @Qualifier("hyperliquidInfoRestClient")
    RestClient hyperliquidInfoRestClient(BoundedProviderRetryInterceptor retryInterceptor) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).requestInterceptor(retryInterceptor).build();
    }
}
