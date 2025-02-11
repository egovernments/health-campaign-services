package digit.service;

import digit.util.CommonUtil;
import digit.web.models.*;
import digit.web.models.boundary.BoundaryTypeHierarchy;
import digit.web.models.boundary.BoundaryTypeHierarchyDefinition;
import digit.web.models.boundary.EnrichedBoundary;
import digit.web.models.boundary.HierarchyRelation;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static digit.config.ServiceConstants.*;

@Component
public class PlanEnricher {

    private CommonUtil commonUtil;

    public PlanEnricher(CommonUtil commonUtil) {
        this.commonUtil = commonUtil;
    }

    /**
     * Enriches the plan create request
     * @param body
     */
    public void enrichPlanCreate(PlanRequest body) {
        // Generate id for plan
        UUIDEnrichmentUtil.enrichRandomUuid(body.getPlan(), ID);

        // Generate id for activities
        body.getPlan().getActivities().forEach(activity -> UUIDEnrichmentUtil.enrichRandomUuid(activity, ID));

        // Generate id for activity conditions
        body.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getConditions())) {
                UUIDEnrichmentUtil.enrichRandomUuid(activity.getConditions(), ID);
            }
        });

        // Set empty value in dependencies list when it is empty or null
        body.getPlan().getActivities().forEach(activity -> {
            if(CollectionUtils.isEmpty(activity.getDependencies())) {
                List<String> emptyStringList = new ArrayList<>();
                emptyStringList.add("");
                activity.setDependencies(emptyStringList);
            }
        });

        // Generate id for resources
        body.getPlan().getResources().forEach(resource -> UUIDEnrichmentUtil.enrichRandomUuid(resource, ID));

        // Generate id for targets
        body.getPlan().getTargets().forEach(target -> UUIDEnrichmentUtil.enrichRandomUuid(target, ID));

        // Generate id for additional fields
        if(!CollectionUtils.isEmpty(body.getPlan().getAdditionalFields()))
            body.getPlan().getAdditionalFields().forEach(additionalField -> UUIDEnrichmentUtil.enrichRandomUuid(additionalField, ID));

        // Enrich audit details
        body.getPlan().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(body.getPlan().getAuditDetails(), body.getRequestInfo(), Boolean.TRUE));

    }

    /**
     * Enriches the plan update request
     * @param body
     */
    public void enrichPlanUpdate(PlanRequest body) {
        // Generate uuid for new activities
        Set<String> newActivityUuids = new HashSet<>();
        body.getPlan().getActivities().forEach(activity -> {
            if(ObjectUtils.isEmpty(activity.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(activity, ID);
                newActivityUuids.add(activity.getId());
            }
        });

        // Generate uuid for new activity conditions
        body.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getConditions()) && newActivityUuids.contains(activity.getId())) {
                activity.getConditions().forEach(condition -> {
                    if(ObjectUtils.isEmpty(condition.getId())) {
                        UUIDEnrichmentUtil.enrichRandomUuid(condition, ID);
                    }
                });
            }
        });

        // Set empty value in dependencies list when it is empty or null
        body.getPlan().getActivities().forEach(activity -> {
            if(CollectionUtils.isEmpty(activity.getDependencies())) {
                List<String> emptyStringList = new ArrayList<>();
                emptyStringList.add("");
                activity.setDependencies(emptyStringList);
            }
        });

        // Generate uuid for new resources
        body.getPlan().getResources().forEach(resource -> {
            if(ObjectUtils.isEmpty(resource.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(resource, ID);
            }
        });

        // Generate uuid for new targets
        body.getPlan().getTargets().forEach(target -> {
            if(ObjectUtils.isEmpty(target.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(target, ID);
            }
        });

        // Generate uuid for new additionalFields
        if(!CollectionUtils.isEmpty(body.getPlan().getAdditionalFields())) {
            body.getPlan().getAdditionalFields().forEach(additionalFields -> {
                if(ObjectUtils.isEmpty(additionalFields.getId())) {
                    UUIDEnrichmentUtil.enrichRandomUuid(additionalFields, ID);
                }
            });
        }

        // Enriching last modified time for update
        body.getPlan().getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
    }

    /**
     * Enriches the boundary ancestral path and jurisdiction mapping for the provided boundary code in the plan request.
     *
     * @param plan         The plan record whose boundary ancestral path has to be enriched.
     * @param tenantBoundary boundary relationship from the boundary service for the given boundary code.
     */
    public void enrichBoundaryAncestralPath(Plan plan, HierarchyRelation tenantBoundary) {
        EnrichedBoundary boundary = tenantBoundary.getBoundary().get(0);
        Map<String, String> jurisdictionMapping = new LinkedHashMap<>();

        StringBuilder boundaryAncestralPath = new StringBuilder(boundary.getCode());
        jurisdictionMapping.put(boundary.getBoundaryType(), boundary.getCode());

        // Iterate through the child boundary until there are no more
        while (!CollectionUtils.isEmpty(boundary.getChildren())) {
            boundary = boundary.getChildren().get(0);
            boundaryAncestralPath.append(PIPE_SEPARATOR).append(boundary.getCode());
            jurisdictionMapping.put(boundary.getBoundaryType(), boundary.getCode());
        }

        // Setting the boundary ancestral path for the provided boundary
        plan.setBoundaryAncestralPath(boundaryAncestralPath.toString());

        // Setting jurisdiction mapping for the provided boundary
        plan.setJurisdictionMapping(jurisdictionMapping);
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
     * Enriches jurisdiction mapping in plan for the given boundary ancestral path.
     *
     * @param plan                     plan with boundary ancestral path.
     * @param boundaryTypeHierarchyDef boundary hierarchy for the given hierarchy type.
     */
    public void enrichJurisdictionMapping(Plan plan, BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDef) {
        Map<String, String> boundaryHierarchyMapping = new HashMap<>();

        // Enriches the boundaryHierarchyMapping and returns the highest boundary hierarchy for the given hierarchy type.
        String highestBoundaryHierarchy = getBoundaryHierarchyMapping(boundaryTypeHierarchyDef, boundaryHierarchyMapping);

        Map<String, String> jurisdictionMapping = new LinkedHashMap<>();
        String boundaryHierarchy = highestBoundaryHierarchy;

        // Get the list of boundary codes from pipe separated boundaryAncestralPath.
        List<String> boundaryCode = commonUtil.getBoundaryCodeFromAncestralPath(plan.getBoundaryAncestralPath());

        // Creates the mapping of boundary hierarchy with the corresponding boundary code.
        for (String boundary : boundaryCode) {
            jurisdictionMapping.put(boundaryHierarchy, boundary);
            boundaryHierarchy = boundaryHierarchyMapping.get(boundaryHierarchy);
        }

        plan.setJurisdictionMapping(jurisdictionMapping);
    }

    /**
     * Enriches jurisdiction mapping for the list of plans for the given boundary ancestral path.
     *
     * @param planList                 list of plans with boundary ancestral paths.
     * @param boundaryTypeHierarchyDef boundary hierarchy for the given hierarchy type.
     */
    public void enrichJurisdictionMapping(List<Plan> planList, BoundaryTypeHierarchyDefinition boundaryTypeHierarchyDef) {
        Map<String, String> boundaryHierarchyMapping = new HashMap<>();

        // Enriches the boundaryHierarchyMapping and returns the highest boundary hierarchy for the given hierarchy type.
        String highestBoundaryHierarchy = getBoundaryHierarchyMapping(boundaryTypeHierarchyDef, boundaryHierarchyMapping);

        for (Plan plan : planList) {

            Map<String, String> jurisdictionMapping = new LinkedHashMap<>();
            String boundaryHierarchy = highestBoundaryHierarchy;

            // Get the list of boundary codes from pipe separated boundaryAncestralPath.
            List<String> boundaryCode = commonUtil.getBoundaryCodeFromAncestralPath(plan.getBoundaryAncestralPath());

            // Creates the mapping of boundary hierarchy with the corresponding boundary code.
            for (String boundary : boundaryCode) {
                jurisdictionMapping.put(boundaryHierarchy, boundary);
                boundaryHierarchy = boundaryHierarchyMapping.get(boundaryHierarchy);
            }

            plan.setJurisdictionMapping(jurisdictionMapping);
        }
    }


    /**
     * Enriches the PlanSearchRequest by populating the filters map from the fields in search criteria.
     * This filterMap is populated to search the fields in plan additional detail object.
     *
     * @param planSearchRequest the planSearchRequest object whose search criteria need enrichment.
     */
    public void enrichSearchRequest(PlanSearchRequest planSearchRequest) {
        PlanSearchCriteria planSearchCriteria = planSearchRequest.getPlanSearchCriteria();

        // Filter map for filtering plan metadata present in additional details
        Map<String, Set<String>> filtersMap = new LinkedHashMap<>();

        // Add facility id as a filter if present in search criteria
        if (!CollectionUtils.isEmpty(planSearchCriteria.getFacilityIds())) {
            filtersMap.put(FACILITY_ID_SEARCH_PARAMETER_KEY, planSearchCriteria.getFacilityIds());
        }

        // Add terrain as a filter if present in search criteria
        if (!ObjectUtils.isEmpty(planSearchCriteria.getTerrain())) {
            filtersMap.put(TERRAIN_CONDITION_SEARCH_PARAMETER_KEY, Collections.singleton(planSearchCriteria.getTerrain()));
        }

        // Add onRoadCondition as a filter if present in search criteria
        if (!ObjectUtils.isEmpty(planSearchCriteria.getOnRoadCondition())) {
            filtersMap.put(ROAD_CONDITION_SEARCH_PARAMETER_KEY, Collections.singleton(planSearchCriteria.getOnRoadCondition()));
        }

        // Add securityQ1 as a filter if present in search criteria
        if (!ObjectUtils.isEmpty(planSearchCriteria.getSecurityQ1())) {
            filtersMap.put(SECURITY_Q1_SEARCH_PARAMETER_KEY, Collections.singleton(planSearchCriteria.getSecurityQ1()));
        }

        // Add securityQ2 as a filter if present in search criteria
        if (!ObjectUtils.isEmpty(planSearchCriteria.getSecurityQ2())) {
            filtersMap.put(SECURITY_Q2_SEARCH_PARAMETER_KEY, Collections.singleton(planSearchCriteria.getSecurityQ2()));
        }

        if(!CollectionUtils.isEmpty(filtersMap))
            planSearchCriteria.setFiltersMap(filtersMap);
    }
}
