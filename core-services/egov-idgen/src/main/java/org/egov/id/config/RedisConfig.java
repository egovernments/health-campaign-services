package org.egov.id.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedissonProperties.class)
public class RedisConfig {

    @Autowired
    private RedissonProperties props;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setThreads(props.getThreads());
        config.setNettyThreads(props.getNettyThreads());

        config.useSingleServer()
                .setAddress("redis://" + props.getHost() + ":" + props.getPort())
                .setTimeout(props.getTimeout())
                .setConnectionPoolSize(props.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(props.getConnectionMinimumIdleSize());

        return Redisson.create(config);
    }
}
