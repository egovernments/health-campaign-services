package org.egov.transformer.aggregator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Import({TracerConfiguration.class})
@ComponentScan(basePackages = {"org.egov"})
public class ServiceConfiguration {

  //ElasticSearch Config
  @Value("${egov.infra.indexer.host}")
  private String esHostUrl;

  @Value("${egov.indexer.es.username}")
  private String esUsername;

  @Value("${egov.indexer.es.password}")
  private String esPassword;

  @Value("${egov.search.index.path}")
  private String searchPath;

  @Value("${egov.update.index.path}")
  private String updatePath;

  @Value("${egov.doc.index.path}")
  private String docPath;

  @Value("${egov.aggregated-household.index}")
  private String aggregatedHouseholdIndex;

  @Value("${egov.household.index}")
  private String householdIndex;

  @Value("${egov.household-member.index}")
  private String householdMemberIndex;

  @Value("${egov.individual.index}")
  private String individualIndex;

  @Value("${egov.search.index.parameters}")
  private String searchParameter;

  @Bean("customObjectMapper")
  public ObjectMapper objectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    return objectMapper;
  }
}
