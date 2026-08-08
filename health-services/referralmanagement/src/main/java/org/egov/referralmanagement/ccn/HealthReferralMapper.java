package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.egov.common.models.core.Field;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Maps a DIGIT {@link Referral} (the normal referral flow) to Beckn {@code HealthReferral} payloads
 * (select / init / confirm / status) for the HCM → SPICE (via CC) downward flow.
 *
 * <p><b>Non-sensitive by design.</b> Per the network rule, we send only coordination keys — the SPICE
 * {@code patientId} (carried on the referral's beneficiary/individual id field) and the coded
 * downward intent. NO names, gender, DOB or free-text clinical notes cross the network. villageId /
 * lat-long are omitted until SPICE provides a field (see task A).</p>
 */
@Component
public class HealthReferralMapper {

    private final CcnProperties p;
    private final ObjectMapper om;

    public HealthReferralMapper(CcnProperties p, @Qualifier("objectMapper") ObjectMapper om) {
        this.p = p;
        this.om = om;
    }

    /** SPICE patientId is carried on the referral's beneficiary/individual id (app sets it for reconciled patients). */
    public static String spicePatientId(Referral r) {
        if (r.getProjectBeneficiaryClientReferenceId() != null && !r.getProjectBeneficiaryClientReferenceId().isBlank())
            return r.getProjectBeneficiaryClientReferenceId();
        return r.getProjectBeneficiaryId();
    }

    private ObjectNode context(String action, String transactionId) {
        ObjectNode c = om.createObjectNode();
        c.put("networkId", p.getNetworkId());
        c.put("action", action);
        c.put("version", p.getVersion());
        c.put("bapId", p.getBapId());
        c.put("bapUri", p.getBapUri());
        c.put("bppId", p.getBppId());
        c.put("bppUri", p.getBppUri());
        c.put("transactionId", transactionId);
        c.put("messageId", UUID.randomUUID().toString());
        c.put("timestamp", OffsetDateTime.now().toString());
        return c;
    }

    private void addCommitments(ObjectNode contract, String statusCode) {
        ObjectNode cm = contract.putArray("commitments").addObject();
        cm.put("id", "commitment-001");
        cm.putObject("status").putObject("descriptor").put("code", statusCode);
        ObjectNode res = cm.putArray("resources").addObject();
        res.put("id", p.getResourceId());
        res.putObject("quantity").put("count", 1);
        ObjectNode offer = cm.putObject("offer");
        offer.put("id", p.getOfferId());
        offer.putArray("resourceIds").add(p.getResourceId());
    }

    /** The patient's display name stamped on the referral (non-PII per integration rule), or null. */
    private String patientName(Referral r) {
        if (r == null || r.getAdditionalFields() == null || r.getAdditionalFields().getFields() == null) return null;
        String key = p.getPatientNameAdditionalKey();
        return r.getAdditionalFields().getFields().stream()
                .filter(f -> f != null && key.equals(f.getKey()))
                .map(Field::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst().orElse(null);
    }

    /** Participants: PATIENT (SPICE patientId + display name) + referrer CHW (id only). Name is the only
     *  demographic that crosses — no DOB/address/clinical notes. */
    private void addParticipants(ObjectNode contract, Referral r, String spicePatientId) {
        ArrayNode arr = contract.putArray("participants");

        ObjectNode patient = arr.addObject();
        patient.put("id", "participant-patient");
        String name = patientName(r);
        if (name != null) {
            patient.putObject("descriptor").put("name", name);   // visible in CCN; name is not treated as PII
        }
        ObjectNode pa = patient.putObject("participantAttributes");
        pa.put("@context", p.getHealthParticipantCtx());
        pa.put("@type", "hpa:HealthParticipant");
        pa.put("participantRole", "PATIENT");
        ObjectNode hid = pa.putArray("healthIds").addObject();
        hid.put("system", p.getPatientHealthIdSystem());   // "ABHA" — SPICE looks up the patient by this system
        hid.put("value", spicePatientId);

        if (r.getReferrerId() != null) {
            ObjectNode chw = arr.addObject();
            chw.put("id", "participant-referrer");
            ObjectNode ca = chw.putObject("participantAttributes");
            ca.put("@context", p.getHealthParticipantCtx());
            ca.put("@type", "hpa:HealthParticipant");
            ca.put("participantRole", "CARE_GIVER");   // community health/campaign worker
            ObjectNode chid = ca.putArray("healthIds").addObject();
            chid.put("system", "HCM_USER_ID");
            chid.put("value", r.getReferrerId());
        }
    }

    private ObjectNode contractAttributes(Referral r, String coordinationId, String lifecycleState) {
        boolean urgent = r.getReasons() != null && !r.getReasons().isEmpty();
        ObjectNode ca = om.createObjectNode();
        ca.put("@context", p.getHealthReferralCtx());
        ca.put("@type", "hrf:HealthReferral");
        ca.put("coordinationId", coordinationId);
        ca.put("lifecycleState", lifecycleState);
        ca.put("clinicalUrgencyTier", urgent ? "URGENT" : "ROUTINE");
        ca.put("healthServiceType", "PHYSICAL_CONSULTATION");
        ObjectNode tc = ca.putObject("targetCriteria");
        ObjectNode sc = tc.putObject("serviceCategory");
        sc.put("@context", p.getCodedValueCtx());
        sc.put("@type", "ServiceCategory");
        sc.put("code", "CONSULTATION");
        sc.put("display", "Consultation");
        tc.putArray("procedureNeeds").add("HOME_VISIT");   // downward marker → CC forwards to SPICE
        tc.put("consultationModality", "IN_PERSON");
        return ca;
    }

    private ObjectNode base(String action, String txnId, String coordinationId, String statusCode,
                            Referral r, String spicePatientId, boolean withParticipants) {
        ObjectNode root = om.createObjectNode();
        root.set("context", context(action, txnId));
        ObjectNode contract = root.putObject("message").putObject("contract");
        if (!"select".equals(action)) contract.put("id", coordinationId);
        contract.putObject("status").put("code", statusCode);
        if (r.getReferralCode() != null) {
            contract.putObject("descriptor").put("name", r.getReferralCode());  // cross-ref only (non-PII)
        }
        addCommitments(contract, statusCode);
        if (withParticipants) addParticipants(contract, r, spicePatientId);
        contract.set("contractAttributes", contractAttributes(r, coordinationId, statusCode.equals("ACTIVE") ? "ACTIVE" : "DRAFT"));
        return root;
    }

    public ObjectNode select(Referral r, String txnId, String coordinationId, String spicePatientId) {
        return base("select", txnId, coordinationId, "DRAFT", r, spicePatientId, true);
    }

    public ObjectNode init(Referral r, String txnId, String coordinationId, String spicePatientId) {
        return base("init", txnId, coordinationId, "DRAFT", r, spicePatientId, true);
    }

    public ObjectNode confirm(Referral r, String txnId, String coordinationId, String spicePatientId) {
        return base("confirm", txnId, coordinationId, "ACTIVE", r, spicePatientId, true);
    }

    /** status: query by coordinationId. The Beckn Contract schema requires commitments
     *  (minItems 1), so include them even on a status query — SPICE NACKs a bare contract. */
    public ObjectNode status(Referral r, String txnId, String coordinationId, String spicePatientId) {
        ObjectNode root = om.createObjectNode();
        root.set("context", context("status", txnId));
        ObjectNode contract = root.putObject("message").putObject("contract");
        contract.put("id", coordinationId);
        contract.putObject("status").put("code", "ACTIVE");
        addCommitments(contract, "ACTIVE");
        return root;
    }

    /**
     * update: HCM (BAP) pushes a lifecycle change on an OUTBOUND referral to SPICE — e.g. the CHW
     * cancels a referral they raised. Fire-and-store: SPICE may or may not act; whatever it returns on
     * on_update is mirrored back onto the HFReferral, and HCM does not re-act. Self-contained (no
     * Referral needed) so it can be driven straight from the coordination link. {@code reason} (nullable)
     * is the worker's free-text note, carried on the status descriptor + contractAttributes.
     */
    public ObjectNode update(String txnId, String coordinationId, String spicePatientId,
                             String ccnLifecycleState, String reason) {
        ObjectNode root = om.createObjectNode();
        root.set("context", context("update", txnId));
        ObjectNode contract = root.putObject("message").putObject("contract");
        contract.put("id", coordinationId);
        // status.code must be a wire code CCN accepts (only DRAFT/ACTIVE pass) — the real outcome goes
        // in contractAttributes.lifecycleState. descriptor is not allowed under contract.status.
        contract.putObject("status").put("code", p.getWireStatusCode());
        addCommitments(contract, p.getWireStatusCode());
        if (spicePatientId != null && !spicePatientId.isBlank()) {
            ObjectNode patient = contract.putArray("participants").addObject();
            patient.put("id", "participant-patient");
            ObjectNode pa = patient.putObject("participantAttributes");
            pa.put("@context", p.getHealthParticipantCtx());
            pa.put("@type", "hpa:HealthParticipant");
            pa.put("participantRole", "PATIENT");
            ObjectNode hid = pa.putArray("healthIds").addObject();
            hid.put("system", p.getPatientHealthIdSystem());
            hid.put("value", spicePatientId);
        }
        ObjectNode ca = contract.putObject("contractAttributes");
        ca.put("@context", p.getHealthReferralCtx());
        ca.put("@type", "hrf:HealthReferral");
        ca.put("coordinationId", coordinationId);
        ca.put("lifecycleState", ccnLifecycleState);   // real outcome (ACCEPTED / CANCELLED / COMPLETED)
        if (reason != null && !reason.isBlank()) {
            ca.put("statusReason", reason);
        }
        // targetCriteria is required by CCN; serviceCategory.code must be in the allowed clinical set.
        ObjectNode tc = ca.putObject("targetCriteria");
        ObjectNode sc = tc.putObject("serviceCategory");
        sc.put("@context", p.getCodedValueCtx());
        sc.put("@type", "ServiceCategory");
        sc.put("code", p.getWireServiceCategory());
        sc.put("display", "Consultation");
        tc.put("consultationModality", "IN_PERSON");
        return root;
    }
}
