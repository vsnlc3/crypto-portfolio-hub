package com.cryptoportfoliohub.marketdata.config;

import java.net.http.HttpClient;
import java.time.Clock;
import com.cryptoportfoliohub.provider.http.BoundedProviderRetryInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class MarketDataHttpConfiguration {

    @Bean
    Clock marketDataClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient marketDataRestClient(MarketDataProperties properties,
            BoundedProviderRetryInterceptor retryInterceptor) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).requestInterceptor(retryInterceptor).build();
    }
}
