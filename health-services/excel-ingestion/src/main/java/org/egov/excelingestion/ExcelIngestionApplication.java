package org.egov.excelingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@ComponentScan(basePackages = {"org.egov"})
@Import({TracerConfiguration.class})
@EnableCaching
@EnableAsync
public class ExcelIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelIngestionApplication.class, args);
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("localizationMessages", "boundaryHierarchy", "boundaryRelationship", "campaignCache");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)  // Campaign cache expires in 15 minutes
                .maximumSize(100));
        return cacheManager;
    }

    @Bean(name = "taskExecutor")
    public java.util.concurrent.Executor taskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // minimum threads always alive
        executor.setMaxPoolSize(10);      // maximum threads allowed
        executor.setQueueCapacity(200);   // queue me 200 tasks wait kar sakte hain
        executor.setThreadNamePrefix("AsyncGeneration-");
        executor.initialize();
        return executor;
    }
}

