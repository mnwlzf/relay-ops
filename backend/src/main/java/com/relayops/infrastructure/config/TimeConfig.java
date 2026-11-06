package com.relayops.infrastructure.config;

import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    BusinessTime businessTime() {
        return new BusinessTime(Clock.system(BusinessTime.DEFAULT_ZONE));
    }
}
