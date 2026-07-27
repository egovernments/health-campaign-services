package org.egov.referralmanagement.ccn.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Config for the CCN (NFH/Beckn) referral-forwarding flow. Bound from {@code referralmanagement.ccn.*}.
 *
 * <p>This flow is fully isolated from the rest of referralmanagement (Option A): it consumes the
 * existing create-HFReferral Kafka topic, maps HFReferral -> Beckn HealthReferral, and POSTs to the
 * ONIX adapter's /bap/caller/. ONIX does the signing, registry lookup, and routing to SPICE (BPP).</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "referralmanagement.ccn")
public class CcnProperties {

    /** Master switch. Default false so the flow is dormant until explicitly enabled. */
    private boolean enabled = false;

    /** ONIX BAP caller endpoint (in-cluster), e.g. http://onix-bap-hcm:8080/bap/caller/ */
    private String onixCallerUrl;

    /** Distinct Kafka consumer group — MUST differ from the persister's group so this
     *  consumer gets its own full copy of every referral (pub-sub fan-out). */
    private String consumerGroup = "referralmanagement-ccn-referral-forwarder";

    // ---- Beckn context values ----
    private String domain = "health";
    private String version = "2.0.0";
    private String networkId;

    /** Our published subscriber identity. */
    private String bapId;
    private String bapUri;

    /** SPICE/ComEMR identity (from Step 8 — placeholder until the NFO/SPICE confirm). */
    private String bppId;
    private String bppUri;

    // ---- HealthReferral @context URLs ----
    private String healthReferralCtx = "https://schema.beckn.io/HealthReferral/v2.1/context.jsonld";
    private String serviceCoordinationCtx = "https://schema.beckn.io/ServiceCoordination/v2.1/context.jsonld";
    private String healthParticipantCtx = "https://schema.beckn.io/HealthParticipant/v2.1/context.jsonld";
    private String codedValueCtx = "https://raw.githubusercontent.com/beckn/DHP-Specs/main/devkit/stub/context.jsonld";

    // ---- Coordination "slot" booked against SPICE (from SPICE's catalog) ----
    private String offerId = "offer-comemr-coord";
    private String resourceId = "res-comemr-coord";

    // ── Inbound BPP side (SPICE -> HCM receive flow, UC3) ──
    /** Master switch for the receive (BPP) flow. Default false — dormant until enabled. */
    private boolean bppEnabled = false;
    /** ONIX BPP caller endpoint for dispatching our on_* / update callbacks. */
    private String onixBppCallerUrl;
    /** HCM campaign field-capacity offer advertised on on_discover. */
    private String campaignCatalogId = "catalog-hcm-campaign";
    private String campaignOfferId = "offer-hcm-campaign-field";
    private String campaignResourceId = "res-hcm-campaign-field";
    /** Tenant the inbound Referral is created under. Fixed per node (this node = Sierra Leone);
     *  multi-tenant (per-tenant DeDi identity) is future work. */
    private String inboundTenantId;
    /** FALLBACK project only. The real project is resolved per-referral from the SPICE patientId
     *  via {@code InboundProjectResolver} (SPICE_PATIENT_ID → synced ProjectBeneficiary → its projectId).
     *  This default is used only when the patient has not been synced yet — Sierra Leone has many
     *  projects, so this must NOT be treated as the single project for all inbound referrals. */
    private String inboundProjectId;

    // ── Inbound completion → publish result back to SPICE ──
    /** Separate consumer group for the update-referral topic — distinct from the create-side group
     *  and from the persister, so completion detection gets its own pub-sub copy. */
    private String updateConsumerGroup = "referralmanagement-ccn-referral-completion";
    /** Sentinel value in Referral.reasons that marks a CHW-completed inbound referral. When an
     *  update to an INBOUND-linked referral carries this reason, we publish the result to SPICE. */
    private String completeReason = "TASK_COMPLETE";
    /** Lifecycle state published back to SPICE on completion. */
    private String closedState = "CLOSED";
}
