package org.egov.transformer.boundary;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryNode {
    private String id;
    private String tenantId;
    private String code;
    private JsonNode geometry;
}
