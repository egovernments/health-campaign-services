package org.egov.common.helpers;

import org.egov.common.contract.models.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name="some_table2")
public class SomeObjectWithClientRefId {
    private String id;
    private String clientReferenceId;
    private String otherClientReferenceId;
    private String otherField;
    private Integer rowVersion;
    private String tenantId;
    private Boolean isDeleted;
    private AuditDetails auditDetails;
}
