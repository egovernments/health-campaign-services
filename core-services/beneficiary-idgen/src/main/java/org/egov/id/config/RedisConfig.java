package org.egov.id.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for setting up Redisson client.
 * Binds Redisson properties from application config using RedissonProperties.
 */
@Configuration
@EnableConfigurationProperties(RedissonProperties.class)
public class RedisConfig {

    // Injects Redisson-specific properties defined in application.yaml or properties file
    @Autowired
    private RedissonProperties props;

    /**
     * Creates and configures a RedissonClient bean for Redis interaction.
     * This bean uses single server configuration and sets up thread and connection settings.
     *
     * @return RedissonClient instance configured with provided Redis properties
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Set thread configurations for Redis operations
        config.setThreads(props.getThreads());
        config.setNettyThreads(props.getNettyThreads());

        // Set up single-server Redis connection with pool and timeout settings
        config.useSingleServer()
                .setAddress("redis://" + props.getHost() + ":" + props.getPort())
                .setTimeout(props.getTimeout())
                .setConnectionPoolSize(props.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(props.getConnectionMinimumIdleSize());

        // Return the fully configured Redisson client
        return Redisson.create(config);
    }
}
