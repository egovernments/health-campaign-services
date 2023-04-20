package org.egov.web.notification.mail.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.web.notification.mail.consumer.contract.Email;
import org.egov.web.notification.mail.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@Slf4j
public class EmailNotificationListener {

    
    private EmailService emailService;
    
    private ObjectMapper objectMapper;

    @Value("${mail.sender.username}")
    private String userName;
    @Value("${mail.sender.password}")
    private String password;
    @Value("${mail.host}")
    private String smtpHost;
    @Value("${mail.port}")
    private String smtpPort;

    @Autowired
    public EmailNotificationListener(EmailService emailService, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.notification.mail.name}")
    public void listen(final HashMap<String, Object> record) {
    	Email email = objectMapper.convertValue(record, Email.class);
        emailService.sendEmail(email);
    }
}
