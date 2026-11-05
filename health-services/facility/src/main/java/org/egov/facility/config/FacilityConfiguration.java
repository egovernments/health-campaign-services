package org.egov.facility.config;

import java.util.List;

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

    @Value("${egov.boundary.hierarchy.search.url:/boundary-service/boundary-hierarchy-definition/_search}")
    private String boundaryHierarchySearchUrl;

    // includeDescendants without a hierarchyType: look the boundary's hierarchy up when boundary-service cannot place it without one
    @Value("${facility.search.descendants.find.hierarchy:true}")
    private Boolean findHierarchyForDescendants;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.v2.search.path}")
    private String mdmsV2SearchPath;

    // Site types used to hide sites when MDMS cannot be read and no list has been read yet
    @Value("${egov.outside.household.fallback.types:OUTSIDE_HOUSEHOLD_PLACES,TRANSIT_POST}")
    private List<String> outsideHouseholdFallbackTypes;


    // Sites (usage = a site type) are checked like the Excel upload: name length and category of the type
    @Value("${facility.validate.outside.household.sites:true}")
    private Boolean validateOutsideHouseholdSites;

    @Value("${facility.outside.household.site.name.min.length:2}")
    private int outsideHouseholdSiteNameMinLength;

    @Value("${facility.outside.household.site.name.max.length:200}")
    private int outsideHouseholdSiteNameMaxLength;

    @Value("${facility.create.return.existing.on.duplicate.client.reference.id:true}")
    private Boolean returnExistingOnDuplicateClientReferenceId;
}
