package org.egov.transformer.boundary;


import org.egov.common.models.transformer.upstream.Boundary;

public class BoundaryMapper {

    public static BoundaryNode from(Boundary boundary) {
        return BoundaryNode.builder()
                .name(boundary.getName())
                .code(boundary.getCode())
                .label(boundary.getLabel())
                .latitude(boundary.getLatitude())
                .longitude(boundary.getLongitude())
                .build();
    }

}
