package org.egov.referralmanagement.repository;

import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks down the campaign-wide PROJECT query fragments:
 * <ul>
 *   <li>campaign membership goes through project.projecthierarchy, never a project-id list;</li>
 *   <li>exactly one beneficiary-reference branch per beneficiaryType, no UNION;</li>
 *   <li>the village beneficiary set is a MATERIALIZED CTE;</li>
 *   <li>the only bind parameters are :rootProjectId and :locality, and Spring's named-parameter
 *       parser handles the {@code :rootProjectId || '.%'} construct.</li>
 * </ul>
 */
class DownsyncSqlFragmentsTest {

    /** Bind-parameter names as Spring will see them; also proves the SQL substitutes cleanly (e.g. {@code :root || '.%'}). */
    private static Set<String> params(String sql) {
        String resolved = sql.replace("{schema}", "ba");
        String substituted = NamedParameterUtils.substituteNamedParameters(resolved,
                new MapSqlParameterSource().addValue("rootProjectId", "r").addValue("locality", "l"));
        assertFalse(substituted.matches("(?s).*(?<!:):[A-Za-z].*"), "unsubstituted named parameter left in: " + substituted);
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("(?<!:):([A-Za-z]\\w*)").matcher(resolved);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    @Test
    @DisplayName("campaign filter uses projecthierarchy prefix, root included, no :projectId")
    void campaignFilter() {
        String sql = DownsyncSqlFragments.CAMPAIGN_PROJECT_IDS;
        assertTrue(sql.contains("p.id = :rootProjectId"));
        assertTrue(sql.contains("p.projecthierarchy LIKE :rootProjectId || '.%'"));
        assertFalse(sql.contains(":projectId"));
        assertEquals(Set.of("rootProjectId"), params(sql));
    }

    @Test
    @DisplayName("INDIVIDUAL campaigns resolve beneficiaries through HOUSEHOLD_MEMBER only")
    void individualBranch() {
        String sql = DownsyncSqlFragments.beneficiaryRefsInLocality("INDIVIDUAL");
        assertTrue(sql.contains("HOUSEHOLD_MEMBER"));
        assertTrue(sql.contains("individualClientReferenceId"));
        assertFalse(sql.contains("UNION"));
        assertEquals(Set.of("locality"), params(sql));
    }

    @Test
    @DisplayName("HOUSEHOLD campaigns resolve beneficiaries through household_address_mv only")
    void householdBranch() {
        String sql = DownsyncSqlFragments.beneficiaryRefsInLocality("household");   // case-insensitive
        assertTrue(sql.contains("household_address_mv"));
        assertFalse(sql.contains("HOUSEHOLD_MEMBER"));
        assertFalse(sql.contains("UNION"));
        assertEquals(Set.of("locality"), params(sql));
    }

    @Test
    @DisplayName("village beneficiary set is a MATERIALIZED CTE bound to root + locality")
    void cte() {
        for (String type : new String[]{"HOUSEHOLD", "INDIVIDUAL"}) {
            String sql = DownsyncSqlFragments.villageBeneficiaryCte(type) + "SELECT 1 FROM village_bene";
            assertTrue(sql.startsWith("WITH village_bene AS MATERIALIZED ("), type);
            assertFalse(sql.contains(":projectId"), type);
            assertEquals(Set.of("rootProjectId", "locality"), params(sql), type);
        }
    }

    @Test
    @DisplayName("full beneficiary query selects pb.* for the campaign in the locality")
    void villageBeneficiaries() {
        String sql = DownsyncSqlFragments.villageBeneficiaries("INDIVIDUAL");
        assertTrue(sql.startsWith("SELECT pb.* FROM {schema}.PROJECT_BENEFICIARY pb"));
        assertTrue(sql.contains("pb.projectId IN (SELECT p.id FROM {schema}.PROJECT p"));
        assertEquals(Set.of("rootProjectId", "locality"), params(sql));
    }

    @Test
    @DisplayName("unknown or missing beneficiaryType is rejected loudly")
    void invalidType() {
        CustomException e1 = assertThrows(CustomException.class, () -> DownsyncSqlFragments.beneficiaryRefsInLocality(null));
        CustomException e2 = assertThrows(CustomException.class, () -> DownsyncSqlFragments.villageBeneficiaryCte("PERSON"));
        assertEquals("INVALID_BENEFICIARY_TYPE", e1.getCode());
        assertEquals("INVALID_BENEFICIARY_TYPE", e2.getCode());
    }
}
