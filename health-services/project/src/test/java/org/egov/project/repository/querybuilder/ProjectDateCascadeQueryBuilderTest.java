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
    @DisplayName("descendant predicate matches the existing hierarchy search so the same rows are returned")
    void dateCascadeDescendantPredicateMatchesExisting() {
        List<Object> narrowParams = new ArrayList<>();
        List<Object> wideParams = new ArrayList<>();

        String narrow = queryBuilder.getDateCascadeDescendantsQueryBasedOnIds(Arrays.asList("root"), narrowParams);
        queryBuilder.getProjectDescendantsSearchQueryBasedOnIds(Arrays.asList("root"), wideParams);

        assertTrue(narrow.contains("prj.projectHierarchy LIKE ?"));
        assertEquals(wideParams, narrowParams, "bound parameters must match the existing descendant search");
    }
}
