package com.reelcipe.common.http;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpConfiguration {

    @Bean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
