
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
     * Kafka listener that consumes messages from create and update PGR topics,
     * optionally scoped by tenant ID when central instance is enabled
     * and triggers notification handling based on the message content.
     *
     * <p><b>Topic Pattern:</b>
     * <pre>{@code
     * (${pgr.kafka.tenant.id.pattern}){0,1}(${pgr.kafka.create.topic}|${pgr.kafka.update.topic}){1}
     * }</pre>
     * This pattern is a dynamic regular expression composed of the following parts:
     * <ul>
     *   <li><b>{@code ${pgr.kafka.tenant.id.pattern}}:</b> A regex pattern used to identify tenant-specific topics.
     *   This is useful when central instance support is enabled and topics are prefixed by tenant IDs.
     *   i.e. <b>{@code kano-|kebbi-}</b> to match prefixes for tenants kano and kebbi
     *   </li>
     *   <li><b>{@code ${pgr.kafka.create.topic}}:</b> The topic name for create PGR events.</li>
     *   <li><b>{@code ${pgr.kafka.update.topic}}:</b> The topic name for update PGR events.</li>
     * </ul>
     * <p>The pattern allows for optional tenant scoping (`{0,1}`) and requires one of the create or update topics (`{1}`).</p>
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

