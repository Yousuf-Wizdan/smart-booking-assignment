package com.moveinsync.smart_booking_engine.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single injectable clock for all business-time decisions (cutoff, "now").
 * Tests inject a fixed clock instead of depending on wall-clock time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.company-time-zone:Asia/Kolkata}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }

    @Bean
    public ZoneId companyZone(@Value("${app.company-time-zone:Asia/Kolkata}") String timeZone) {
        return ZoneId.of(timeZone);
    }
}
