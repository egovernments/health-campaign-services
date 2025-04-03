package org.egov.id.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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