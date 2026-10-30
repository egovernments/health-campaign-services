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

    @Value("${facility.consumer.bulk.create.topic}")
    private String bulkCreateFacilityTopic;

    @Value("${facility.kafka.update.topic}")
    private String updateFacilityTopic;

    @Value("${facility.consumer.bulk.update.topic}")
    private String bulkUpdateFacilityTopic;

    @Value("${facility.kafka.delete.topic}")
    private String deleteFacilityTopic;

    @Value("${facility.consumer.bulk.delete.topic}")
    private String bulkDeleteFacilityTopic;

    @Value("${facility.idgen.id.format}")
    private String facilityIdFormat;

    @Value("${egov.boundary.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Value("${egov.boundary.relationship.search.url:/boundary-service/boundary-relationships/_search}")
    private String boundaryRelationshipSearchUrl;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.v2.search.path}")
    private String mdmsV2SearchPath;

    @Value("${facility.create.return.existing.on.duplicate.client.reference.id:true}")
    private Boolean returnExistingOnDuplicateClientReferenceId;
}
