package org.egov.transformer.location;

import org.egov.transformer.models.upstream.Boundary;

import java.util.ArrayList;
import java.util.List;

public class TreeGenerator {


    public BoundaryTree generateTree(Boundary boundary) {
            BoundaryTree boundaryTree = new BoundaryTree();
            boundaryTree.setBoundaryNode(BoundaryMapper.from(boundary));
            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
                List<BoundaryTree> boundaryTrees = new ArrayList<>();
                boundaryTree.setBoundaryTrees(boundaryTrees);
                for (Boundary child : boundary.getChildren()) {
                    boundaryTrees.add(generateTree(child));
                }
            }
        return boundaryTree;
    }
}
