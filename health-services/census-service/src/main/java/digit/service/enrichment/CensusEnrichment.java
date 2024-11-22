package digit.service.enrichment;

import digit.util.CommonUtil;
import digit.web.models.AdditionalField;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.boundary.BoundaryTypeHierarchy;
import digit.web.models.boundary.BoundaryTypeHierarchyDefinition;
import digit.web.models.boundary.EnrichedBoundary;
import digit.web.models.boundary.HierarchyRelation;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class CensusEnrichment {

    private CommonUtil commonUtil;

    public CensusEnrichment(CommonUtil commonUtil) {
        this.commonUtil = commonUtil;
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

        // Generate id for additionalFields
        census.getAdditionalFields().forEach(additionalField -> UUIDEnrichmentUtil.enrichRandomUuid(additionalField, "id"));

        // Set audit details for census record
        census.setAuditDetails(prepareAuditDetails(census.getAuditDetails(), request.getRequestInfo(), Boolean.TRUE));

        // Enrich effectiveFrom for the census record
        census.setEffectiveFrom(census.getAuditDetails().getCreatedTime());

        denormalizeAdditionalFields(request.getCensus());
    }

    private void denormalizeAdditionalFields(Census census) {
        Map<String, Object> fieldsToAdd = census.getAdditionalFields().stream()
                .collect(Collectors.toMap(AdditionalField::getKey, AdditionalField::getValue));

        census.setAdditionalDetails(commonUtil.updateFieldInAdditionalDetails(census.getAdditionalDetails(), fieldsToAdd));
    }

    /**
     * Enriches the boundary ancestral path and jurisdiction mapping for the provided boundary code in the census request.
     *
     * @param census         The census record whose boundary ancestral path has to be enriched.
     * @param tenantBoundary boundary relationship from the boundary service for the given boundary code.
     */
    public void enrichBoundaryAncestralPath(Census census, HierarchyRelation tenantBoundary) {
        EnrichedBoundary boundary = tenantBoundary.getBoundary().get(0);
        Map<String, String> jurisdictionMapping = new LinkedHashMap<>();

        StringBuilder boundaryAncestralPath = new StringBuilder(boundary.getCode());
        jurisdictionMapping.put(boundary.getBoundaryType(), boundary.getCode());

        // Iterate through the child boundary until there are no more
        while (!CollectionUtils.isEmpty(boundary.getChildren())) {
            boundary = boundary.getChildren().get(0);
            boundaryAncestralPath.append("|").append(boundary.getCode());
            jurisdictionMapping.put(boundary.getBoundaryType(), boundary.getCode());
        }

        // Setting the boundary ancestral path for the provided boundary
        census.setBoundaryAncestralPath(Collections.singletonList(boundaryAncestralPath.toString()));

        // Setting jurisdiction mapping for the provided boundary
        census.setJurisdictionMapping(jurisdictionMapping);
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

        //Generate id for additionalFields
        census.getAdditionalFields().forEach(additionalField -> {
            if (ObjectUtils.isEmpty(additionalField.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(additionalField, "id");
            }
        });

        denormalizeAdditionalFields(request.getCensus());
    }

    /**
     * Helper method to enrich boundary hierarchy mapping.
     * Creates a mapping of parentBoundaryType to childBoundaryType from the boundaryTypeHierarchy search response.
     *
     * @param boundaryTypeHierarchyDef Search response from boundary hierarchy search.
     * @param boundaryHierarchyMapping boundary hierarchy map to be enriched.
     * @return returns the highest boundary hierarchy for the given hierarchy type.
     */
    private String getBoundaryHierarchyMapping(BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDef, Map<String, String> boundaryHierarchyMapping) {
        String highestBoundaryHierarchy = null;

        for (BoundaryTypeHierarchy boundaryTypeHierarchy : boundaryTypeHierarchyDef.getBoundaryHierarchy()) {
            if (ObjectUtils.isEmpty(boundaryTypeHierarchy.getParentBoundaryType()))
                highestBoundaryHierarchy = boundaryTypeHierarchy.getBoundaryType();
            else
                boundaryHierarchyMapping.put(boundaryTypeHierarchy.getParentBoundaryType(), boundaryTypeHierarchy.getBoundaryType());
        }

        return highestBoundaryHierarchy;
    }

    /**
     * Enriches jurisdiction mapping in census for the given boundary ancestral path.
     *
     * @param census                   census data with boundary ancestral path.
     * @param boundaryTypeHierarchyDef boundary hierarchy for the given hierarchy type.
     */
    public void enrichJurisdictionMapping(Census census, BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDef) {
        Map<String, String> boundaryHierarchyMapping = new HashMap<>();

        // Enriches the boundaryHierarchyMapping and returns the highest boundary hierarchy for the given hierarchy type.
        String highestBoundaryHierarchy = getBoundaryHierarchyMapping(boundaryTypeHierarchyDef, boundaryHierarchyMapping);

        Map<String, String> jurisdictionMapping = new LinkedHashMap<>();
        String boundaryHierarchy = highestBoundaryHierarchy;

        // Get the list of boundary codes from pipe separated boundaryAncestralPath.
        List<String> boundaryCode = getBoundaryCodeFromAncestralPath(census.getBoundaryAncestralPath());

        // Creates the mapping of boundary hierarchy with the corresponding boundary code.
        for (String boundary : boundaryCode) {
            jurisdictionMapping.put(boundaryHierarchy, boundary);
            boundaryHierarchy = boundaryHierarchyMapping.get(boundaryHierarchy);
        }

        census.setJurisdictionMapping(jurisdictionMapping);
    }

    /**
     * Enriches jurisdiction mapping for the list of census records for the given boundary ancestral path.
     *
     * @param censusList               list of census data with boundary ancestral paths.
     * @param boundaryTypeHierarchyDef boundary hierarchy for the given hierarchy type.
     */
    public void enrichJurisdictionMapping(List<Census> censusList, BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDef) {
        Map<String, String> boundaryHierarchyMapping = new HashMap<>();

        // Enriches the boundaryHierarchyMapping and returns the highest boundary hierarchy for the given hierarchy type.
        String highestBoundaryHierarchy = getBoundaryHierarchyMapping(boundaryTypeHierarchyDef, boundaryHierarchyMapping);

        for (Census census : censusList) {

            Map<String, String> jurisdictionMapping = new LinkedHashMap<>();
            String boundaryHierarchy = highestBoundaryHierarchy;

            // Get the list of boundary codes from pipe separated boundaryAncestralPath.
            List<String> boundaryCode = getBoundaryCodeFromAncestralPath(census.getBoundaryAncestralPath());

            // Creates the mapping of boundary hierarchy with the corresponding boundary code.
            for (String boundary : boundaryCode) {
                jurisdictionMapping.put(boundaryHierarchy, boundary);
                boundaryHierarchy = boundaryHierarchyMapping.get(boundaryHierarchy);
            }

            census.setJurisdictionMapping(jurisdictionMapping);
        }
    }

    /**
     * Converts the boundaryAncestral path from a pipe separated string to an array of boundary codes.
     *
     * @param boundaryAncestralPath pipe separated boundaryAncestralPath.
     * @return a list of boundary codes.
     */
    private List<String> getBoundaryCodeFromAncestralPath(List<String> boundaryAncestralPath) {
        if (CollectionUtils.isEmpty(boundaryAncestralPath)) {
            return Collections.emptyList();
        }
        return Arrays.asList(boundaryAncestralPath.get(0).split("\\|"));
    }
}
