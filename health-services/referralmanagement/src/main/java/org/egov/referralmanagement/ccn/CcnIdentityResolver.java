package org.egov.referralmanagement.ccn;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the ONE canonical patient identity used across the CCN integration: the Individual's
 * idgen-generated {@code UNIQUE_BENEFICIARY_ID} identifier (a 13-digit id from the beneficiary
 * id-pool). That value is the healthId {@code value} sent outbound (system stays ABHA) and the sole
 * inbound match key.
 *
 * <p>The DIGIT {@link Referral} has no free-form {@code additionalDetails} JsonNode; it carries an
 * {@code additionalFields} bag (schema/version + a {@code List<Field>} of key/value). We cache/stamp
 * the resolved id under {@link CcnProperties#getAbhaAdditionalDetailsKey()} in that bag.</p>
 *
 * <p>Reuses the existing {@link ServiceRequestClient} POST-search pattern (same as
 * {@code InboundProjectResolver}) against the project-beneficiary and individual services.</p>
 */
@Slf4j
@Component
public class CcnIdentityResolver {

    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration config;
    private final CcnProperties ccn;

    public CcnIdentityResolver(ServiceRequestClient serviceRequestClient,
                               ReferralManagementConfiguration config, CcnProperties ccn) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.ccn = ccn;
    }

    // ── additionalFields (abhaId) read/write ──────────────────────────────────

    /** Read the cached canonical id from {@code referral.additionalFields[abhaAdditionalDetailsKey]}. */
    public String readAbhaField(Referral referral) {
        if (referral == null) return null;
        AdditionalFields af = referral.getAdditionalFields();
        if (af == null || af.getFields() == null) return null;
        String key = ccn.getAbhaAdditionalDetailsKey();
        return af.getFields().stream()
                .filter(f -> f != null && key.equals(f.getKey()))
                .map(Field::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    /** Stamp the canonical id into {@code referral.additionalFields[abhaAdditionalDetailsKey]} (idempotent). */
    public void writeAbhaField(Referral referral, String value) {
        if (referral == null || value == null || value.isBlank()) return;
        String key = ccn.getAbhaAdditionalDetailsKey();
        AdditionalFields af = referral.getAdditionalFields();
        if (af == null) {
            af = AdditionalFields.builder().build();
            referral.setAdditionalFields(af);
        }
        if (af.getFields() == null) af.setFields(new ArrayList<>());
        for (Field f : af.getFields()) {
            if (f != null && key.equals(f.getKey())) { f.setValue(value); return; }
        }
        af.getFields().add(Field.builder().key(key).value(value).build());
    }

    // ── additionalFields (patientName) read/write ─────────────────────────────

    /** Read the cached patient display name from {@code referral.additionalFields[patientNameAdditionalKey]}. */
    public String readNameField(Referral referral) {
        if (referral == null) return null;
        AdditionalFields af = referral.getAdditionalFields();
        if (af == null || af.getFields() == null) return null;
        String key = ccn.getPatientNameAdditionalKey();
        return af.getFields().stream()
                .filter(f -> f != null && key.equals(f.getKey()))
                .map(Field::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    /** Stamp the patient display name into {@code referral.additionalFields[patientNameAdditionalKey]}. */
    public void writeNameField(Referral referral, String value) {
        if (referral == null || value == null || value.isBlank()) return;
        String key = ccn.getPatientNameAdditionalKey();
        AdditionalFields af = referral.getAdditionalFields();
        if (af == null) {
            af = AdditionalFields.builder().build();
            referral.setAdditionalFields(af);
        }
        if (af.getFields() == null) af.setFields(new ArrayList<>());
        for (Field f : af.getFields()) {
            if (f != null && key.equals(f.getKey())) { f.setValue(value); return; }
        }
        af.getFields().add(Field.builder().key(key).value(value).build());
    }

    /** The Individual's display name ("givenName familyName", trimmed) or null. */
    public String displayNameOf(Individual individual) {
        if (individual == null || individual.getName() == null) return null;
        String given = individual.getName().getGivenName();
        String family = individual.getName().getFamilyName();
        String name = ((given != null ? given : "") + " " + (family != null ? family : "")).trim();
        return name.isBlank() ? null : name;
    }

    /**
     * Resolve the patient's display name to send outbound (non-PII per integration rule). Order:
     * cached {@code additionalFields[patientName]} → project-beneficiary → individual name;
     * fallback to individual-by-{@code UNIQUE_BENEFICIARY_ID}. Caches back and never throws.
     */
    public String resolveOutboundName(Referral referral, RequestInfo requestInfo) {
        if (referral == null) return null;
        String cached = readNameField(referral);
        if (cached != null && !cached.isBlank()) return cached;
        String tenantId = referral.getTenantId() != null ? referral.getTenantId() : ccn.getInboundTenantId();
        try {
            Individual individual = resolveIndividualForReferral(referral, requestInfo);
            if (individual == null) {
                String abha = readAbhaField(referral);
                if (abha != null && !abha.isBlank())
                    individual = findIndividualByUniqueBeneficiaryId(abha, tenantId, requestInfo);
            }
            String name = displayNameOf(individual);
            if (name != null && !name.isBlank()) {
                writeNameField(referral, name);
                return name;
            }
        } catch (Exception e) {
            log.error("CCN identity: outbound name resolve failed for referral {}: {}", referral.getId(), e.getMessage());
        }
        return null;
    }

    /** The {@code UNIQUE_BENEFICIARY_ID} identifier value on an Individual, or null. */
    public String uniqueBeneficiaryIdOf(Individual individual) {
        if (individual == null || individual.getIdentifiers() == null) return null;
        String type = ccn.getPatientIdentifierType();
        return individual.getIdentifiers().stream()
                .filter(i -> i != null && type.equals(i.getIdentifierType()))
                .map(Identifier::getIdentifierId)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    // ── Outbound: resolve the canonical ABHA value for a referral ─────────────

    /**
     * Resolve the canonical patient id to send as the ABHA healthId value:
     * <ol>
     *   <li>if already cached in {@code referral.additionalFields[abhaId]} → use it;</li>
     *   <li>else resolve the Individual (project beneficiary → individual), read its
     *       {@code UNIQUE_BENEFICIARY_ID}, cache it back into additionalFields, and use it.</li>
     * </ol>
     * Returns null when it cannot be resolved (caller decides whether to skip forwarding).
     * Never throws — search failures are logged and treated as "not resolved".
     */
    public String resolveOutboundAbha(Referral referral, RequestInfo requestInfo) {
        if (referral == null) return null;
        String cached = readAbhaField(referral);
        if (cached != null && !cached.isBlank()) return cached;

        try {
            Individual individual = resolveIndividualForReferral(referral, requestInfo);
            String ubid = uniqueBeneficiaryIdOf(individual);
            if (ubid != null && !ubid.isBlank()) {
                writeAbhaField(referral, ubid);
                log.info("CCN identity: resolved referral {} → individual {} UNIQUE_BENEFICIARY_ID={}",
                        referral.getId(), individual != null ? individual.getId() : null, ubid);
                return ubid;
            }
            log.warn("CCN identity: no {} identifier for referral {} (beneficiaryId={}, clientRef={})",
                    ccn.getPatientIdentifierType(), referral.getId(),
                    referral.getProjectBeneficiaryId(), referral.getProjectBeneficiaryClientReferenceId());
        } catch (Exception e) {
            log.error("CCN identity: outbound resolve failed for referral {}: {}", referral.getId(), e.getMessage());
        }
        return null;
    }

    private Individual resolveIndividualForReferral(Referral referral, RequestInfo ri) {
        String tenantId = referral.getTenantId() != null ? referral.getTenantId() : ccn.getInboundTenantId();
        ProjectBeneficiary pb = searchBeneficiary(
                referral.getProjectBeneficiaryId(), referral.getProjectBeneficiaryClientReferenceId(), tenantId, ri);
        if (pb == null) return null;
        return searchIndividual(pb.getBeneficiaryId(), pb.getBeneficiaryClientReferenceId(), tenantId, ri);
    }

    // ── Inbound: find the Individual holding this canonical id ────────────────

    /** Find the Individual whose {@code UNIQUE_BENEFICIARY_ID} identifier equals the incoming id, or null. */
    public Individual findIndividualByUniqueBeneficiaryId(String abhaId, String tenantId, RequestInfo ri) {
        if (abhaId == null || abhaId.isBlank()) return null;
        try {
            IndividualSearch search = IndividualSearch.builder()
                    .identifier(Identifier.builder()
                            .identifierType(ccn.getPatientIdentifierType())
                            .identifierId(abhaId)
                            .build())
                    .build();
            IndividualBulkResponse resp = serviceRequestClient.fetchResult(
                    new StringBuilder(config.getIndividualHost()
                            + config.getIndividualSearchUrl()
                            + "?limit=1&offset=0&tenantId=" + tenantId),
                    IndividualSearchRequest.builder().requestInfo(ri).individual(search).build(),
                    IndividualBulkResponse.class);
            List<Individual> found = resp != null ? resp.getIndividual() : null;
            if (found != null && !found.isEmpty()) return found.get(0);
        } catch (Exception e) {
            log.error("CCN identity: individual-by-identifier search failed for {}={}: {}",
                    ccn.getPatientIdentifierType(), abhaId, e.getMessage());
        }
        return null;
    }

    // ── shared HCM searches ───────────────────────────────────────────────────

    private ProjectBeneficiary searchBeneficiary(String id, String clientRef, String tenantId, RequestInfo ri) {
        if ((id == null || id.isBlank()) && (clientRef == null || clientRef.isBlank())) return null;
        try {
            ProjectBeneficiarySearch.ProjectBeneficiarySearchBuilder b = ProjectBeneficiarySearch.builder();
            if (id != null && !id.isBlank()) b.id(List.of(id));
            else b.clientReferenceId(List.of(clientRef));
            BeneficiaryBulkResponse resp = serviceRequestClient.fetchResult(
                    new StringBuilder(config.getProjectHost()
                            + config.getProjectBeneficiarySearchUrl()
                            + "?limit=1&offset=0&tenantId=" + tenantId),
                    BeneficiarySearchRequest.builder().requestInfo(ri).projectBeneficiary(b.build()).build(),
                    BeneficiaryBulkResponse.class);
            List<ProjectBeneficiary> found = resp != null ? resp.getProjectBeneficiaries() : null;
            return found != null && !found.isEmpty() ? found.get(0) : null;
        } catch (Exception e) {
            log.error("CCN identity: beneficiary search failed (id={}, clientRef={}): {}", id, clientRef, e.getMessage());
            return null;
        }
    }

    private Individual searchIndividual(String id, String clientRef, String tenantId, RequestInfo ri) {
        if ((id == null || id.isBlank()) && (clientRef == null || clientRef.isBlank())) return null;
        try {
            IndividualSearch.IndividualSearchBuilder<?, ?> b = IndividualSearch.builder();
            if (id != null && !id.isBlank()) b.id(List.of(id));
            else b.clientReferenceId(List.of(clientRef));
            IndividualBulkResponse resp = serviceRequestClient.fetchResult(
                    new StringBuilder(config.getIndividualHost()
                            + config.getIndividualSearchUrl()
                            + "?limit=1&offset=0&tenantId=" + tenantId),
                    IndividualSearchRequest.builder().requestInfo(ri).individual(b.build()).build(),
                    IndividualBulkResponse.class);
            List<Individual> found = resp != null ? resp.getIndividual() : null;
            return found != null && !found.isEmpty() ? found.get(0) : null;
        } catch (Exception e) {
            log.error("CCN identity: individual search failed (id={}, clientRef={}): {}", id, clientRef, e.getMessage());
            return null;
        }
    }
}
