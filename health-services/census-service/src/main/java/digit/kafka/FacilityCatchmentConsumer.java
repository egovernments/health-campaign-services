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
import org.springframework.util.ObjectUtils;

import java.util.*;

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
            // Convert consumer record to DTO
            PlanFacilityRequestDTO planFacilityRequestDTO = objectMapper.convertValue(consumerRecord, PlanFacilityRequestDTO.class);
            PlanFacilityDTO planFacilityDTO = planFacilityRequestDTO.getPlanFacilityDTO();

            // Extract current and initial service boundaries
            List<String> initialServiceBoundaries = ObjectUtils.isEmpty(planFacilityDTO.getInitiallySetServiceBoundaries()) ?
                    Collections.emptyList() : planFacilityDTO.getInitiallySetServiceBoundaries();
            
            List<String> currentServiceBoundaries = ObjectUtils.isEmpty(planFacilityDTO.getServiceBoundaries()) ?
                    Collections.emptyList() : List.of(planFacilityDTO.getServiceBoundaries().split(COMMA_DELIMITER));

            // Determine boundaries that require census record updates
            Set<String> boundariesToBeSearched = initialServiceBoundaries.size() > currentServiceBoundaries.size()
                    ? commonUtil.getUniqueElements(initialServiceBoundaries, currentServiceBoundaries)
                    : commonUtil.getUniqueElements(currentServiceBoundaries, initialServiceBoundaries);

            // Fetch existing census records for the identified boundaries
            CensusResponse censusResponse = service.search(commonUtil.getCensusSearchRequest(planFacilityDTO.getTenantId(), planFacilityDTO.getPlanConfigurationId(), boundariesToBeSearched, planFacilityRequestDTO.getRequestInfo()));
            List<Census> censusFromSearch = censusResponse.getCensus();

            // Fetch boundary type hierarchy for enriching jurisdiction mapping
            BoundaryTypeHierarchyResponse boundaryTypeHierarchyResponse = boundaryUtil.fetchBoundaryHierarchy(planFacilityRequestDTO.getRequestInfo(), censusFromSearch.get(0).getTenantId(), censusFromSearch.get(0).getHierarchyType());

            // Extract facility details from the request
            String facilityId = planFacilityRequestDTO.getPlanFacilityDTO().getFacilityId();
            String facilityName = planFacilityRequestDTO.getPlanFacilityDTO().getFacilityName();

            // Determine whether to assign or unassign facilities based on initial and current service boundaries.
            List<Census> updatedCensusRecords = initialServiceBoundaries.size() > currentServiceBoundaries.size()
                    ? unassignFacilities(censusFromSearch)
                    : assignFacilities(censusFromSearch, facilityId, facilityName);

            // Enrich jurisdiction mapping in census for indexer
            enrichment.enrichJurisdictionMapping(updatedCensusRecords, boundaryTypeHierarchyResponse.getBoundaryHierarchy().get(0));
            repository.bulkUpdate(BulkCensusRequest.builder().requestInfo(planFacilityRequestDTO.getRequestInfo()).census(updatedCensusRecords).build());

        } catch (Exception exception) {
            log.error("Error in census consumer", exception);
        }
    }

    /**
     * Unassigns facilities from census records by removing facility-related fields.
     *
     * @param censusList List of census records to be updated.
     * @return Updated list of census records with facility fields removed.
     */
    private List<Census> unassignFacilities(List<Census> censusList) {
        censusList.forEach(census -> {

            // Unassigning facilities to the boundaries which were initially assigned that facility
            census.setAdditionalDetails(commonUtil.removeFieldFromAdditionalDetails(census.getAdditionalDetails(), FACILITY_ID_FIELD));
            census.setAdditionalDetails(commonUtil.removeFieldFromAdditionalDetails(census.getAdditionalDetails(), FACILITY_NAME_FIELD));
            census.setFacilityAssigned(Boolean.FALSE);
            census.setPartnerAssignmentValidationEnabled(Boolean.FALSE);
        });
        return censusList;
    }

    /**
     * Assigns facilities to census records by adding facility-related fields.
     *
     * @param censusList  List of census records to be updated.
     * @param facilityId  ID of the facility to be assigned.
     * @param facilityName Name of the facility to be assigned.
     * @return Updated list of census records with facility details added.
     */
    private List<Census> assignFacilities(List<Census> censusList, String facilityId, String facilityName) {
        censusList.forEach(census -> {

            // Assigning facilities to the newly added boundaries in the update request.
            census.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(census.getAdditionalDetails(), FACILITY_ID_FIELD, facilityId));
            census.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(census.getAdditionalDetails(), FACILITY_NAME_FIELD, facilityName));
            census.setFacilityAssigned(Boolean.TRUE);
            census.setPartnerAssignmentValidationEnabled(Boolean.FALSE);
        });
        return censusList;
    }
}
