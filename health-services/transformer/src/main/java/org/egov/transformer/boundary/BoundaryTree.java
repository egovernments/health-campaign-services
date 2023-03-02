package org.egov.transformer.boundary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BoundaryTree {
    private BoundaryNode boundaryNode;
    private BoundaryTree parent;
    private List<BoundaryTree> boundaryTrees;

    public List<BoundaryNode> getParentNodes() {
        if (parent == null) {
            return Collections.emptyList();
        }
        BoundaryTree referenceTree = this.parent;
        List<BoundaryNode> parentNodes = new ArrayList<>();
        while (referenceTree != null) {
            parentNodes.add(referenceTree.getBoundaryNode());
            referenceTree = referenceTree.parent;
        }
        return parentNodes;
    }
}
