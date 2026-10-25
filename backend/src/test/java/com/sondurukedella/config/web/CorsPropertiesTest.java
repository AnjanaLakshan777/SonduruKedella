package com.sondurukedella.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void exactOriginsAreAccepted() {
        assertThat(new CorsProperties(List.of(" http://localhost:5173 ", "https://app.sondurukedella.lk", "")).allowedOrigins())
                .containsExactly("http://localhost:5173", "https://app.sondurukedella.lk");
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }

    @Test
    void wildcardsAndNonOriginsAreRefused() {
        for (String bad : List.of("*", "https://*.example.com", "localhost:5173", "http://localhost:5173/", "https://app.example.com/path",
                "ftp://example.com")) {
            assertThatThrownBy(() -> new CorsProperties(List.of(bad))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
