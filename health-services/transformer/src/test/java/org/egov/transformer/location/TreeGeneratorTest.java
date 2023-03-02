package org.egov.transformer.location;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.models.upstream.Boundary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class TreeGeneratorTest {

    private TreeGenerator treeGenerator;

    @BeforeEach
    void setUp() {
        treeGenerator = new TreeGenerator();
    }

    private static List<Boundary> getTestBoundaryList() {
        List<Boundary> boundaryList = new ArrayList<>();
        boundaryList.add(Boundary.builder()
                .code("LC00001")
                .name("Tete")
                .label("Province")
                .children(Arrays.asList(Boundary.builder()
                        .code("LC00002")
                        .name("Angónia")
                        .label("District")
                        .children(Arrays.asList(Boundary.builder()
                                        .code("LC00003")
                                        .name("Ulongué")
                                        .label("AdministrativeProvince")
                                        .children(Arrays.asList(Boundary.builder()
                                                        .code("LC00004")
                                                        .name("TAU L1")
                                                        .label("Locality")
                                                        .build(),
                                                Boundary.builder()
                                                        .code("LC00007")
                                                        .name("TAU L2 V1")
                                                        .label("Village")
                                                        .children(Arrays.asList())
                                                        .build()))
                                        .build(),
                                Boundary.builder()
                                        .code("LC000010")
                                        .name("Dómuè")
                                        .label("AdministrativeProvince")
                                        .build()))
                        .build()))
                .build());
        return boundaryList;
    }

    @Test
    void shouldGenerateATreeWithNodeHavingNoChildren() {
        List<Boundary> boundaryList = new ArrayList<>();
        boundaryList.add(Boundary.builder()
                        .code("LC00001")
                        .name("Tete")
                        .label("Province")
                .build());
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertEquals("LC00001", boundaryTree.getBoundaryNode().getCode());
        assertNull(boundaryTree.getBoundaryTrees());
    }

    @Test
    void shouldGenerateATreeWithNodeHavingOneChild() {
        List<Boundary> boundaryList = new ArrayList<>();
        boundaryList.add(Boundary.builder()
                .code("LC00001")
                .name("Tete")
                .label("Province")
                        .children(Arrays.asList(Boundary.builder()
                                .code("LC00002")
                                .name("Angónia")
                                .label("District")
                                .build()))
                .build());
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertTrue(boundaryTree.getBoundaryTrees().stream()
                .anyMatch(b -> b.getBoundaryNode().getCode().equals("LC00002")));
    }

    @Test
    void shouldGenerateATreeWithNodeHavingTwoChildrenOfAChild() {
        List<Boundary> boundaryList = new ArrayList<>();
        boundaryList.add(Boundary.builder()
                .code("LC00001")
                .name("Tete")
                .label("Province")
                .children(Arrays.asList(Boundary.builder()
                        .code("LC00002")
                        .name("Angónia")
                        .label("District")
                        .children(Arrays.asList(Boundary.builder()
                                        .code("LC00003")
                                        .name("Ulongué")
                                        .label("AdministrativeProvince")
                                        .build(),
                                Boundary.builder()
                                        .code("LC000010")
                                        .name("Dómuè")
                                        .label("AdministrativeProvince")
                                        .build()))
                        .build()))
                .build());
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertTrue(boundaryTree.getBoundaryTrees().stream()
                .filter(b -> b.getBoundaryNode().getCode().equals("LC00002")).findFirst()
                .filter(b -> b.getBoundaryTrees().stream().findAny().isPresent()).isPresent());
    }

    @Test
    void shouldGenerateATreeWithNodeHavingTwoChildrenOfAChildAndTwoChildrenOfOneChildrenOfAChild() {
        List<Boundary> boundaryList = getTestBoundaryList();
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertTrue(boundaryTree.getBoundaryTrees().stream()
                .filter(b -> b.getBoundaryNode().getCode().equals("LC00002"))
                .map(b -> b.getBoundaryTrees().stream()
                        .filter(b2 -> b2.getBoundaryNode().getCode().equals("LC00003"))
                        .map(b3 -> b3.getBoundaryTrees().size() == 2)).findAny().isPresent());
    }

    @Test
    void shouldReturnTrueIfAGivenBoundaryNodeExistsInTheTree() {
        List<Boundary> boundaryList = getTestBoundaryList();
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertTrue(treeGenerator.search(boundaryTree, "LC00004"));
    }

    @Test
    void shouldReturnFalseIfAGivenBoundaryNodeDoesNotExistInTheTree() {
        List<Boundary> boundaryList = getTestBoundaryList();
        BoundaryTree boundaryTree = treeGenerator.generateTree(boundaryList.get(0));
        assertFalse(treeGenerator.search(boundaryTree, "LC00005"));
    }
}