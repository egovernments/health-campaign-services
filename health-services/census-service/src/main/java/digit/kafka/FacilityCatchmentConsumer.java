package digit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.repository.CensusRepository;
import digit.service.CensusService;
import digit.service.enrichment.CensusEnrichment;
import digit.util.BoundaryUtil;
import digit.util.CommonUtil;
import digit.web.models.BulkCensusRequest;
import digit.web.models.Census;
import digit.web.models.CensusResponse;
import digit.web.models.boundary.BoundaryTypeHierarchyResponse;
import digit.web.models.plan.PlanFacilityDTO;
import digit.web.models.plan.PlanFacilityRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class FacilityCatchmentConsumer {

    private ObjectMapper objectMapper;

    private CensusService service;

    private CensusRepository repository;

    private CommonUtil commonUtil;

    private BoundaryUtil boundaryUtil;

    private CensusEnrichment enrichment;

    public FacilityCatchmentConsumer(ObjectMapper objectMapper, CensusService service, CommonUtil commonUtil, CensusRepository repository, BoundaryUtil boundaryUtil, CensusEnrichment enrichment) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.commonUtil = commonUtil;
        this.repository = repository;
        this.boundaryUtil = boundaryUtil;
        this.enrichment = enrichment;
    }

    @KafkaListener(topics = {"${plan.facility.update.topic}"})
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PlanFacilityRequestDTO planFacilityRequestDTO = objectMapper.convertValue(consumerRecord, PlanFacilityRequestDTO.class);
            PlanFacilityDTO planFacilityDTO = planFacilityRequestDTO.getPlanFacilityDTO();

            CensusResponse censusResponse = service.search(commonUtil.getCensusSearchRequest(planFacilityDTO.getTenantId(), planFacilityDTO.getPlanConfigurationId(), planFacilityDTO.getServiceBoundaries(), planFacilityDTO.getInitiallySetServiceBoundaries(), planFacilityRequestDTO.getRequestInfo()));
            List<Census> censusFromSearch = censusResponse.getCensus();

            BoundaryTypeHierarchyResponse boundaryTypeHierarchyResponse = boundaryUtil.fetchBoundaryHierarchy(planFacilityRequestDTO.getRequestInfo(), censusFromSearch.get(0).getTenantId(), censusFromSearch.get(0).getHierarchyType());
            String facilityId = planFacilityRequestDTO.getPlanFacilityDTO().getFacilityId();
            String facilityName = planFacilityRequestDTO.getPlanFacilityDTO().getFacilityName();

            Set<String> boundariesWithFacility = new HashSet<>(List.of(planFacilityDTO.getServiceBoundaries().split(COMMA_DELIMITER)));
            Set<String> boundariesWithNoFacility = new HashSet<>(planFacilityDTO.getInitiallySetServiceBoundaries());

            censusFromSearch.forEach(census -> {
                String boundaryCode = census.getBoundaryCode();

                if (!boundariesWithFacility.contains(boundaryCode)) {

                    // Unassigning facilities to the boundaries which were initially assigned that facility
                    census.setAdditionalDetails(commonUtil.removeFieldFromAdditionalDetails(census.getAdditionalDetails(), FACILITY_ID_FIELD));
                    census.setAdditionalDetails(commonUtil.removeFieldFromAdditionalDetails(census.getAdditionalDetails(), FACILITY_NAME_FIELD));
                    census.setFacilityAssigned(Boolean.FALSE);
                    census.setPartnerAssignmentValidationEnabled(Boolean.FALSE);

                } else if (!boundariesWithNoFacility.contains(boundaryCode)) {

                    // Assigning facilities to the newly added boundaries in the update request.
                    census.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(census.getAdditionalDetails(), FACILITY_ID_FIELD, facilityId));
                    census.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(census.getAdditionalDetails(), FACILITY_NAME_FIELD, facilityName));
                    census.setFacilityAssigned(Boolean.TRUE);
                    census.setPartnerAssignmentValidationEnabled(Boolean.FALSE);
                }
            });

            // Enrich jurisdiction mapping in census for indexer
            enrichment.enrichJurisdictionMapping(censusFromSearch, boundaryTypeHierarchyResponse.getBoundaryHierarchy().get(0));
            repository.bulkUpdate(BulkCensusRequest.builder().requestInfo(planFacilityRequestDTO.getRequestInfo()).census(censusFromSearch).build());

        } catch (Exception exception) {
            log.error("Error in census consumer", exception);
        }
    }
}
