package org.egov.household.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.household.service.HouseholdService;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdBulkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class HouseholdConsumer {

    private final HouseholdService householdService;

    private final ObjectMapper objectMapper;

    @Autowired
    public HouseholdConsumer(HouseholdService householdService,
                             @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.householdService = householdService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${household.consumer.bulk.create.topic}")
    public List<Household> bulkCreate(Map<String, Object> consumerRecord,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        HouseholdBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdBulkRequest.class);
        return householdService.create(request, true);
    }

    @KafkaListener(topics = "${household.consumer.bulk.update.topic}")
    public List<Household> bulkUpdate(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        HouseholdBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdBulkRequest.class);
        return householdService.update(request, true);
    }

    @KafkaListener(topics = "${household.consumer.bulk.delete.topic}")
    public List<Household> bulkDelete(Map<String, Object> consumerRecord,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        HouseholdBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdBulkRequest.class);
        return householdService.delete(request, true);
    }
}
