package com.cryptoportfoliohub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import com.cryptoportfoliohub.error.ProblemAccessDeniedHandler;
import com.cryptoportfoliohub.error.ProblemAuthenticationEntryPoint;
import com.cryptoportfoliohub.security.GoogleLoginUserProvisioner;
import com.cryptoportfoliohub.security.GoogleOidcUserService;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            GoogleOidcUserService googleOidcUserService,
            @Value("${app.auth.google.enabled:false}") boolean googleLoginEnabled,
            @Value("${app.auth.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) throws Exception {
        http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session
                        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId()))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));

        if (googleLoginEnabled) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService))
                    .defaultSuccessUrl(frontendBaseUrl, true)
                    .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                            frontendBaseUrl + "/signin?error=AUTHENTICATION_FAILED")));
        }

        return http.build();
    }

    @Bean
    GoogleOidcUserService googleOidcUserService(GoogleLoginUserProvisioner userProvisioner) {
        return new GoogleOidcUserService(new OidcUserService(), userProvisioner);
    }
}
