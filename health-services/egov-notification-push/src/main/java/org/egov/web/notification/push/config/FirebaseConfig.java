package org.egov.web.notification.push.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    @Autowired
    private PushProperties pushProperties;

    @Bean
    public FcmClientRegistry fcmClientRegistry(ObjectMapper objectMapper) {
        System.setProperty("http.maxConnections", String.valueOf(pushProperties.getFcmHttpMaxConnections()));
        return new FcmClientRegistry(pushProperties.getFcmServiceAccountKeyJson(),
                pushProperties.getFcmTenantProjectMapJson(), objectMapper);
    }

}
