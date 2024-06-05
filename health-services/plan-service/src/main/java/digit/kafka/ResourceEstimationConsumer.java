package digit.kafka;

import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import digit.service.PlanService;
import digit.web.models.PlanRequest;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ResourceEstimationConsumer {

    private ObjectMapper objectMapper;
    
    private PlanService planService;
    
    public ResourceEstimationConsumer(ObjectMapper objectMapper, PlanService planService) {
        this.objectMapper = objectMapper;
        this.planService = planService;
    }
	
	@KafkaListener(topics = {"${resource.config.consumer.plan.create.topic}"})
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
        	PlanRequest planRequest = objectMapper.convertValue(consumerRecord, PlanRequest.class);
        	planService.createPlan(planRequest);
        } catch (Exception exception) {
            log.error("Error in plan consumer", exception);
        }
    }
}
