package digit.service.enrichment;

import digit.web.models.Plan;
import digit.web.models.PlanRequest;
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

@Component
public class PlanEnricher {

    /**
     * Enriches the plan create request
     * @param body
     */
    public void enrichPlanCreate(PlanRequest body) {
    	 if (body.getPlan() == null) {
             throw new IllegalArgumentException("Plan details are missing in the request.");
         }
        // Generate id for plan
        UUIDEnrichmentUtil.enrichRandomUuid(body.getPlan(), "id");

        // Generate id for activities
        body.getPlan().getActivities().forEach(activity -> UUIDEnrichmentUtil.enrichRandomUuid(activity, "id"));

        // Generate id for activity conditions
        body.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getConditions())) {
                UUIDEnrichmentUtil.enrichRandomUuid(activity.getConditions(), "id");
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
        body.getPlan().getResources().forEach(resource -> UUIDEnrichmentUtil.enrichRandomUuid(resource, "id"));

        // Generate id for targets
        body.getPlan().getTargets().forEach(target -> UUIDEnrichmentUtil.enrichRandomUuid(target, "id"));

        // Generate id for additional fields
        body.getPlan().getAdditionalFields().forEach(additionalField -> UUIDEnrichmentUtil.enrichRandomUuid(additionalField, "id"));

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
                UUIDEnrichmentUtil.enrichRandomUuid(activity, "id");
                newActivityUuids.add(activity.getId());
            }
        });

        // Generate uuid for new activity conditions
        body.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getConditions()) && newActivityUuids.contains(activity.getId())) {
                activity.getConditions().forEach(condition -> {
                    if(ObjectUtils.isEmpty(condition.getId())) {
                        UUIDEnrichmentUtil.enrichRandomUuid(condition, "id");
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
                UUIDEnrichmentUtil.enrichRandomUuid(resource, "id");
            }
        });

        // Generate uuid for new targets
        body.getPlan().getTargets().forEach(target -> {
            if(ObjectUtils.isEmpty(target.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(target, "id");
            }
        });

        // Generate uuid for new additionalFields
        body.getPlan().getAdditionalFields().forEach(additionalFields -> {
            if(ObjectUtils.isEmpty(additionalFields.getId())) {
                UUIDEnrichmentUtil.enrichRandomUuid(additionalFields, "id");
            }
        });

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
            boundaryAncestralPath.append("|").append(boundary.getCode());
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
        List<String> boundaryCode = getBoundaryCodeFromAncestralPath(plan.getBoundaryAncestralPath());

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
            List<String> boundaryCode = getBoundaryCodeFromAncestralPath(plan.getBoundaryAncestralPath());

            // Creates the mapping of boundary hierarchy with the corresponding boundary code.
            for (String boundary : boundaryCode) {
                jurisdictionMapping.put(boundaryHierarchy, boundary);
                boundaryHierarchy = boundaryHierarchyMapping.get(boundaryHierarchy);
            }

            plan.setJurisdictionMapping(jurisdictionMapping);
        }
    }

    /**
     * Converts the boundaryAncestral path from a pipe separated string to an array of boundary codes.
     *
     * @param boundaryAncestralPath pipe separated boundaryAncestralPath.
     * @return a list of boundary codes.
     */
    private List<String> getBoundaryCodeFromAncestralPath(String boundaryAncestralPath) {
        if (ObjectUtils.isEmpty(boundaryAncestralPath)) {
            return Collections.emptyList();
        }
        return Arrays.asList(boundaryAncestralPath.split("\\|"));
    }
}
