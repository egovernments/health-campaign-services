package org.egov.project.util;

import digit.models.coremodels.AuditDetails;
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
}
