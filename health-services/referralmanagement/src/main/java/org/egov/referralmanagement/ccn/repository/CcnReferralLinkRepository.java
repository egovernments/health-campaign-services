package org.egov.referralmanagement.ccn.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JDBC repository for {@code ccn_referral_link} (both directions). Isolated from the module's
 * GenericRepository machinery — plain named-parameter upserts/updates.
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
            "hf_referral_client_reference_id, beneficiary_id, lifecycle_state, last_action, direction, " +
            "local_role, initiator_subscriber_id, counterparty_subscriber_id, contract_type, service_category, " +
            "target_booking_ref, last_payload, tenant_id, created_time, last_modified_time) VALUES " +
            "(:coordinationId, :transactionId, :hfReferralId, :hfReferralClientReferenceId, :beneficiaryId, " +
            ":lifecycleState, :lastAction, :direction, :localRole, :initiatorSubscriberId, :counterpartySubscriberId, " +
            ":contractType, :serviceCategory, :targetBookingRef, :lastPayload, :tenantId, :createdTime, :lastModifiedTime) " +
            "ON CONFLICT (coordination_id) DO UPDATE SET lifecycle_state = EXCLUDED.lifecycle_state, " +
            "last_action = EXCLUDED.last_action, target_booking_ref = COALESCE(EXCLUDED.target_booking_ref, ccn_referral_link.target_booking_ref), " +
            "last_payload = EXCLUDED.last_payload, last_modified_time = EXCLUDED.last_modified_time";

    private static final String UPDATE_STATE =
            "UPDATE ccn_referral_link SET lifecycle_state = :lifecycleState, last_action = :lastAction, " +
            "last_modified_time = :lastModifiedTime WHERE coordination_id = :coordinationId";

    private static final String SELECT_BY_ID =
            "SELECT * FROM ccn_referral_link WHERE coordination_id = :coordinationId";

    private final RowMapper<CcnReferralLink> mapper = (rs, i) -> CcnReferralLink.builder()
            .coordinationId(rs.getString("coordination_id"))
            .transactionId(rs.getString("transaction_id"))
            .hfReferralId(rs.getString("hf_referral_id"))
            .beneficiaryId(rs.getString("beneficiary_id"))
            .lifecycleState(rs.getString("lifecycle_state"))
            .lastAction(rs.getString("last_action"))
            .direction(rs.getString("direction"))
            .localRole(rs.getString("local_role"))
            .initiatorSubscriberId(rs.getString("initiator_subscriber_id"))
            .counterpartySubscriberId(rs.getString("counterparty_subscriber_id"))
            .contractType(rs.getString("contract_type"))
            .serviceCategory(rs.getString("service_category"))
            .targetBookingRef(rs.getString("target_booking_ref"))
            .tenantId(rs.getString("tenant_id"))
            .build();

    public void save(CcnReferralLink l) {
        jdbc.update(UPSERT, new MapSqlParameterSource()
                .addValue("coordinationId", l.getCoordinationId())
                .addValue("transactionId", l.getTransactionId())
                .addValue("hfReferralId", l.getHfReferralId())
                .addValue("hfReferralClientReferenceId", l.getHfReferralClientReferenceId())
                .addValue("beneficiaryId", l.getBeneficiaryId())
                .addValue("lifecycleState", l.getLifecycleState())
                .addValue("lastAction", l.getLastAction())
                .addValue("direction", l.getDirection())
                .addValue("localRole", l.getLocalRole())
                .addValue("initiatorSubscriberId", l.getInitiatorSubscriberId())
                .addValue("counterpartySubscriberId", l.getCounterpartySubscriberId())
                .addValue("contractType", l.getContractType())
                .addValue("serviceCategory", l.getServiceCategory())
                .addValue("targetBookingRef", l.getTargetBookingRef())
                .addValue("lastPayload", l.getLastPayload())
                .addValue("tenantId", l.getTenantId())
                .addValue("createdTime", l.getCreatedTime())
                .addValue("lastModifiedTime", l.getLastModifiedTime()));
    }

    public int updateState(String coordinationId, String lifecycleState, String lastAction, long modifiedTime) {
        return jdbc.update(UPDATE_STATE, new MapSqlParameterSource()
                .addValue("coordinationId", coordinationId)
                .addValue("lifecycleState", lifecycleState)
                .addValue("lastAction", lastAction)
                .addValue("lastModifiedTime", modifiedTime));
    }

    /** Returns the link for a coordinationId, or null if none (used to differentiate inbound vs outbound). */
    public CcnReferralLink findByCoordinationId(String coordinationId) {
        List<CcnReferralLink> rows = jdbc.query(SELECT_BY_ID,
                new MapSqlParameterSource("coordinationId", coordinationId), mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
