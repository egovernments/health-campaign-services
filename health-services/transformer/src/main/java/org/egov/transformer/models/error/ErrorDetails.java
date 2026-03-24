package org.egov.transformer.models.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.AuditDetails;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorDetails {
    private ApiDetails apiDetails;
    private List<Error> errors;
    private String uuid;
    private AuditDetails auditDetails;
}


