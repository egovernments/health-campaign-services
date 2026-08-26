package org.egov.project.repository.querybuilder;

import org.egov.common.models.project.Project;
import org.egov.project.config.ProjectConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hierarchy match must stay anchored: '%id%' cannot use idx_project_projecthierarchy and reads
 * the whole project table, while '<path>%' is a prefix the index can serve.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectAddressQueryBuilderTest {

    private static final String DISTRICT_ID = "e52b334d-2613-4694-8bc9-178bf0fcadc8";
    private static final String DISTRICT_PATH =
            "e17edea8-859d-4811-b57e-fc806a7313eb.1bec04aa-92ad-4635-880c-678a3226003f." + DISTRICT_ID;

    @Mock
    private ProjectConfiguration config;

    @InjectMocks
    private ProjectAddressQueryBuilder queryBuilder;

    private static List<Project> projectWithId(String id) {
        return Collections.singletonList(Project.builder().id(id).build());
    }

    private void ancestorSearch(Map<String, String> idToHierarchy, List<Object> params) {
        queryBuilder.getProjectSearchQuery(projectWithId(DISTRICT_ID), 10, 0, "mz", null,
                Boolean.FALSE, null, null, true, params, false, idToHierarchy);
    }

    @Test
    @DisplayName("ancestor search anchors the pattern to the project's own path")
    void ancestorSearchAnchorsThePattern() {
        List<Object> params = new ArrayList<>();

        ancestorSearch(Map.of(DISTRICT_ID, DISTRICT_PATH), params);

        assertTrue(params.contains(DISTRICT_PATH + "%"), "expected an anchored prefix pattern");
        assertFalse(params.contains("%" + DISTRICT_ID + "%"), "unanchored pattern must not be used");
    }

    @Test
    @DisplayName("ancestor search keeps the unanchored form when no path is on record")
    void ancestorSearchFallsBackWithoutAPath() {
        List<Object> params = new ArrayList<>();

        ancestorSearch(Collections.emptyMap(), params);

        assertTrue(params.contains("%" + DISTRICT_ID + "%"), "expected the fallback to keep results identical");
    }

    @Test
    @DisplayName("count query anchors the pattern the same way as the search")
    void countQueryAnchorsThePattern() {
        List<Object> params = new ArrayList<>();

        queryBuilder.getSearchCountQueryString(projectWithId(DISTRICT_ID), "mz", null, Boolean.FALSE,
                null, null, true, params, Map.of(DISTRICT_ID, DISTRICT_PATH));

        assertTrue(params.contains(DISTRICT_PATH + "%"));
        assertFalse(params.contains("%" + DISTRICT_ID + "%"));
    }

    @Test
    @DisplayName("a plain id search never touches the hierarchy")
    void plainIdSearchIsUnchanged() {
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getProjectSearchQuery(projectWithId(DISTRICT_ID), 10, 0, "mz", null,
                Boolean.FALSE, null, null, false, params, false);

        assertTrue(query.contains(" prj.id =? "));
        assertFalse(query.contains("projectHierarchy LIKE"));
        assertTrue(params.contains(DISTRICT_ID));
    }

    @Test
    @DisplayName("descendant fetch by path anchors every pattern")
    void descendantFetchAnchorsEachPattern() {
        List<Object> params = new ArrayList<>();
        String otherPath = "aaaaaaaa-0000-4000-8000-000000000001.bbbbbbbb-0000-4000-8000-000000000002";

        queryBuilder.getProjectDescendantsSearchQueryBasedOnHierarchies(List.of(DISTRICT_PATH, otherPath), params);

        assertTrue(params.contains(DISTRICT_PATH + "%"));
        assertTrue(params.contains(otherPath + "%"));
        assertFalse(params.stream().anyMatch(p -> String.valueOf(p).startsWith("%")));
    }

    @Test
    @DisplayName("the path lookup is a primary-key match, one placeholder per id")
    void pathLookupIsByPrimaryKey() {
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getProjectHierarchyQueryBasedOnIds(List.of(DISTRICT_ID, "other-id"), params);

        assertTrue(query.contains("prj.id IN (?, ?)"));
        assertTrue(query.contains("prj.projectHierarchy as project_projectHierarchy"));
        assertFalse(query.contains("LIKE"));
        assertTrue(params.contains(DISTRICT_ID) && params.contains("other-id"));
    }
}
