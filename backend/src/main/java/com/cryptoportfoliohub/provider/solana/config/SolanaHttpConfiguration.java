package com.cryptoportfoliohub.provider.solana.config;

import java.net.http.HttpClient;
import com.cryptoportfoliohub.provider.http.BoundedProviderRetryInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
public class SolanaHttpConfiguration {

    @Bean
    RestClient solanaRestClient(SolanaProviderProperties properties,
            BoundedProviderRetryInterceptor retryInterceptor) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).requestInterceptor(retryInterceptor).build();
    }
}
