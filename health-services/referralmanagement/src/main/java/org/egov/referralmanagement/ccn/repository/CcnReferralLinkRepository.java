package org.egov.referralmanagement.ccn.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

/**
 * JDBC repository for {@code ccn_referral_link} (both directions). The table lives in the
 * <b>tenant schema</b> (schema-per-tenant, central-instance), so every query goes through
 * {@link MultiStateInstanceUtil#replaceSchemaPlaceholder} to target the right schema.
 *
 * <p>The forward flow, inbound (BPP) flow and the update-consumer all know the tenant. The
 * outbound Beckn callbacks ({@code on_*}) arrive with only a coordinationId and no tenant, so
 * those calls pass {@code tenantId == null} and we fan out over {@code referralmanagement.ccn.tenants}
 * (coordinationId is unique, so at most one schema has the row).</p>
 */
@Slf4j
@Repository
public class CcnReferralLinkRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final MultiStateInstanceUtil multiStateInstanceUtil;
    /** Tenants to fan out over when a callback arrives with no tenant context (outbound on_*). */
    private final List<String> fanoutTenants;

    public CcnReferralLinkRepository(NamedParameterJdbcTemplate jdbc,
                                     MultiStateInstanceUtil multiStateInstanceUtil,
                                     @Value("${referralmanagement.ccn.tenants:}") String tenantsCsv) {
        this.jdbc = jdbc;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
        this.fanoutTenants = StringUtils.hasText(tenantsCsv)
                ? Arrays.stream(tenantsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
    }

    private static final String UPSERT_TMPL =
            "INSERT INTO %s.ccn_referral_link (coordination_id, transaction_id, hf_referral_id, " +
            "hf_referral_client_reference_id, beneficiary_id, lifecycle_state, last_action, direction, " +
            "local_role, initiator_subscriber_id, counterparty_subscriber_id, contract_type, service_category, " +
            "target_booking_ref, last_payload, tenant_id, created_time, last_modified_time) VALUES " +
            "(:coordinationId, :transactionId, :hfReferralId, :hfReferralClientReferenceId, :beneficiaryId, " +
            ":lifecycleState, :lastAction, :direction, :localRole, :initiatorSubscriberId, :counterpartySubscriberId, " +
            ":contractType, :serviceCategory, :targetBookingRef, :lastPayload, :tenantId, :createdTime, :lastModifiedTime) " +
            "ON CONFLICT (coordination_id) DO UPDATE SET lifecycle_state = EXCLUDED.lifecycle_state, " +
            "last_action = EXCLUDED.last_action, target_booking_ref = COALESCE(EXCLUDED.target_booking_ref, %1$s.ccn_referral_link.target_booking_ref), " +
            "last_payload = EXCLUDED.last_payload, last_modified_time = EXCLUDED.last_modified_time";

    private static final String UPDATE_STATE_TMPL =
            "UPDATE %s.ccn_referral_link SET lifecycle_state = :lifecycleState, last_action = :lastAction, " +
            "last_modified_time = :lastModifiedTime WHERE coordination_id = :coordinationId";

    private static final String SELECT_BY_ID_TMPL =
            "SELECT * FROM %s.ccn_referral_link WHERE coordination_id = :coordinationId";

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
            .createdTime(rs.getObject("created_time") == null ? null : rs.getLong("created_time"))
            .lastModifiedTime(rs.getObject("last_modified_time") == null ? null : rs.getLong("last_modified_time"))
            .build();

    /** Resolve the tenant schema into the query. */
    private String schema(String tmpl, String tenantId) {
        try {
            return multiStateInstanceUtil.replaceSchemaPlaceholder(String.format(tmpl, SCHEMA_REPLACE_STRING), tenantId);
        } catch (InvalidTenantIdException e) {
            throw new IllegalArgumentException("CCN link: invalid tenantId " + tenantId, e);
        }
    }

    public void save(CcnReferralLink l) {
        jdbc.update(schema(UPSERT_TMPL, l.getTenantId()), new MapSqlParameterSource()
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

    /** Update lifecycle. Pass tenantId when known; pass null on outbound callbacks to fan out. */
    public int updateState(String coordinationId, String lifecycleState, String lastAction, long modifiedTime, String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("coordinationId", coordinationId)
                .addValue("lifecycleState", lifecycleState)
                .addValue("lastAction", lastAction)
                .addValue("lastModifiedTime", modifiedTime);
        int total = 0;
        for (String t : tenantsToTry(tenantId)) {
            try {
                total += jdbc.update(schema(UPDATE_STATE_TMPL, t), p);
            } catch (Exception e) {
                log.debug("CCN updateState fanout tenant {} skipped: {}", t, e.getMessage());
            }
        }
        return total;
    }

    /** Find the link. Pass tenantId when known; pass null on outbound callbacks to fan out. */
    public CcnReferralLink findByCoordinationId(String coordinationId, String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource("coordinationId", coordinationId);
        for (String t : tenantsToTry(tenantId)) {
            try {
                List<CcnReferralLink> rows = jdbc.query(schema(SELECT_BY_ID_TMPL, t), p, mapper);
                if (!rows.isEmpty()) return rows.get(0);
            } catch (Exception e) {
                log.debug("CCN findByCoordinationId fanout tenant {} skipped: {}", t, e.getMessage());
            }
        }
        return null;
    }

    private List<String> tenantsToTry(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) return List.of(tenantId);
        return new ArrayList<>(fanoutTenants);
    }

    /**
     * Status search: any combination of coordinationId / referralId (hf_referral_id) /
     * referralClientReferenceId / beneficiaryId (SPICE patientId) / direction. tenantId narrows to
     * one schema; if null, fans out over the configured tenants.
     */
    public List<CcnReferralLink> search(String tenantId, String coordinationId, String referralId,
                                        String referralClientReferenceId, String beneficiaryId, String direction) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (coordinationId != null && !coordinationId.isBlank()) { where.append(" AND coordination_id = :cid"); p.addValue("cid", coordinationId); }
        if (referralId != null && !referralId.isBlank()) { where.append(" AND hf_referral_id = :rid"); p.addValue("rid", referralId); }
        if (referralClientReferenceId != null && !referralClientReferenceId.isBlank()) { where.append(" AND hf_referral_client_reference_id = :rcid"); p.addValue("rcid", referralClientReferenceId); }
        if (beneficiaryId != null && !beneficiaryId.isBlank()) { where.append(" AND beneficiary_id = :bid"); p.addValue("bid", beneficiaryId); }
        if (direction != null && !direction.isBlank()) { where.append(" AND direction = :dir"); p.addValue("dir", direction); }
        String tmpl = "SELECT * FROM %s.ccn_referral_link" + where + " ORDER BY last_modified_time DESC";
        List<CcnReferralLink> out = new ArrayList<>();
        for (String t : tenantsToTry(tenantId)) {
            try {
                out.addAll(jdbc.query(schema(tmpl, t), p, mapper));
            } catch (Exception e) {
                log.debug("CCN search fanout tenant {} skipped: {}", t, e.getMessage());
            }
        }
        return out;
    }
}
