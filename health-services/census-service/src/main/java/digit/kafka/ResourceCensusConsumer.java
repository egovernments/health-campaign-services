package digit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.service.CensusService;
import digit.web.models.CensusRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ResourceCensusConsumer {

    private CensusService censusService;

    private ObjectMapper mapper;

    public ResourceCensusConsumer(CensusService censusService, ObjectMapper mapper) {
        this.censusService = censusService;
        this.mapper = mapper;
    }

    @KafkaListener(topics = {"${resource.config.consumer.census.create.topic}"})
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            CensusRequest censusRequest = mapper.convertValue(consumerRecord, CensusRequest.class);
            censusService.create(censusRequest);
        } catch (Exception exception) {
            log.error("Error in resource census consumer", exception);
        }
    }
}
