package org.digit.health.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@EnableAsync
public class SyncServiceApp {
    public static void main(String[] args) {
        SpringApplication.run(SyncServiceApp.class, args);
    }
}
