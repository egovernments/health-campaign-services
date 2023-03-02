package org.egov.transformer.location;

import org.egov.transformer.models.upstream.Boundary;

import java.util.ArrayList;
import java.util.List;

public class TreeGenerator {


    public BoundaryTree generateTree(Boundary boundary) {
            BoundaryTree boundaryTree = new BoundaryTree();
            BoundaryNode current = BoundaryMapper.from(boundary);
            boundaryTree.setBoundaryNode(current);
            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
                List<BoundaryTree> boundaryTrees = new ArrayList<>();
                boundaryTree.setBoundaryTrees(boundaryTrees);
                for (Boundary child : boundary.getChildren()) {
                    BoundaryTree resultTree = generateTree(child);
                    resultTree.setParent(current);
                    boundaryTrees.add(resultTree);
                }
            }
        return boundaryTree;
    }

    public BoundaryNode search(BoundaryTree boundaryTree, String code) {
        if (code.equals(boundaryTree.getBoundaryNode().getCode())) {
            return boundaryTree.getBoundaryNode();
        }
        BoundaryNode boundaryNode = null;
        if (boundaryTree.getBoundaryTrees() != null && !boundaryTree.getBoundaryTrees().isEmpty()) {
            for (BoundaryTree child : boundaryTree.getBoundaryTrees()) {
                boundaryNode = search(child, code);
                if (boundaryNode != null) {
                    break;
                }
            }
        }
        return boundaryNode;
    }
}
