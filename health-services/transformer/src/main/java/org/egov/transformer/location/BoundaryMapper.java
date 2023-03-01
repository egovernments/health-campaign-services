package org.egov.transformer.location;

import org.egov.transformer.models.upstream.Boundary;

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
