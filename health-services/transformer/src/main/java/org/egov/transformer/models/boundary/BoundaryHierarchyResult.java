package org.egov.transformer.models.boundary;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryHierarchyResult {

    // Defaulted so an unresolved boundary yields empty maps rather than nulls: callers assign these
    // straight into index documents and call isEmpty() on them. @Builder.Default is required because
    // both the no-args constructor and the builder are used to create instances.
    @JsonProperty("boundaryHierarchy")
    @Builder.Default
    private Map<String, String> boundaryHierarchy = new HashMap<>();

    @JsonProperty("boundaryHierarchyCode")
    @Builder.Default
    private Map<String, String> boundaryHierarchyCode = new HashMap<>();
}
