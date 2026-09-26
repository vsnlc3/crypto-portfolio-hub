package com.cryptoportfoliohub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@TestConfiguration(proxyBeanMethods = false)
@EnableMethodSecurity
class ErrorHandlingTestConfiguration {

    @Bean
    ErrorHandlingTestController errorHandlingTestController() {
        return new ErrorHandlingTestController();
    }
}
