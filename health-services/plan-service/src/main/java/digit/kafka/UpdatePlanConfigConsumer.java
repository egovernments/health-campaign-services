package digit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.service.PlanConfigurationService;
import digit.web.models.PlanConfigurationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class UpdatePlanConfigConsumer {

    private PlanConfigurationService planConfigurationService;

    private ObjectMapper objectMapper;

    public UpdatePlanConfigConsumer(PlanConfigurationService planConfigurationService, ObjectMapper objectMapper) {
        this.planConfigurationService = planConfigurationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"${resource.update.plan.config.consumer.topic}"})
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PlanConfigurationRequest planConfigurationRequest = objectMapper.convertValue(consumerRecord, PlanConfigurationRequest.class);
            log.info("Update plan config from resource generator.");
            planConfigurationService.update(planConfigurationRequest);
        } catch (Exception exception) {
            log.error("Error in update plan configuration consumer while processing topic {}: {}", topic, consumerRecord, exception);
        }
    }
}
