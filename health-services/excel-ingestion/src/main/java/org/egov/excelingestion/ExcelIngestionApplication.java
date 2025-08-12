package org.egov.excelingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
@ComponentScan(basePackages = {"org.egov.excelingestion", "org.egov.common.http.client"})
@EnableCaching
public class ExcelIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelIngestionApplication.class, args);
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("localizationMessages", "boundaryHierarchy", "boundaryRelationship");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100));
        return cacheManager;
    }
}

