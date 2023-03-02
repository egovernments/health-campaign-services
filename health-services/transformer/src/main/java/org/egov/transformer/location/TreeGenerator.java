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

    public boolean search(BoundaryTree boundaryTree, String code) {
        if (code.equals(boundaryTree.getBoundaryNode().getCode())) {
            return true;
        }
        if (boundaryTree.getBoundaryTrees() != null && !boundaryTree.getBoundaryTrees().isEmpty()) {
            for (BoundaryTree child : boundaryTree.getBoundaryTrees()) {
                return search(child, code);
            }
        }
        return false;
    }
}
