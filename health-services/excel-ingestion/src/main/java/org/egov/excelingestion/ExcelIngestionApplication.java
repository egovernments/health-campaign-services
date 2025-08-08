package org.egov.excelingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"org.egov.excelingestion", "org.egov.common.http.client"})
public class ExcelIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelIngestionApplication.class, args);
    }

}
