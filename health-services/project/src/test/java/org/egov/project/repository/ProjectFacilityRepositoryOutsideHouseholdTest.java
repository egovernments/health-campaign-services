package org.egov.project.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.project.repository.rowmapper.ProjectFacilityRowMapper;
import org.egov.project.service.OutsideHouseholdTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SQL of the hierarchy and criteria searches: links to an outside household site are left out by default (as
 * before), and with onlyOutsideHouseholdSites only those links are kept.
 */
@ExtendWith(MockitoExtension.class)
class ProjectFacilityRepositoryOutsideHouseholdTest {

    private static final String HIDE = "AND NOT EXISTS (SELECT 1 FROM";
    private static final String KEEP_ONLY = "AND EXISTS (SELECT 1 FROM";
    private static final String NONE = "AND 1 = 0";
    private static final List<String> SITE_TYPES = List.of("OUTSIDE_HOUSEHOLD_PLACES", "TRANSIT_POST");

    @Mock private Producer producer;
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private SelectQueryBuilder selectQueryBuilder;
    @Mock private ProjectFacilityRowMapper rowMapper;
    @Mock private OutsideHouseholdTypeService outsideHouseholdTypeService;
    @Mock private MultiStateInstanceUtil multiStateInstanceUtil;

    private ProjectFacilityRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new ProjectFacilityRepository(producer, jdbc, redisTemplate, selectQueryBuilder, rowMapper,
                outsideHouseholdTypeService);
        ReflectionTestUtils.setField(repository, "multiStateInstanceUtil", multiStateInstanceUtil);
        lenient().when(multiStateInstanceUtil.replaceSchemaPlaceholder(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private String hierarchySql(boolean descendants, Boolean onlyOutsideHouseholdSites) throws Exception {
        List<String> boundaryTypes = List.of("LOCALITY", "VILLAGE");
        if (descendants) {
            if (onlyOutsideHouseholdSites == null) {
                repository.findFacilitiesByDescendants("P1", boundaryTypes, "qa");
            } else {
                repository.findFacilitiesByDescendants("P1", boundaryTypes, "qa", onlyOutsideHouseholdSites);
            }
        } else if (onlyOutsideHouseholdSites == null) {
            repository.findFacilitiesByAncestors(List.of("P1", "ROOT"), boundaryTypes, "qa");
        } else {
            repository.findFacilitiesByAncestors(List.of("P1", "ROOT"), boundaryTypes, "qa", onlyOutsideHouseholdSites);
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        return sql.getValue();
    }

    @Test
    @DisplayName("hierarchy searches leave out sites by default, as before")
    void hierarchySearchesHideSitesByDefault() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        String descendants = hierarchySql(true, null);
        assertTrue(descendants.contains(HIDE), descendants);
        assertFalse(descendants.contains(KEEP_ONLY), descendants);
    }

    @Test
    @DisplayName("the old signature and onlyOutsideHouseholdSites=false build the same SQL")
    void oldSignatureEqualsFlagFalse() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        repository.findFacilitiesByAncestors(List.of("P1"), List.of("VILLAGE"), "qa");
        repository.findFacilitiesByAncestors(List.of("P1"), List.of("VILLAGE"), "qa", false);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).query(sql.capture(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
        assertEquals(sql.getAllValues().get(0), sql.getAllValues().get(1));
    }

    @Test
    @DisplayName("with onlyOutsideHouseholdSites the hierarchy searches keep only sites")
    void hierarchySearchesKeepOnlySites() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        String descendants = hierarchySql(true, true);
        assertTrue(descendants.contains(KEEP_ONLY), descendants);
        assertFalse(descendants.contains(HIDE), descendants);
        assertTrue(descendants.contains("prj.projectHierarchy LIKE :hierarchyPattern"), descendants);
    }

    @Test
    @DisplayName("with onlyOutsideHouseholdSites the ancestors search keeps only sites, with the site types as parameter")
    void ancestorsSearchKeepsOnlySites() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        repository.findFacilitiesByAncestors(List.of("P1", "ROOT"), List.of("LOCALITY"), "qa", true);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowCallbackHandler.class));
        assertTrue(sql.getValue().contains(KEEP_ONLY), sql.getValue());
        assertEquals(SITE_TYPES, ((MapSqlParameterSource) params.getValue()).getValue("outsideHouseholdTypes"));
    }

    @Test
    @DisplayName("site links are matched on the upper-case usage, so a site type saved in small letters is still a site")
    void sitesAreMatchedOnUpperCaseUsage() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        assertTrue(hierarchySql(true, false).contains("UPPER(fac.usage) IN (:outsideHouseholdTypes)"));
        org.mockito.Mockito.clearInvocations(jdbc);
        assertTrue(hierarchySql(true, true).contains("UPPER(fac.usage) IN (:outsideHouseholdTypes)"));
    }

    @Test
    @DisplayName("with onlyOutsideHouseholdSites and no site type configured, nothing is returned")
    void noSiteTypeMeansNoSite() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(Collections.emptyList());
        String ancestors = hierarchySql(false, true);
        assertTrue(ancestors.contains(NONE), ancestors);
    }

    @Test
    @DisplayName("without onlyOutsideHouseholdSites and no site type configured, nothing is left out, as before")
    void noSiteTypeByDefaultLeavesTheQueryAlone() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(Collections.emptyList());
        String ancestors = hierarchySql(false, null);
        assertFalse(ancestors.contains(HIDE), ancestors);
        assertFalse(ancestors.contains(KEEP_ONLY), ancestors);
        assertFalse(ancestors.contains(NONE), ancestors);
    }

    private String criteriaSql(ProjectFacilitySearch search, Boolean onlyOutsideHouseholdSites) throws Exception {
        when(selectQueryBuilder.build(any(), anyString(), anyString()))
                .thenReturn("SELECT * FROM project_facility WHERE projectId IN (:projectId)");
        when(selectQueryBuilder.getParamsMap()).thenReturn(new HashMap<>());
        when(jdbc.query(anyString(), anyMap(), any(ResultSetExtractor.class))).thenReturn(0L);
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(new ArrayList<>());
        if (onlyOutsideHouseholdSites == null) {
            repository.findWithCount(search, 1, 0, "qa", null, false);
        } else {
            repository.findWithCount(search, 1, 0, "qa", null, false, onlyOutsideHouseholdSites);
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), anyMap(), any(RowMapper.class));
        return sql.getValue();
    }

    @Test
    @DisplayName("criteria search: sites left out unless a facilityId is asked for, as before")
    void criteriaSearchUnchanged() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        String sql = criteriaSql(ProjectFacilitySearch.builder().projectId(List.of("P1")).build(), null);
        assertTrue(sql.contains(HIDE), sql);
    }

    @Test
    @DisplayName("criteria search with a facilityId: nothing left out, as before")
    void criteriaSearchWithFacilityIdUnchanged() throws Exception {
        String sql = criteriaSql(ProjectFacilitySearch.builder().projectId(List.of("P1"))
                .facilityId(List.of("F1")).build(), null);
        assertFalse(sql.contains(HIDE), sql);
        assertFalse(sql.contains(KEEP_ONLY), sql);
    }

    @Test
    @DisplayName("criteria search with onlyOutsideHouseholdSites keeps only sites, also with a facilityId")
    void criteriaSearchKeepsOnlySites() throws Exception {
        when(outsideHouseholdTypeService.getTypes("qa")).thenReturn(SITE_TYPES);
        String sql = criteriaSql(ProjectFacilitySearch.builder().projectId(List.of("P1"))
                .facilityId(List.of("F1")).build(), true);
        assertTrue(sql.contains(KEEP_ONLY), sql);
        assertFalse(sql.contains(HIDE), sql);
    }
}
