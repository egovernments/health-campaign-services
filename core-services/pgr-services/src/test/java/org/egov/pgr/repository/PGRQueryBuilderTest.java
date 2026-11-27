package org.egov.pgr.repository;

import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.repository.rowmapper.PGRQueryBuilder;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PGRQueryBuilderTest {

    private static final String RESTRICTION = "(ser.accountId = ? OR ser.createdby = ?)";

    private final PGRQueryBuilder queryBuilder = new PGRQueryBuilder(new PGRConfiguration());

    private int count(String query, char c) {
        return (int) query.chars().filter(ch -> ch == c).count();
    }

    @Test
    public void restrictedSearchHasOrConditionWithTwoParametersAndNoValueInSql() {
        String evilUuid = "u1' OR '1'='1";
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya").limit(10).offset(0).build();
        criteria.restrictToUser(evilUuid);
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        assertTrue(query.contains(RESTRICTION), query);
        assertFalse(query.contains(evilUuid), "value must go only as a prepared statement parameter");
        assertEquals(params.size(), count(query, '?'));
        // tenantId, accountId, createdby, offset, limit
        assertEquals(Arrays.asList("pg.citya", evilUuid, evilUuid, 0, 10), params);
        assertTrue(query.indexOf("ser.tenantid=?") < query.indexOf(RESTRICTION));
        assertTrue(query.contains(" WHERE  ser.tenantid=?  AND (ser.accountId = ? OR ser.createdby = ?) "), query);
    }

    @Test
    public void restrictionIsTheFirstClauseWhenNothingElseIsSet() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().limit(10).offset(0).build();
        criteria.restrictToUser("u1");
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        assertTrue(query.contains(" WHERE  (ser.accountId = ? OR ser.createdby = ?) "), query);
        assertEquals(Arrays.asList("u1", "u1", 0, 10), params);
    }

    @Test
    public void restrictionIsAndedWithClientFilters() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya")
                .userIds(Collections.singleton("other-user")).limit(10).offset(0).build();
        criteria.restrictToUser("u1");
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        // client sent userIds stay as an AND condition: they narrow the result, never widen it
        assertTrue(query.contains("ser.accountId IN ( ? ) AND (ser.accountId = ? OR ser.createdby = ?)"), query);
        assertEquals(Arrays.asList("pg.citya", "other-user", "u1", "u1", 0, 10), params);
        assertEquals(params.size(), count(query, '?'));
    }

    @Test
    public void unrestrictedSearchHasNoRestrictionClause() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya").limit(10).offset(0).build();
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        assertFalse(query.contains("ser.createdby = ?"), query);
        assertEquals(Arrays.asList("pg.citya", 0, 10), params);
    }

    @Test
    public void blankRestrictionIsNotApplied() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya").limit(10).offset(0).build();
        criteria.restrictToUser("  ");
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        assertFalse(query.contains("ser.createdby = ?"), query);
    }

    @Test
    public void countQueryCarriesTheRestriction() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg.citya").limit(10).offset(0).build();
        criteria.restrictToUser("u1");
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getCountQuery(criteria, params);

        assertTrue(query.startsWith("select count(*) from ("), query);
        assertTrue(query.contains(RESTRICTION), query);
        assertEquals(Arrays.asList("pg.citya", "u1", "u1", 0, 10), params);
    }

    @Test
    public void restrictionAppliesWithStateLevelTenantLikeSearch() {
        RequestSearchCriteria criteria = RequestSearchCriteria.builder().tenantId("pg").limit(10).offset(0).build();
        criteria.restrictToUser("u1");
        List<Object> params = new ArrayList<>();

        String query = queryBuilder.getPGRSearchQuery(criteria, params);

        assertTrue(query.contains("ser.tenantid LIKE ?  AND (ser.accountId = ? OR ser.createdby = ?)"), query);
        assertEquals(Arrays.asList("pg%", "u1", "u1", 0, 10), params);
    }
}
