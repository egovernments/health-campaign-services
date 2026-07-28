package org.egov.referralmanagement.ccn.web;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only status lookup for the CCN correlation table ({@code ccn_referral_link}), for verifying
 * where a referral is in the Beckn lifecycle (both OUTBOUND and INBOUND). Any combination of the
 * query params narrows the result; {@code tenantId} targets one schema, otherwise it fans out over
 * the configured tenants.
 *
 * <p>Example:
 * {@code GET /referralmanagement/ccn/link/_search?tenantId=sierraleone&coordinationId=<id>}
 * or {@code ?tenantId=sierraleone&beneficiaryId=<SPICE_PATIENT_ID>}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/ccn/link")
public class CcnStatusController {

    private final CcnReferralLinkRepository linkRepository;

    public CcnStatusController(CcnReferralLinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @GetMapping("/_search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String coordinationId,
            @RequestParam(required = false) String referralId,
            @RequestParam(required = false) String referralClientReferenceId,
            @RequestParam(required = false) String beneficiaryId,
            @RequestParam(required = false) String direction) {
        List<CcnReferralLink> links = linkRepository.search(
                tenantId, coordinationId, referralId, referralClientReferenceId, beneficiaryId, direction);
        return ResponseEntity.ok(Map.of("count", links.size(), "CcnReferralLinks", links));
    }
}
