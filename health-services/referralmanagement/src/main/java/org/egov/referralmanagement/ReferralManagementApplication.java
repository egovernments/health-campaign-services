package org.egov.referralmanagement;


import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@Import({ TracerConfiguration.class })
public class ReferralManagementApplication
{
    public static void main(String[] args) throws Exception {
        SpringApplication.run(ReferralManagementApplication.class, args);
    }
}
