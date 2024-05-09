package org.egov.transformer.boundary;


import org.egov.common.models.core.Boundary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Deprecated
//FIXME to be removed
public class TreeGenerator {


    public BoundaryTree generateTree(Boundary boundary) {
            BoundaryTree boundaryTree = new BoundaryTree();
            boundaryTree.setBoundaryNode(BoundaryMapper.from(boundary));
//            if (boundary.getChildren() != null && !boundary.getChildren().isEmpty()) {
//                List<BoundaryTree> boundaryTrees = new ArrayList<>();
//                boundaryTree.setBoundaryTrees(boundaryTrees);
//                for (Boundary child : boundary.getChildren()) {
//                    BoundaryTree resultTree = generateTree(child);
//                    resultTree.setParent(boundaryTree);
//                    boundaryTrees.add(resultTree);
//                }
//            }
        return boundaryTree;
    }

    public BoundaryTree search(BoundaryTree boundaryTree, String code) {
        if (code.equals(boundaryTree.getBoundaryNode().getCode())) {
            return boundaryTree;
        }
        BoundaryTree bt = null;
        if (boundaryTree.getBoundaryTrees() != null && !boundaryTree.getBoundaryTrees().isEmpty()) {
            for (BoundaryTree child : boundaryTree.getBoundaryTrees()) {
                bt = search(child, code);
                if (bt != null) {
                    break;
                }
            }
        }
        return bt;
    }
}
