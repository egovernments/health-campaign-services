package org.egov.product;

import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableCaching
@Import({TracerConfiguration.class , MultiStateInstanceUtil.class})
public class ProductApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(ProductApplication.class, args);
    }
}
