package org.egov.pgr.web.models.boundary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchyRelation {

    private String tenantId = null;

    private String hierarchyType = null;

    private List<EnrichedBoundary> boundary = null;

}