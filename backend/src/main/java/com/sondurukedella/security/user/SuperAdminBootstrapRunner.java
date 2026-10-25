package com.sondurukedella.security.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// Runs the one-time Super Admin bootstrap at startup (after Flyway has migrated the schema).
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
class SuperAdminBootstrapRunner implements ApplicationRunner {

    private final SuperAdminProvisioner provisioner;
    private final SuperAdminBootstrapProperties settings;

    @Override
    public void run(ApplicationArguments args) {
        provisioner.provision(settings);
    }
}
