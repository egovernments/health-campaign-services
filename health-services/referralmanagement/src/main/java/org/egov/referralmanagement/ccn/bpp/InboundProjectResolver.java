package org.egov.referralmanagement.ccn.bpp;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves which HCM project an inbound (SPICE → HCM) referral belongs to.
 *
 * <p>Sierra Leone runs many projects, so the target project is <b>not</b> a fixed config value.
 * The inbound Beckn payload carries no geography — its only stable key is the SPICE patientId
 * (confirmed to be the same {@code SPICE_PATIENT_ID} we ingest from the household/member sync).
 * So we resolve by that identity:</p>
 *
 * <pre>SPICE_PATIENT_ID → ProjectBeneficiary (clientReferenceId == patientId) → its projectId</pre>
 *
 * <p>This reuses the sync half authoritatively (the beneficiary already knows its project) and
 * hands back the beneficiary link to attach to the created Referral. If the patient hasn't been
 * synced, we fall back to {@link CcnProperties#getInboundProjectId()} and flag it — and note that
 * {@code RmProjectBeneficiaryIdValidator} will then reject the create, which is the desired
 * loud-fail (we don't invent referrals for unknown patients).</p>
 */
@Slf4j
@Component
public class InboundProjectResolver {

    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration config;
    private final CcnProperties ccn;

    public InboundProjectResolver(ServiceRequestClient serviceRequestClient,
                                  ReferralManagementConfiguration config, CcnProperties ccn) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
        this.ccn = ccn;
    }

    /** Resolution of an inbound patient to an HCM project + beneficiary link. */
    @Getter
    public static class Resolution {
        private final String projectId;
        private final String projectBeneficiaryId;
        private final String projectBeneficiaryClientReferenceId;
        private final boolean resolved;

        public Resolution(String projectId, String pbId, String pbClientRef, boolean resolved) {
            this.projectId = projectId;
            this.projectBeneficiaryId = pbId;
            this.projectBeneficiaryClientReferenceId = pbClientRef;
            this.resolved = resolved;
        }
    }

    /**
     * Resolve the project for a SPICE patientId. Never throws — on any miss/error it returns the
     * configured fallback project so the caller can still attempt create() (and let validation decide).
     */
    public Resolution resolve(String spicePatientId, RequestInfo requestInfo) {
        String tenantId = ccn.getInboundTenantId();
        if (spicePatientId == null || spicePatientId.isBlank()) {
            log.warn("CCN BPP: inbound referral has no SPICE patientId; using fallback project {}", ccn.getInboundProjectId());
            return fallback(spicePatientId);
        }
        try {
            ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                    .clientReferenceId(List.of(spicePatientId))
                    .build();
            BeneficiaryBulkResponse resp = serviceRequestClient.fetchResult(
                    new StringBuilder(config.getProjectHost()
                            + config.getProjectBeneficiarySearchUrl()
                            + "?limit=1&offset=0&tenantId=" + tenantId),
                    BeneficiarySearchRequest.builder().requestInfo(requestInfo).projectBeneficiary(search).build(),
                    BeneficiaryBulkResponse.class);
            List<ProjectBeneficiary> found = resp != null ? resp.getProjectBeneficiaries() : null;
            if (found != null && !found.isEmpty()) {
                ProjectBeneficiary pb = found.get(0);
                log.info("CCN BPP: resolved SPICE patientId={} → project={} beneficiary={}",
                        spicePatientId, pb.getProjectId(), pb.getId());
                return new Resolution(pb.getProjectId(), pb.getId(), pb.getClientReferenceId(), true);
            }
            log.warn("CCN BPP: SPICE patientId={} not yet synced as a ProjectBeneficiary in tenant {}; "
                    + "using fallback project {} (create() will validate)", spicePatientId, tenantId, ccn.getInboundProjectId());
            return fallback(spicePatientId);
        } catch (Exception e) {
            log.error("CCN BPP: project resolution failed for patientId={}: {}; using fallback project {}",
                    spicePatientId, e.getMessage(), ccn.getInboundProjectId());
            return fallback(spicePatientId);
        }
    }

    private Resolution fallback(String spicePatientId) {
        return new Resolution(ccn.getInboundProjectId(), null, spicePatientId, false);
    }
}
