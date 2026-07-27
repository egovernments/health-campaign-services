package org.egov.referralmanagement.ccn.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Correlation row linking a DIGIT Referral to its Beckn coordination on the NFH network.
 * One row per coordination, BOTH directions (see {@code direction}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CcnReferralLink {
    public static final String OUTBOUND = "OUTBOUND"; // we started it (HCM BAP -> SPICE)
    public static final String INBOUND  = "INBOUND";  // came from another system (SPICE BAP -> HCM BPP)

    private String coordinationId;
    private String transactionId;
    private String hfReferralId;                  // originating referral id (out) OR the Referral we create (in)
    private String hfReferralClientReferenceId;
    private String beneficiaryId;                 // the SPICE patientId on the wire
    private String lifecycleState;                // DRAFT -> ACTIVE -> ATTENDED -> ... -> CLOSED
    private String lastAction;                    // last Beckn action seen

    // ── direction / origin (differentiates our-initiated vs incoming) ──
    private String direction;                     // OUTBOUND | INBOUND
    private String localRole;                     // BAP (outbound) | BPP (inbound)
    private String initiatorSubscriberId;         // context.bapId — the origin system
    private String counterpartySubscriberId;      // the other party
    private String contractType;                  // HealthReferral | ServiceCoordination
    private String serviceCategory;               // e.g. FIELD_DATA_COLLECTION
    private String targetBookingRef;              // escalation link (ADR-0002)
    private String lastPayload;                   // last raw message (audit/replay)

    private String tenantId;
    private Long createdTime;
    private Long lastModifiedTime;
}
