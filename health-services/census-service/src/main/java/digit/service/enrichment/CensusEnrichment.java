package digit.service.enrichment;

import digit.web.models.Census;
import digit.web.models.CensusRequest;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class CensusEnrichment {

    public CensusEnrichment() {
    }

    /**
     * Enriches the CensusRequest for creating a new census record.
     * Enriches the given census record with generated IDs for Census and PopulationByDemographics.
     * Enriches audit details for create operation.
     *
     * @param request The CensusRequest to be enriched.
     */
    public void enrichCreate(CensusRequest request) {
        Census census = request.getCensus();

        // Generate id for census record
        UUIDEnrichmentUtil.enrichRandomUuid(census, "id");

        // Generate id for PopulationByDemographics
        census.getPopulationByDemographics().forEach(populationByDemographics -> {
            UUIDEnrichmentUtil.enrichRandomUuid(populationByDemographics, "id");
        });

        census.setAuditDetails(prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.TRUE));
    }
}
