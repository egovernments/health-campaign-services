package digit.service;

import digit.web.models.Plan;
import digit.web.models.PlanRequest;
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

        // Enriching last modified time for update
        body.getPlan().getAuditDetails().setLastModifiedTime(System.currentTimeMillis());
    }

    /**
     * Enriches the boundary ancestral path for the provided boundary code in the census request.
     *
     * @param plan         The plan record whose boundary ancestral path has to be enriched.
     * @param tenantBoundary boundary relationship from the boundary service for the given boundary code.
     */
    public void enrichBoundaryAncestralPath(Plan plan, HierarchyRelation tenantBoundary) {
        EnrichedBoundary boundary = tenantBoundary.getBoundary().get(0);
        StringBuilder boundaryAncestralPath = new StringBuilder(boundary.getCode());

        // Iterate through the child boundary until there are no more
        while (!CollectionUtils.isEmpty(boundary.getChildren())) {
            boundary = boundary.getChildren().get(0);
            boundaryAncestralPath.append("|").append(boundary.getCode());
        }

        // Setting the boundary ancestral path for the provided boundary
        plan.setBoundaryAncestralPath(boundaryAncestralPath.toString());
    }
}
