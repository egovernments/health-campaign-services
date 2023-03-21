package org.egov.household.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.household.service.HouseholdMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
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
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
            return householdMemberService.create(request, true);
        } catch (Exception exception) {
            log.error("error in household member consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${household.member.consumer.bulk.update.topic}")
    public List<HouseholdMember> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
            return householdMemberService.update(request, true);
        } catch (Exception exception) {
            log.error("error in household member consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${household.member.consumer.bulk.delete.topic}")
    public List<HouseholdMember> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HouseholdMemberBulkRequest request = objectMapper.convertValue(consumerRecord, HouseholdMemberBulkRequest.class);
            return householdMemberService.delete(request, true);
        } catch (Exception exception) {
            log.error("error in household member consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
