package com.cryptoportfoliohub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CryptoPortfolioHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoPortfolioHubApplication.class, args);
    }
}
