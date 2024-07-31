package org.egov.project.util;

import digit.models.coremodels.AuditDetails;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.egov.common.models.project.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectServiceUtil {

    public AuditDetails getAuditDetails(String by, AuditDetails auditDetails, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        if (isCreate)
            return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time).build();
        else
            return AuditDetails.builder().createdBy(auditDetails.getCreatedBy()).lastModifiedBy(by)
                    .createdTime(auditDetails.getCreatedTime()).lastModifiedTime(time).build();
    }


    /**
     * Creates a map from a list of projects, using project IDs as keys.
     *
     * @param projects The list of projects to be converted into a map.
     * @return A map with project IDs as keys and project objects as values.
     */
    public Map<String, Project> createProjectMap(List<Project> projects) {
        return projects.stream()
            .collect(Collectors.toMap(p -> String.valueOf(p.getId()), Function.identity()));
    }
}
