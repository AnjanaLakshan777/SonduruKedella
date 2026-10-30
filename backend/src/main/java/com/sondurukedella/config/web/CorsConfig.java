package com.sondurukedella.config.web;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Needed locally because Vite (:5173) calls the API on :8080 with cookies.
// No origins configured = no cross-origin calls at all.
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.allowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, "X-XSRF-TOKEN", "X-Requested-With"));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
