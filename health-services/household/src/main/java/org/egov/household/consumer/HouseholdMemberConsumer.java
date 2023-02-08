package org.egov.household.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.household.service.HouseholdMemberService;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HouseholdMemberConsumer {

    private final HouseholdMemberService householdMemberService;

    private final ObjectMapper objectMapper;

    @Autowired
    public HouseholdMemberConsumer(HouseholdMemberService householdMemberService,
                                      @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.householdMemberService = householdMemberService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${household.member.consumer.bulk.create.topic}")
    public List<HouseholdMember> bulkCreate(Map<String, Object> consumerRecord,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
        return householdMemberService.create(request, true);
    }

    @KafkaListener(topics = "${household.member.consumer.bulk.update.topic}")
    public List<HouseholdMember> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
        return householdMemberService.update(request, true);
    }

    @KafkaListener(topics = "${household.member.consumer.bulk.delete.topic}")
    public List<HouseholdMember> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
        return householdMemberService.delete(request, true);
    }
}
