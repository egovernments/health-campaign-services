package org.egov.project.repository.querybuilder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
    import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ProjectDateCascadeQueryBuilderTest {

    @InjectMocks
    private ProjectAddressQueryBuilder queryBuilder;

    @Test
    @DisplayName("date cascade projection does not join project_address")
    void dateCascadeQueryOmitsAddressJoin() {
        List<Object> preparedStmtList = new ArrayList<>();

        String query = queryBuilder.getDateCascadeQueryBasedOnIds(Arrays.asList("p1", "p2"), preparedStmtList);

        assertFalse(query.contains("project_address"), "address join must not be pulled for the date cascade");
        assertFalse(query.contains("addr."), "address columns must not be selected for the date cascade");
    }

    @Test
    @DisplayName("date cascade projection selects only the columns the date-only persister writes")
    void dateCascadeQuerySelectsOnlyRequiredColumns() {
        List<Object> preparedStmtList = new ArrayList<>();

        String query = queryBuilder.getDateCascadeQueryBasedOnIds(Arrays.asList("p1"), preparedStmtList);

        assertTrue(query.contains("prj.startDate as project_startDate"));
        assertTrue(query.contains("prj.endDate as project_endDate"));
        assertTrue(query.contains("prj.additionalDetails as project_additionalDetails"));
        assertTrue(query.contains("prj.projectHierarchy as project_projectHierarchy"));
        assertTrue(query.contains("prj.parent as project_parent"));
        // columns the cascade never rewrites should stay out of the result set
        assertFalse(query.contains("project_name"));
        assertFalse(query.contains("project_department"));
        assertFalse(query.contains("project_natureOfWork"));
    }

    @Test
    @DisplayName("date cascade id lookup binds every id")
    void dateCascadeQueryBindsIds() {
        List<Object> preparedStmtList = new ArrayList<>();

        String query = queryBuilder.getDateCascadeQueryBasedOnIds(Arrays.asList("p1", "p2"), preparedStmtList);

        assertTrue(query.contains("WHERE prj.id IN ("));
        assertEquals(Arrays.asList("p1", "p2"), preparedStmtList);
    }

    @Test
    @DisplayName("anchored descendant query binds prefix + separator so the index can serve it")
    void anchoredDescendantQueryBindsPrefix() {
        List<Object> preparedStmtList = new ArrayList<>();

        String query = queryBuilder.getDateCascadeDescendantsQueryBasedOnHierarchies(
                Arrays.asList("ROOT.P"), preparedStmtList);

        assertTrue(query.contains("prj.projectHierarchy LIKE ?"));
        assertEquals(Arrays.asList("ROOT.P.%"), preparedStmtList);
        assertFalse(query.contains("project_address"), "anchored cascade query must not join the address table");
    }

    @Test
    @DisplayName("anchored descendant query binds exactly as the merged search path does")
    void anchoredDescendantQueryMatchesSearchPathBinding() {
        List<Object> cascadeParams = new ArrayList<>();
        List<Object> searchParams = new ArrayList<>();

        queryBuilder.getDateCascadeDescendantsQueryBasedOnHierarchies(Arrays.asList("ROOT.P"), cascadeParams);
        queryBuilder.getProjectDescendantsSearchQueryBasedOnHierarchies(Arrays.asList("ROOT.P"), searchParams);

        assertEquals(searchParams, cascadeParams, "cascade must select the same rows as the search path");
    }

    @Test
    @DisplayName("hierarchyPrefix anchors a root project on its own id and refuses an unresolvable row")
    void hierarchyPrefixBehaviour() {
        // root: no path of its own, children start at its id
        assertEquals("ROOT", ProjectAddressQueryBuilder.hierarchyPrefix(null, "ROOT", null));
        // non-root: its own full path
        assertEquals("ROOT.P", ProjectAddressQueryBuilder.hierarchyPrefix("ROOT.P", "P", "ROOT"));
        // data gap: has a parent but no path -> unsafe to anchor, caller must fall back
        assertNull(ProjectAddressQueryBuilder.hierarchyPrefix(null, "P", "ROOT"));
    }

    @Test
    @DisplayName("unanchored fallback predicate matches the existing hierarchy search so the same rows are returned")
    void dateCascadeDescendantPredicateMatchesExisting() {
        List<Object> narrowParams = new ArrayList<>();
        List<Object> wideParams = new ArrayList<>();

        String narrow = queryBuilder.getDateCascadeDescendantsQueryBasedOnIds(Arrays.asList("root"), narrowParams);
        queryBuilder.getProjectDescendantsSearchQueryBasedOnIds(Arrays.asList("root"), wideParams);

        assertTrue(narrow.contains("prj.projectHierarchy LIKE ?"));
        assertEquals(wideParams, narrowParams, "bound parameters must match the existing descendant search");
    }
}
