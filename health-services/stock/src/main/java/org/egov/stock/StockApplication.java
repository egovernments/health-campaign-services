package org.egov.stock;

import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
@Import({ TracerConfiguration.class, MultiStateInstanceUtil.class})
@SpringBootApplication
@EnableCaching
public class StockApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(StockApplication.class, args);
    }
}
