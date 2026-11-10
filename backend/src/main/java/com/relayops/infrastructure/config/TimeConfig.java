package com.relayops.infrastructure.config;

import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    BusinessTime businessTime(@Value("${relay-ops.timezone:Asia/Shanghai}") String timezone) {
        return new BusinessTime(Clock.system(ZoneId.of(timezone)));
    }
}
