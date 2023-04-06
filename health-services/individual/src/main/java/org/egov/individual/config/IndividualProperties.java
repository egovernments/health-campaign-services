package org.egov.individual.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class IndividualProperties {

    @Value("${individual.producer.save.topic}")
    private String saveIndividualTopic;

    @Value("${individual.producer.update.topic}")
    private String updateIndividualTopic;

    @Value("${individual.producer.delete.topic}")
    private String deleteIndividualTopic;

    @Value("${individual.consumer.bulk.create.topic}")
    private String bulkSaveIndividualTopic;

    @Value("${individual.consumer.bulk.update.topic}")
    private String bulkUpdateIndividualTopic;

    @Value("${individual.consumer.bulk.delete.topic}")
    private String bulkDeleteIndividualTopic;

    @Value("${idgen.individual.id.format}")
    private String individualId;

    @Value("${aadhaar.pattern}")
    private String aadhaarPattern;

    @Value("${mobile.pattern}")
    private String mobilePattern;

    @Value(("${state.level.tenant.id}"))
    private String stateLevelTenantId;

}
