package org.egov.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class KafkaEnvironmentConfig {

    @Value("${is.environment.central.instance}")
    private boolean centralInstance = false;
}
