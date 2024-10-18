package digit.service.enrichment;

import digit.web.models.Census;
import digit.web.models.CensusRequest;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class CensusEnrichment {

    public CensusEnrichment() {
    }

    /**
     * Enriches the CensusRequest for creating a new census record.
     * Enriches the given census record with generated IDs for Census and PopulationByDemographics.
     * Validates user information and enriches audit details for create operation.
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

        census.setAuditDetails(prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.TRUE));
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
