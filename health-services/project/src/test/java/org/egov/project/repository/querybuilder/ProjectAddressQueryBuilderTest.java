package org.egov.project.repository.querybuilder;

import org.egov.common.models.project.Project;
import org.egov.project.config.ProjectConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ProjectAddressQueryBuilderTest {

    private static final String DISTRICT_ID = "e52b334d-2613-4694-8bc9-178bf0fcadc8";
    private static final String DISTRICT_PATH = "e17edea8-859d-4811-b57e-fc806a7313eb.1bec04aa-92ad-4635-880c-678a3226003f." + DISTRICT_ID;

    @Mock
    private ProjectConfiguration config;

    @InjectMocks
    private ProjectAddressQueryBuilder queryBuilder;

    private static List<Project> projectWithId(String id) {
        return Collections.singletonList(Project.builder().id(id).build());
    }

    private String buildAncestorSearch(List<Object> params) {
        return queryBuilder.getProjectSearchQuery(projectWithId(DISTRICT_ID), 10, 0, "demo", null,
                Boolean.FALSE, null, null, true, params, false);
    }

    @Test
    @DisplayName("ancestor search walks the parent chain instead of matching the hierarchy text")
    void ancestorSearchRecursesOverParent() {
        List<Object> params = new ArrayList<>();

        String query = buildAncestorSearch(params);

        assertTrue(query.contains("WITH RECURSIVE descendants"), "expected a recursive walk");
        assertTrue(query.contains("child.parent = descendants.id"), "expected the parent join");
        assertFalse(query.contains("projectHierarchy LIKE"), "hierarchy text must not be matched");
        // the id is bound bare — no LIKE pattern is built from it any more
        assertTrue(params.contains(DISTRICT_ID));
        assertTrue(params.stream().noneMatch(p -> String.valueOf(p).contains("%" + DISTRICT_ID)));
        assertTrue(params.stream().noneMatch(p -> String.valueOf(p).equals(DISTRICT_PATH + "%")));
    }

    @Test
    @DisplayName("a plain id search is untouched by the ancestor branch")
    void plainIdSearchIsUnchanged() {
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getProjectSearchQuery(projectWithId(DISTRICT_ID), 10, 0, "demo", null,
                Boolean.FALSE, null, null, false, params, false);

        assertTrue(query.contains("prj.id =?"), "expected a primary-key match");
        assertFalse(query.contains("WITH RECURSIVE"), "no recursion for a plain id search");
        assertTrue(params.contains(DISTRICT_ID));
    }

    @Test
    @DisplayName("descendant fetch recurses too, one clause per ancestor id")
    void descendantFetchRecursesPerId() {
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getProjectDescendantsSearchQueryByRecursion(
                List.of(DISTRICT_ID, "b7c1f0aa-0000-4000-8000-000000000001"), params);

        assertEquals(2, countOccurrences(query, "WITH RECURSIVE descendants"));
        assertFalse(query.contains("projectHierarchy LIKE"));
        assertEquals(2, params.size());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
