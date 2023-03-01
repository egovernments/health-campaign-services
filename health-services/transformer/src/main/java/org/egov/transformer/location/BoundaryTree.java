package org.egov.transformer.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryTree {
    private BoundaryNode boundaryNode;
    private List<BoundaryTree> boundaryTrees;
}
