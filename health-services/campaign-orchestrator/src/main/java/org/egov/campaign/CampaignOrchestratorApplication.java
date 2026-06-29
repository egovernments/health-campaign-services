package org.egov.campaign;

import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;


@Import({TracerConfiguration.class, MultiStateInstanceUtil.class})
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class CampaignOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignOrchestratorApplication.class, args);
    }
}
