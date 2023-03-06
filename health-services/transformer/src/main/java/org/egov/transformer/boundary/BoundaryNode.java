package org.egov.transformer.boundary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryNode {
    private String code;
    private String name;
    private String label;
    private String latitude;
    private String longitude;
}
