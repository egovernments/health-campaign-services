package org.egov.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ProductApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(ProductApplication.class, args);
    }
}
