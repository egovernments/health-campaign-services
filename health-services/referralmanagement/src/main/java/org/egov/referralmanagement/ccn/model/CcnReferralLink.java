package org.egov.referralmanagement.ccn.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Correlation row linking a DIGIT HFReferral to its Beckn coordination on the NFH network.
 * ccn-owned (table {@code ccn_referral_link}); keeps the shared HFReferral model untouched.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CcnReferralLink {
    private String coordinationId;
    private String transactionId;
    private String hfReferralId;
    private String hfReferralClientReferenceId;
    private String beneficiaryId;
    private String lifecycleState;   // DRAFT -> ACTIVE -> ATTENDED -> ... -> CLOSED (from SPICE)
    private String lastAction;       // last Beckn action seen (confirm / on_confirm / on_status ...)
    private String tenantId;
    private Long createdTime;
    private Long lastModifiedTime;
}
