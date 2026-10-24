package com.sondurukedella.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // The business runs in Sri Lanka, so "today" (completion dates, activity dates) must be the
    // Colombo date, whatever timezone the server or container happens to use.
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
