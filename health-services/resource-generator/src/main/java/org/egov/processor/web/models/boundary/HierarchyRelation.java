package org.egov.processor.web.models.boundary;

import java.util.List;

import javax.validation.Valid;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HierarchyRelation
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HierarchyRelation {

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("hierarchyType")
    private String hierarchyType = null;

    @JsonProperty("boundary")
    @Valid
    private List<EnrichedBoundary> boundary = null;


}