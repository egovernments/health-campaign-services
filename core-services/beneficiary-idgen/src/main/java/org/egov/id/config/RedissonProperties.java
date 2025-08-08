package org.egov.id.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redisson.redis")
@Getter
@Setter
public class RedissonProperties {
    private String host;
    private Integer port;
    private Integer timeout;
    private Integer connectionPoolSize;
    private Integer threads;
    private Integer nettyThreads;
    private Integer connectionMinimumIdleSize;
}