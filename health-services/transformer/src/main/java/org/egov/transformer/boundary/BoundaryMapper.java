package org.egov.transformer.boundary;


import org.egov.common.models.core.Boundary;

public class BoundaryMapper {

    public static BoundaryNode from(Boundary boundary) {
        return BoundaryNode.builder()
                .id(boundary.getId())
                .code(boundary.getCode())
                .tenantId(boundary.getTenantId())
                .geometry(boundary.getGeometry())
                .build();
    }

}
