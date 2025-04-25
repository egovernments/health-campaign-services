package org.egov.facility.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class FacilityConfiguration {
    @Value("${facility.kafka.create.topic}")
    private String createFacilityTopic;

    @Value("${facility.producer.bulk.create.topic}")
    private String bulkCreateFacilityTopic;

    @Value("${facility.kafka.update.topic}")
    private String updateFacilityTopic;

    @Value("${facility.producer.bulk.update.topic}")
    private String bulkUpdateFacilityTopic;

    @Value("${facility.kafka.delete.topic}")
    private String deleteFacilityTopic;

    @Value("${facility.producer.bulk.delete.topic}")
    private String bulkDeleteFacilityTopic;

    @Value("${facility.idgen.id.format}")
    private String facilityIdFormat;

    @Value("${egov.boundary.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;
}
