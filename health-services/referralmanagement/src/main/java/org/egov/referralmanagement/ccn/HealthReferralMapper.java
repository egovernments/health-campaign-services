package org.egov.referralmanagement.ccn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps a DIGIT {@link HFReferral} to Beckn {@code HealthReferral} payloads (CCN UC1 shape) for the
 * select / init / confirm actions. The payload is UNSIGNED — ONIX (the BAP node) signs and routes it.
 *
 * <p>NOTE: HFReferral is a thin model (beneficiaryId, symptom, referralCode, projectFacilityId,
 * nationalLevelId). Fields SPICE needs but HFReferral has no source for (specialty, consent, etc.)
 * are marked TODO and must be agreed with SPICE (Step 8).</p>
 */
@Component
public class HealthReferralMapper {

    private final CcnProperties p;
    private final ObjectMapper om;

    public HealthReferralMapper(CcnProperties p, @Qualifier("objectMapper") ObjectMapper om) {
        this.p = p;
        this.om = om;
    }

    private ObjectNode context(String action, String transactionId) {
        ObjectNode c = om.createObjectNode();
        c.put("domain", p.getDomain());
        c.put("networkId", p.getNetworkId());
        c.put("action", action);
        c.put("version", p.getVersion());
        c.put("bapId", p.getBapId());
        c.put("bapUri", p.getBapUri());
        c.put("transactionId", transactionId);
        c.put("messageId", UUID.randomUUID().toString());
        c.put("timestamp", OffsetDateTime.now().toString());
        c.put("bppId", p.getBppId());
        c.put("bppUri", p.getBppUri());
        return c;
    }

    private ObjectNode commitments(String statusCode) {
        ObjectNode arrHolder = om.createObjectNode();
        ObjectNode cm = arrHolder.putArray("commitments").addObject();
        cm.put("id", "commitment-001");
        cm.putObject("status").putObject("descriptor").put("code", statusCode);
        ObjectNode r = cm.putArray("resources").addObject();
        r.put("id", p.getResourceId());
        r.putObject("quantity").put("count", 1);
        ObjectNode offer = cm.putObject("offer");
        offer.put("id", p.getOfferId());
        offer.putArray("resourceIds").add(p.getResourceId());
        return arrHolder;
    }

    private void addPatientParticipant(ObjectNode contract, HFReferral r) {
        ObjectNode part = contract.putArray("participants").addObject();
        part.put("id", "participant-" + safe(r.getBeneficiaryId()));
        ObjectNode attrs = part.putObject("participantAttributes");
        attrs.put("@context", p.getHealthParticipantCtx());
        attrs.put("@type", "hpa:HealthParticipant");
        attrs.put("participantRole", "PATIENT");
        ObjectNode hid = attrs.putArray("healthIds").addObject();
        hid.put("system", "BENEFICIARY_ID");
        hid.put("value", safe(r.getBeneficiaryId()));
        if (r.getNationalLevelId() != null) {
            ObjectNode nid = attrs.withArray("healthIds").addObject();
            nid.put("system", "NATIONAL_ID");
            nid.put("value", r.getNationalLevelId());
        }
    }

    /** HealthReferral contractAttributes. {@code full} adds the referralNote (confirm). */
    private ObjectNode contractAttributes(HFReferral r, String coordinationId, String lifecycleState, boolean full) {
        ObjectNode ca = om.createObjectNode();
        ca.put("@context", p.getHealthReferralCtx());
        ca.put("@type", "hrf:HealthReferral");
        ca.put("coordinationId", coordinationId);
        ca.put("lifecycleState", lifecycleState);
        ca.put("clinicalUrgencyTier", "ROUTINE"); // TODO: no urgency in HFReferral; confirm with SPICE
        // targetCriteria — carry the symptom as the referral reason.
        ObjectNode tc = ca.putObject("targetCriteria");
        tc.put("consultationModality", "IN_PERSON");
        if (r.getSymptom() != null) {
            tc.putObject("reason").put("code", "SYMPTOM").put("display", r.getSymptom());
        }
        // referralCode carried as a cross-reference back to the HCM record.
        if (r.getReferralCode() != null) ca.put("referralCode", r.getReferralCode());
        if (r.getProjectFacilityId() != null) ca.put("referringFacilityId", r.getProjectFacilityId());

        if (full) {
            // referralNote is a POINTER only (clinical data stays in the records/HIE layer).
            ObjectNode note = ca.putObject("referralNote");
            note.put("artifactRef", "hfreferral-" + safe(r.getReferralCode() != null ? r.getReferralCode() : coordinationId));
            note.put("revocationStatus", "ACTIVE");
            if (r.getSymptomSurveyId() != null) note.put("symptomSurveyRef", r.getSymptomSurveyId());
            // TODO: consent block — UC1 requires consent at confirm; HFReferral has no consent source.
        }
        return ca;
    }

    private ObjectNode base(String action, String transactionId, String coordinationId, String statusCode) {
        ObjectNode root = om.createObjectNode();
        root.set("context", context(action, transactionId));
        ObjectNode contract = root.putObject("message").putObject("contract");
        if (!"select".equals(action)) contract.put("id", coordinationId);
        contract.putObject("status").put("code", statusCode);
        contract.setAll(commitments(statusCode));
        return root;
    }

    public ObjectNode select(HFReferral r, String txnId, String coordinationId) {
        ObjectNode root = base("select", txnId, coordinationId, "DRAFT");
        ObjectNode contract = (ObjectNode) root.at("/message/contract");
        addPatientParticipant(contract, r);
        contract.set("contractAttributes", contractAttributes(r, coordinationId, "DRAFT", false));
        return root;
    }

    public ObjectNode init(HFReferral r, String txnId, String coordinationId) {
        ObjectNode root = base("init", txnId, coordinationId, "DRAFT");
        ObjectNode contract = (ObjectNode) root.at("/message/contract");
        addPatientParticipant(contract, r);
        contract.set("contractAttributes", contractAttributes(r, coordinationId, "DRAFT", false));
        return root;
    }

    public ObjectNode confirm(HFReferral r, String txnId, String coordinationId) {
        ObjectNode root = base("confirm", txnId, coordinationId, "ACTIVE");
        ObjectNode contract = (ObjectNode) root.at("/message/contract");
        addPatientParticipant(contract, r);
        contract.set("contractAttributes", contractAttributes(r, coordinationId, "ACTIVE", true));
        return root;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
