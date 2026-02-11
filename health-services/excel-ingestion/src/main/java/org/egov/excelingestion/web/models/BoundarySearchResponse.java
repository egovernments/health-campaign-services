package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * BoundarySearchResponse
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundarySearchResponse {

    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("TenantBoundary")
    @Valid
    private List<HierarchyRelation> tenantBoundary = null;

    public BoundarySearchResponse addTenantBoundaryItem(HierarchyRelation tenantBoundaryItem) {
        if (this.tenantBoundary == null) {
            this.tenantBoundary = new ArrayList<>();
        }
        this.tenantBoundary.add(tenantBoundaryItem);
        return this;
    }

}