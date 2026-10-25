package com.sondurukedella.config.web;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// security.cors.allowed-origins (env CORS_ALLOWED_ORIGINS, comma separated): the exact frontend
// origins allowed to call the API with the session cookie, e.g. http://localhost:5173. Empty means
// same-origin only. A wildcard is refused, because credentials are allowed.
@ConfigurationProperties("security.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
        allowedOrigins.forEach(CorsProperties::requireOrigin);
    }

    private static void requireOrigin(String origin) {
        if (origin.contains("*")) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must list exact origins; \"*\" is not allowed.");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS has an entry that is not an origin: " + origin);
        }
        boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean bare = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && uri.getRawQuery() == null
                && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!httpScheme || uri.getHost() == null || !bare) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS entries look like https://app.example.com "
                    + "(scheme, host and optional port; no path or trailing slash): " + origin);
        }
    }
}
