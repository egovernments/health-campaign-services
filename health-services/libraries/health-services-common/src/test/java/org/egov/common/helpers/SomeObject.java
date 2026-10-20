package org.egov.common.helpers;

import org.egov.common.contract.models.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.annotations.Table;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name="some_table")
public class SomeObject {
    private String id;
    private String otherField;
    private Integer rowVersion;
    private String tenantId;
    private Boolean isDeleted;
    private RequestInfo requestInfo;
    private List<OtherObject> otherObject;
    private AuditDetails auditDetails;
}