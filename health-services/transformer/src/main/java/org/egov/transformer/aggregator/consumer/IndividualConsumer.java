package org.egov.transformer.aggregator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.individual.Individual;
import org.egov.transformer.aggregator.service.IndividualService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IndividualConsumer {

  private final ObjectMapper objectMapper;
  private final IndividualService individualService;

  @Autowired
  public IndividualConsumer(@Qualifier("customObjectMapper") ObjectMapper objectMapper,
      IndividualService individualService) {
    this.objectMapper = objectMapper;

    this.individualService = individualService;
  }

  @KafkaListener(topics = {"${aggregator.consumer.create.individual.topic}",
      "${aggregator.consumer.update.individual.topic}"})
  public void consumeHouseholds(ConsumerRecord<String, Object> payload,
      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    try {
      List<Individual> individuals = Arrays.asList(objectMapper
          .readValue((String) payload.value(),
              Individual[].class));
      individualService.processIndividuals(individuals);
    } catch (Exception exception) {
      log.error("error in household consumer {}", ExceptionUtils.getStackTrace(exception));
    }
  }
}

