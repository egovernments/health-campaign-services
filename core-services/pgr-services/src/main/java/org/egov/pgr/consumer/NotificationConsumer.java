
package org.egov.pgr.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.pgr.service.NotificationService;
import org.egov.pgr.web.models.ServiceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@Slf4j
public class NotificationConsumer {
    @Autowired
    NotificationService notificationService;

    @Autowired
    private ObjectMapper mapper;


    /**
     * Listens to Kafka messages from the specified topic patterns, processes the incoming message,
     * and triggers notification handling based on the message content.
     *
     * @param record The Kafka record received, represented as a HashMap containing message data.
     * @param topic  The topic from which the record is received, extracted from Kafka headers.
     */
    @KafkaListener(topicPattern = "(${pgr.kafka.tenant.id.pattern}){0,1}(${pgr.kafka.create.topic}|${pgr.kafka.update.topic}){1}")
    public void listen(final HashMap<String, Object> record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ServiceRequest request = mapper.convertValue(record, ServiceRequest.class);

            notificationService.process(request, topic);
        } catch (Exception ex) {
            StringBuilder builder = new StringBuilder("Error while listening to value: ").append(record)
                    .append("on topic: ").append(topic);
            log.error(builder.toString(), ex);
        }
    }
}

