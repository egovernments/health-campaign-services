package org.egov.transformer.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.ErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ErrorQueueProducer {

    private final Producer producer;

    private final TransformerProperties properties;

    @Autowired
    public ErrorQueueProducer(Producer producer, TransformerProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    public void sendToErrorTopic(Object payload, String sourceTopic, Exception exception) {
        try {
            ErrorMessage errorMessage = ErrorMessage.builder()
                    .topic(sourceTopic)
                    .payload(payload != null ? payload.toString() : null)
                    .errorMessage(exception.getMessage())
                    .stackTrace(ExceptionUtils.getStackTrace(exception))
                    .timestamp(System.currentTimeMillis())
                    .build();
            producer.push(properties.getTransformerErrorTopic(), errorMessage);
        } catch (Exception e) {
            log.error("TRANSFORMER failed to push error to error topic: {}", ExceptionUtils.getStackTrace(e));
        }
    }
}
