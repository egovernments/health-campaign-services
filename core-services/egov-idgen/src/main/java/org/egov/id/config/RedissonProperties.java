package org.egov.id.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redisson.redis")
@Getter
@Setter
public class RedissonProperties {
    private String host;
    private int port;
    private int timeout;
    private int connectionPoolSize;
    private int threads;
    private int nettyThreads;
    private int connectionMinimumIdleSize;
}