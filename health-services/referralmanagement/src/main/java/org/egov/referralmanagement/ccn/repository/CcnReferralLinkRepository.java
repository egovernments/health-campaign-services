package org.egov.referralmanagement.ccn.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for {@code ccn_referral_link}. Isolated from the module's GenericRepository
 * machinery — plain named-parameter upserts/updates.
 */
@Slf4j
@Repository
public class CcnReferralLinkRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CcnReferralLinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT =
            "INSERT INTO ccn_referral_link (coordination_id, transaction_id, hf_referral_id, " +
            "hf_referral_client_reference_id, beneficiary_id, lifecycle_state, last_action, tenant_id, " +
            "created_time, last_modified_time) VALUES (:coordinationId, :transactionId, :hfReferralId, " +
            ":hfReferralClientReferenceId, :beneficiaryId, :lifecycleState, :lastAction, :tenantId, " +
            ":createdTime, :lastModifiedTime) " +
            "ON CONFLICT (coordination_id) DO UPDATE SET lifecycle_state = EXCLUDED.lifecycle_state, " +
            "last_action = EXCLUDED.last_action, last_modified_time = EXCLUDED.last_modified_time";

    private static final String UPDATE_STATE =
            "UPDATE ccn_referral_link SET lifecycle_state = :lifecycleState, last_action = :lastAction, " +
            "last_modified_time = :lastModifiedTime WHERE coordination_id = :coordinationId";

    public void save(CcnReferralLink link) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("coordinationId", link.getCoordinationId())
                .addValue("transactionId", link.getTransactionId())
                .addValue("hfReferralId", link.getHfReferralId())
                .addValue("hfReferralClientReferenceId", link.getHfReferralClientReferenceId())
                .addValue("beneficiaryId", link.getBeneficiaryId())
                .addValue("lifecycleState", link.getLifecycleState())
                .addValue("lastAction", link.getLastAction())
                .addValue("tenantId", link.getTenantId())
                .addValue("createdTime", link.getCreatedTime())
                .addValue("lastModifiedTime", link.getLastModifiedTime());
        jdbc.update(UPSERT, p);
    }

    /** Update lifecycle from an inbound SPICE callback. Returns rows affected (0 = unknown coordinationId). */
    public int updateState(String coordinationId, String lifecycleState, String lastAction, long modifiedTime) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("coordinationId", coordinationId)
                .addValue("lifecycleState", lifecycleState)
                .addValue("lastAction", lastAction)
                .addValue("lastModifiedTime", modifiedTime);
        return jdbc.update(UPDATE_STATE, p);
    }
}
