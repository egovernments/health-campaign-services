package org.egov.referralmanagement.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The five PROJECT-file queries: campaign-wide, root+locality parameters only, CTE-fenced where reused. */
class DownsyncFileGenServiceQueryTest {

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
    @DisplayName("every PROJECT query binds exactly :rootProjectId and :locality")
    void parameters() {
        for (String type : new String[]{"HOUSEHOLD", "INDIVIDUAL"}) {
            assertEquals(Set.of("rootProjectId", "locality"), params(DownsyncFileGenService.beneficiaryQuery(type)), "bene " + type);
            assertEquals(Set.of("rootProjectId", "locality"), params(DownsyncFileGenService.sideEffectQuery(type)), "se " + type);
            assertEquals(Set.of("rootProjectId", "locality"), params(DownsyncFileGenService.referralQuery(type)), "ref " + type);
            assertEquals(Set.of("rootProjectId", "locality"), params(DownsyncFileGenService.taskQuery(type)), "task " + type);
        }
        assertEquals(Set.of("rootProjectId", "locality"), params(DownsyncFileGenService.HF_REFERRAL_QUERY));
    }

    @Test
    @DisplayName("task, side-effect and referral queries join the MATERIALIZED village_bene CTE instead of a nested IN")
    void cteJoins() {
        String task = DownsyncFileGenService.taskQuery("INDIVIDUAL");
        assertTrue(task.startsWith("WITH village_bene AS MATERIALIZED ("));
        assertTrue(task.contains("JOIN village_bene vb ON vb.clientReferenceId = pt.projectBeneficiaryClientReferenceId"));
        assertFalse(task.contains("pt2."), "resource sub-select must not re-filter by project");
        assertTrue(task.contains("LEFT JOIN LATERAL ("), "resources must be aggregated per task (LATERAL), not over the whole table");
        assertTrue(task.contains("WHERE tr.taskId = pt.id AND tr.isDeleted = false"), "LATERAL must correlate on the task id");
        assertFalse(task.contains("GROUP BY tr.taskId"), "no whole-table GROUP BY on TASK_RESOURCE");
        assertTrue(task.contains("res_agg.resources_json"), "task resources aggregation retained");
        assertTrue(task.contains("address_json"), "task address json retained");

        assertTrue(DownsyncFileGenService.sideEffectQuery("HOUSEHOLD").contains("JOIN village_bene vb"));
        assertTrue(DownsyncFileGenService.referralQuery("HOUSEHOLD").contains("JOIN village_bene vb"));
    }

    @Test
    @DisplayName("HF referrals are filtered by locality and campaign membership")
    void hfReferral() {
        String sql = DownsyncFileGenService.HF_REFERRAL_QUERY;
        assertTrue(sql.contains("hfr.localitycode = :locality"));
        assertTrue(sql.contains("hfr.projectid IN (SELECT p.id FROM {schema}.PROJECT p"));
    }
}
