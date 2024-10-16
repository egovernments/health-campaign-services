package digit.service.enrichment;

import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.boundary.EnrichedBoundary;
import digit.web.models.boundary.HierarchyRelation;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.Collections;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class CensusEnrichment {

    public CensusEnrichment() {
    }

    /**
     * Enriches the CensusRequest for creating a new census record.
     * Enriches the given census record with generated IDs for Census and PopulationByDemographics.
     * Validates user information, enriches audit details and effectiveFrom for create operation.
     *
     * @param request The CensusRequest to be enriched.
     * @throws CustomException if user information is missing in the request.
     */
    public void enrichCreate(CensusRequest request) {
        Census census = request.getCensus();

        // Generate id for census record
        UUIDEnrichmentUtil.enrichRandomUuid(census, "id");

        // Generate id for PopulationByDemographics
        if (!CollectionUtils.isEmpty(census.getPopulationByDemographics())) {
            census.getPopulationByDemographics().forEach(populationByDemographics -> UUIDEnrichmentUtil.enrichRandomUuid(populationByDemographics, "id"));
        }

        // Set audit details for census record
        census.setAuditDetails(prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.TRUE));

        // Enrich effectiveFrom for the census record
        census.setEffectiveFrom(census.getAuditDetails().getCreatedTime());
    }

    /**
     * Enriches the boundary ancestral path for the provided boundary code in the census request.
     *
     * @param census         The census record whose boundary ancestral path has to be enriched.
     * @param tenantBoundary boundary relationship from the boundary service for the given boundary code.
     */
    public void enrichBoundaryAncestralPath(Census census, HierarchyRelation tenantBoundary) {
        EnrichedBoundary boundary = tenantBoundary.getBoundary().get(0);
        StringBuilder boundaryAncestralPath = new StringBuilder(boundary.getCode());

        // Iterate through the child boundary until there are no more
        while (!CollectionUtils.isEmpty(boundary.getChildren())) {
            boundary = boundary.getChildren().get(0);
            boundaryAncestralPath.append("|").append(boundary.getCode());
        }

        // Setting the boundary ancestral path for the provided boundary
        census.setBoundaryAncestralPath(Collections.singletonList(boundaryAncestralPath.toString()));
    }

    /**
     * Enriches the CensusRequest for updating an existing census record.
     * This method enriches the census record for update, validates user information and enriches audit details for update operation.
     *
     * @param request The CensusRequest to be enriched.
     * @throws CustomException if user information is missing in the request.
     */
    public void enrichUpdate(CensusRequest request) {
        Census census = request.getCensus();

        // Generate id for populationByDemographics
        if (!CollectionUtils.isEmpty(census.getPopulationByDemographics())) {
            census.getPopulationByDemographics().forEach(populationByDemographics -> {
                if (ObjectUtils.isEmpty(populationByDemographics.getId())) {
                    UUIDEnrichmentUtil.enrichRandomUuid(populationByDemographics, "id");
                }
            });
        }

        census.setAuditDetails(prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.FALSE));
    }

}
