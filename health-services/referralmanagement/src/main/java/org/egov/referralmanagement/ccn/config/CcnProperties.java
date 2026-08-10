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

    /** healthId.system used for the PATIENT participant. SPICE performs its patient lookup on this
     *  string (they use "ABHA"); the value carried is the national id (projectBeneficiaryClientReferenceId). */
    private String patientHealthIdSystem = "ABHA";

    /** idgen-generated identifier type on the Individual that is the ONE canonical patient identity —
     *  a 13-digit value drawn from the beneficiary id-pool. This value (not the project-beneficiary
     *  clientReferenceId) is what we send as the ABHA healthId outbound and the sole match key inbound. */
    private String patientIdentifierType = "UNIQUE_BENEFICIARY_ID";

    /** Key under {@code Referral.additionalFields} where the resolved canonical id is cached/stamped.
     *  NOTE: the DIGIT Referral model has no free-form {@code additionalDetails} JsonNode; it carries an
     *  {@code additionalFields} (schema/version + List&lt;Field key,value&gt;). We stamp under this key there. */
    private String abhaAdditionalDetailsKey = "abhaId";

    /** Key under {@code Referral.additionalFields} where the patient's display name is cached/stamped.
     *  Name is NOT treated as PII for this integration — it is sent outbound as the PATIENT participant
     *  {@code descriptor.name} so CCN/SPICE operators can see who the referral is for. Still no other
     *  demographics (DOB, address, clinical notes) cross the network. */
    private String patientNameAdditionalKey = "patientName";

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
    /** Staff (project-staff user uuid) the inbound Referral is assigned to as recipient. The referral
     *  create validates recipientType=STAFF against project staff, so this must be a valid staff on
     *  the inbound project. */
    private String inboundRecipientId;

    // ── Inbound HFReferral autofill (so a SPICE referral also shows in the app's "Referral Details"
    //     HFReferral screen, not just the Referral entity) ──
    /** projectFacilityId assigned to inbound HFReferrals. MUST be a valid project-facility id on the
     *  project (validated by HfrProjectFacilityIdValidator). Blank → HFReferral creation is skipped. */
    private String inboundHfFacilityId;
    /** projectId for inbound HFReferrals. MUST equal the project the CHW downloads so the HFReferral
     *  downsyncs (downsync filters HFReferrals by projectId). Falls back to the resolved project when blank. */
    private String inboundHfProjectId;
    /** symptom / referralReason code stamped on inbound HFReferrals — MUST be a valid
     *  HCM-REFERRAL-REASONS code (e.g. FEVER, SICK) so the app can render/localise it. */
    private String inboundHfSymptom = "SICK";
    /** Current project cycle to prefill on inbound HFReferrals (hardcoded for the demo). */
    private String inboundHfCycle = "2";
    /** Gender stamped on inbound HFReferrals. Hardcoded FEMALE for the demo (valid genderConfig code). */
    private String inboundHfGender = "FEMALE";
    /** additionalFields key that tags an HFReferral as inbound-originated. The outbound CCN consumer
     *  skips any HFReferral carrying this marker, so an inbound referral is never bounced back to SPICE. */
    private String inboundHfMarkerKey = "ccnInbound";

    // ── Inbound completion → publish result back to SPICE ──
    /** Separate consumer group for the update-referral topic — distinct from the create-side group
     *  and from the persister, so completion detection gets its own pub-sub copy. */
    private String updateConsumerGroup = "referralmanagement-ccn-referral-completion";
    /** Sentinel value in Referral.reasons that marks a CHW-completed inbound referral. When an
     *  update to an INBOUND-linked referral carries this reason, we publish the result to SPICE. */
    private String completeReason = "TASK_COMPLETE";
    /** Lifecycle state published back to SPICE on completion. */
    private String closedState = "CLOSED";

    // ── Referral status lifecycle (HFReferral-only; mirrored to additionalFields[referralStatus]) ──
    /** additionalFields key on the HFReferral holding the app-facing referral status. */
    private String referralStatusKey = "referralStatus";
    /** additionalFields key holding the free-text reason the worker gives (e.g. rejection reason).
     *  Stored on the HFReferral and forwarded to SPICE alongside the status. */
    private String referralStatusReasonKey = "referralStatusReason";
    /** Status stamped on an inbound HFReferral when SPICE first sends it (before the HF worker acts). */
    private String inboundInitialStatus = "RECEIVED";
    /** SPICE lifecycleStates (from on_status/on_update on an OUTBOUND referral) that we mirror onto the
     *  HFReferral's {@code referralStatus} so the CHW app sees the outcome. Comma-separated, UPPERCASE.
     *  Placeholder set until SPICE confirms the exact codes (task: pull from DeDi). */
    private String mirroredStates = "ACCEPTED,REJECTED,COMPLETED,CANCELLED,BOOKING_CONFIRMED";
    /** referralStatus values that, when set on an INBOUND HFReferral (HF worker accept/reject/resolve),
     *  we forward to SPICE via the Beckn {@code update} action. Comma-separated, UPPERCASE. */
    private String forwardableStatuses = "ACCEPTED,REJECTED,RESOLVED,COMPLETED,CANCELLED";
    /** Topic pattern for HFReferral updates (the entity the app updates on accept/reject/resolve). */
    private String hfUpdateTopicPattern = ".*update-hfreferral-topic";
    /** Consumer group for the HFReferral-update CCN listener — own pub-sub copy, never steals from persister. */
    private String hfUpdateConsumerGroup = "referralmanagement-ccn-hfreferral-status";
    /** Beckn action used to push HCM-initiated (inbound accept/reject/resolve) status changes back to SPICE. */
    private String backAction = "on_update";
    /** Fallback/default {@code contract.status.code} when a status has no mapping in
     *  {@link #outboundStatusCodeMap}. NOTE (corrected, verified live): CCN's {@code contract.status.code}
     *  enum is actually {@code DRAFT, ACTIVE, CANCELLED, COMPLETE} — terminal outcomes CAN be sent here
     *  (the commitment {@code descriptor.code} is the field limited to {@code DRAFT, ACTIVE, CLOSED}).
     *  CCN displays the referral from {@code contract.status.code}, so we now map the real outcome into it. */
    private String wireStatusCode = "ACTIVE";
    /** HCM referralStatus / CCN lifecycle value → wire {@code contract.status.code}
     *  (enum DRAFT/ACTIVE/CANCELLED/COMPLETE). Comma-separated {@code KEY:VALUE}, UPPERCASE. Covers both
     *  raw referralStatus (REJECTED/ACCEPTED/…) and already-mapped lifecycle values (CANCELLED/COMPLETED). */
    private String outboundStatusCodeMap =
            "RECEIVED:ACTIVE,ACCEPTED:ACTIVE,ACTIVE:ACTIVE,DRAFT:DRAFT,"
            + "REJECTED:CANCELLED,CANCELLED:CANCELLED,"
            + "RESOLVED:COMPLETE,COMPLETED:COMPLETE,COMPLETE:COMPLETE,CLOSED:COMPLETE";
    /** Wire {@code status.code} values that are terminal → commitment {@code descriptor.code} must be
     *  {@code CLOSED} (its enum is DRAFT/ACTIVE/CLOSED). Non-terminal wire codes map descriptor 1:1. */
    private String terminalWireStatusCodes = "CANCELLED,COMPLETE";
    /** Inbound: wire {@code contract.status.code} → the HCM referralStatus stored on the record. */
    private String inboundStatusCodeMap =
            "DRAFT:RECEIVED,ACTIVE:RECEIVED,CANCELLED:CANCELLED,COMPLETE:COMPLETED,CLOSED:COMPLETED";
    /** {@code targetCriteria.serviceCategory.code} — CCN requires this and only allows
     *  ADMISSION/CONSULTATION/INVESTIGATION/PROCEDURE (verified live). */
    private String wireServiceCategory = "CONSULTATION";
    /** Maps each HCM referralStatus → the {@code contractAttributes.lifecycleState} value sent to SPICE
     *  ({@code referralStatus:lifecycleState}, comma-separated, UPPERCASE). Per SPICE: reject AND cancel
     *  both go out as CANCELLED. Unmapped passes through unchanged. */
    private String outboundLifecycleMap =
            "ACCEPTED:ACCEPTED,REJECTED:CANCELLED,CANCELLED:CANCELLED,RESOLVED:COMPLETED,COMPLETED:COMPLETED,CLOSED:COMPLETED";
    /** System user stamped on backend-initiated HFReferral writes (e.g. the SPICE→HCM status mirror).
     *  The status consumer skips push-back for updates made by this user so a mirror never bounces
     *  back to SPICE — only genuine app/worker status changes flow out. */
    private String systemUser = "ccn-system";
}
