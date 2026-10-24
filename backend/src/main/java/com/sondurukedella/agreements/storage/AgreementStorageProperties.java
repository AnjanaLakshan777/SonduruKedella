package com.sondurukedella.agreements.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

// agreements.storage-dir and agreements.max-size in application.properties. The directory must be
// private (never under a static/public folder) and, in any real deployment, on persistent storage.
@ConfigurationProperties("agreements")
public record AgreementStorageProperties(
        @DefaultValue("data/agreements") Path storageDir,
        @DefaultValue("20MB") DataSize maxSize) {
}
