package com.example.shipping.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Restricts cross-origin access to the API so only the configured frontend
 * origins can call the service from a browser.
 *
 * <p>Default allowed origin: {@code http://localhost:5173} (Vite dev server).
 * Override with {@code cors.allowed-origins} in application.properties or
 * the environment.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebMvcConfig(
            @Value("${cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
