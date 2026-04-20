package org.egov.transformer.aggregator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.aggregator.service.HouseholdMemberService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HouseholdMemberConsumer {

  private final ObjectMapper objectMapper;
  private final HouseholdMemberService householdMemberService;

  public HouseholdMemberConsumer(@Qualifier("customObjectMapper") ObjectMapper objectMapper,
      HouseholdMemberService householdMemberService) {
    this.objectMapper = objectMapper;
    this.householdMemberService = householdMemberService;
  }

  @KafkaListener(topics = {"${aggregator.consumer.save.household.member.topic}",
      "${aggregator.consumer.update.household.member.topic}",})
  public void consumeHouseholdMember(ConsumerRecord<String, Object> payload,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    try {
      List<HouseholdMember> householdMembers = Arrays.asList(
          objectMapper.readValue((String) payload.value(), HouseholdMember[].class));
      householdMemberService.processHouseholdMembers(householdMembers);
    } catch (Exception exception) {
      log.error("Aggregator error in householdMemberConsumer {}",
          ExceptionUtils.getStackTrace(exception));
    }
  }

}