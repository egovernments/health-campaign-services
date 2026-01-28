package digit.service.enrichment;

import digit.repository.CensusRepository;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.CensusSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

@Component
public class CensusTimeframeEnrichment {

    private CensusRepository repository;

    public CensusTimeframeEnrichment(CensusRepository repository) {
        this.repository = repository;
    }

    /**
     * Enriches the effectiveTo of previous census records for the same boundary.
     *
     * @param request The census request.
     */
    public void enrichPreviousTimeframe(CensusRequest request) {
        Census census = request.getCensus();
        List<Census> censusList = repository.search(CensusSearchCriteria.builder().tenantId(census.getTenantId()).areaCodes(Collections.singletonList(census.getBoundaryCode())).effectiveTo(0L).build());

        if (!CollectionUtils.isEmpty(censusList)) {
            censusList.forEach(censusData -> {
                censusData.setEffectiveTo(census.getAuditDetails().getCreatedTime());
                repository.update(CensusRequest.builder()
                        .requestInfo(request.getRequestInfo())
                        .census(censusData)
                        .build());
            });
        }
    }
}
