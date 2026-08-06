package org.egov.referralmanagement.ccn.bpp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralRequest;
import org.egov.referralmanagement.ccn.CcnIdentityResolver;
import org.egov.referralmanagement.ccn.CcnReferralStatusService;
import org.egov.referralmanagement.ccn.client.CcnOnixClient;
import org.egov.referralmanagement.ccn.config.CcnProperties;
import org.egov.referralmanagement.ccn.model.CcnReferralLink;
import org.egov.referralmanagement.ccn.repository.CcnReferralLinkRepository;
import org.egov.referralmanagement.service.HFReferralService;
import org.egov.referralmanagement.service.ReferralManagementService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Receive flow (UC3): SPICE (BAP) → HCM (BPP). Handles inbound discover/select/init/confirm/status/
 * update, dispatches on_* via the ONIX BPP caller, and on confirm creates a normal DIGIT
 * {@link Referral} in-process (validated) for the CHW. Writes/updates the INBOUND
 * {@link CcnReferralLink} row so it's tracked alongside our outbound referrals.
 */
@Slf4j
@Service
public class CcnBppService {

    private final CcnProperties p;
    private final ServiceCoordinationMapper mapper;
    private final CcnOnixClient onix;
    private final CcnReferralLinkRepository linkRepository;
    private final ReferralManagementService referralService;
    private final HFReferralService hfReferralService;
    private final InboundProjectResolver projectResolver;
    private final CcnIdentityResolver identityResolver;
    private final CcnReferralStatusService statusService;
    private final ObjectMapper om;

    public CcnBppService(CcnProperties p, ServiceCoordinationMapper mapper, CcnOnixClient onix,
                         CcnReferralLinkRepository linkRepository, ReferralManagementService referralService,
                         HFReferralService hfReferralService,
                         InboundProjectResolver projectResolver, CcnIdentityResolver identityResolver,
                         CcnReferralStatusService statusService,
                         @Qualifier("objectMapper") ObjectMapper om) {
        this.p = p;
        this.mapper = mapper;
        this.onix = onix;
        this.linkRepository = linkRepository;
        this.referralService = referralService;
        this.hfReferralService = hfReferralService;
        this.projectResolver = projectResolver;
        this.identityResolver = identityResolver;
        this.statusService = statusService;
        this.om = om;
    }

    /** Handle one inbound Beckn request from SPICE. Returns nothing — on_* dispatched async. */
    public void handle(String action, JsonNode body) {
        if (!p.isBppEnabled()) {
            log.debug("CCN BPP disabled; ignoring inbound {}", action);
            return;
        }
        String coordinationId = coordinationId(body);
        String bapId = body.at("/context/bapId").asText(null);
        String txn = body.at("/context/transactionId").asText(null);
        log.info("CCN BPP inbound {} coordinationId={} from bapId={}", action, coordinationId, bapId);

        switch (action) {
            case "discover" -> dispatch("on_discover", mapper.onDiscover(body));
            case "select"   -> { upsertInbound(coordinationId, txn, bapId, "DRAFT", "select", body, null); dispatch("on_select", mapper.onEcho("on_select", body, "DRAFT", "DRAFT")); }
            case "init"     -> { upsertInbound(coordinationId, txn, bapId, "DRAFT", "init", body, null); dispatch("on_init", mapper.onEcho("on_init", body, "DRAFT", "DRAFT")); }
            case "confirm"  -> onConfirm(coordinationId, txn, bapId, body);
            case "status"   -> dispatch("on_status", mapper.onEcho("on_status", body, currentState(coordinationId), "ACTIVE"));
            case "update"   -> { upsertInbound(coordinationId, txn, bapId, lifecycleState(body), "update", body, null); dispatch("on_update", mapper.onEcho("on_update", body, lifecycleState(body), "ACTIVE")); }
            default         -> log.warn("CCN BPP unhandled action {}", action);
        }
    }

    private void onConfirm(String coordinationId, String txn, String bapId, JsonNode body) {
        // HFReferral-only: the app no longer uses the normal Referral entity, so we create ONLY an
        // autofilled HFReferral (shows in the app's "Referral Details" screen for the HF worker to
        // view/accept/reject/resolve). The normal Referral flow is intentionally not integrated.
        String createdHfReferralId = null;
        try {
            createdHfReferralId = createInboundHfReferral(coordinationId, body);
        } catch (Exception e) {
            log.error("CCN BPP failed to create inbound HFReferral for {}: {}", coordinationId, e.getMessage());
        }
        // Link points at the created HFReferral so the inbound status-back flow can correlate it.
        upsertInbound(coordinationId, txn, bapId, "ACTIVE", "confirm", body, createdHfReferralId);
        dispatch("on_confirm", mapper.onEcho("on_confirm", body, "ACTIVE", "ACTIVE"));
    }

    /**
     * Create an HFReferral for the inbound SPICE referral so it renders in the app's "Referral Details"
     * screen. Uses a normal VALIDATED create — HFReferral validation only checks projectId +
     * projectFacilityId + non-existence (no beneficiary validator), all of which we satisfy; the same
     * validators run on the device UPDATE (attend), so nothing the update needs is missing here.
     * All required form fields are auto-filled: cycle (hardcoded current cycle), name of child, age in
     * months (from the individual's DOB), gender (hardcoded), symptom/referral-reason. Tagged with the
     * inbound marker so the outbound consumer never bounces it back to SPICE.
     */
    private String createInboundHfReferral(String coordinationId, JsonNode body) {
        if (p.getInboundHfFacilityId() == null || p.getInboundHfFacilityId().isBlank()) {
            log.warn("CCN BPP: inboundHfFacilityId not configured — skipping HFReferral for {}", coordinationId);
            return null;
        }
        String abha = patientId(body);
        RequestInfo ri = RequestInfo.builder()
                .userInfo(User.builder().uuid("ccn-system").tenantId(p.getInboundTenantId()).type("SYSTEM").build())
                .build();
        String tenantId = p.getInboundTenantId();

        // Resolve the (imported) individual for name + age; tolerate absence.
        Individual individual = identityResolver.findIndividualByUniqueBeneficiaryId(abha, tenantId, ri);
        String name = firstNonBlank(patientNameFromBody(body), identityResolver.displayNameOf(individual));
        String age = ageInMonths(individual);

        // projectId MUST be the project the CHW downloads (config override) else the resolved project.
        String projectId = p.getInboundHfProjectId();
        if (projectId == null || projectId.isBlank()) {
            InboundProjectResolver.Resolution r = projectResolver.resolve(abha, ri);
            projectId = r != null ? r.getProjectId() : null;
        }
        if (projectId == null || projectId.isBlank()) {
            log.warn("CCN BPP: no projectId for inbound HFReferral {} — skipping", coordinationId);
            return null;
        }

        long now = System.currentTimeMillis();
        AuditDetails audit = AuditDetails.builder()
                .createdBy("ccn-system").createdTime(now).lastModifiedBy("ccn-system").lastModifiedTime(now).build();

        List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().key(p.getInboundHfMarkerKey()).value("true").build());   // loop-guard marker
        if (name != null) fields.add(Field.builder().key("nameOfReferral").value(name).build());
        // Write both key variants so the value prefills regardless of app version: the referral form
        // enum uses "age"/"cycle" (ReferralReconEnums), while the reactive form controls use
        // "ageInMonths"/"cycleIndex". Populating both keeps it version-proof.
        if (age != null) {
            fields.add(Field.builder().key("age").value(age).build());
            fields.add(Field.builder().key("ageInMonths").value(age).build());
        }
        fields.add(Field.builder().key("gender").value(p.getInboundHfGender()).build());
        fields.add(Field.builder().key("cycle").value(p.getInboundHfCycle()).build());
        fields.add(Field.builder().key("cycleIndex").value(p.getInboundHfCycle()).build());
        // Initial referral status — the HF worker moves this to ACCEPTED/REJECTED/RESOLVED, which the
        // HFReferral-update consumer pushes back to SPICE. Downsyncs to the device so the CHW sees it.
        fields.add(Field.builder().key(p.getReferralStatusKey()).value(p.getInboundInitialStatus()).build());

        HFReferral hf = HFReferral.builder()
                .clientReferenceId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .projectId(projectId)
                .projectFacilityId(p.getInboundHfFacilityId())
                .beneficiaryId(abha)
                .referralCode(coordinationId)
                .symptom(p.getInboundHfSymptom())
                .isDeleted(Boolean.FALSE)
                .rowVersion(1)
                .auditDetails(audit)
                .clientAuditDetails(AuditDetails.builder()
                        .createdBy("ccn-system").createdTime(now).lastModifiedBy("ccn-system").lastModifiedTime(now).build())
                .additionalFields(AdditionalFields.builder().schema("HFReferral").version(1).fields(fields).build())
                .build();

        HFReferral created = hfReferralService.create(
                HFReferralRequest.builder().requestInfo(ri).hfReferral(hf).build());
        log.info("CCN BPP created inbound HFReferral id={} (project={}, facility={}, symptom={}, cycle={}, status={}) for coordinationId={}",
                created.getId(), projectId, p.getInboundHfFacilityId(), p.getInboundHfSymptom(), p.getInboundHfCycle(),
                p.getInboundInitialStatus(), coordinationId);
        return created.getId();
    }

    /** Patient display name from the inbound PATIENT participant descriptor, or null. */
    private String patientNameFromBody(JsonNode b) {
        for (JsonNode part : b.at("/message/contract/participants")) {
            if ("PATIENT".equals(part.at("/participantAttributes/participantRole").asText())) {
                String nm = part.at("/descriptor/name").asText(null);
                return (nm != null && !nm.isBlank()) ? nm : null;
            }
        }
        return null;
    }

    /** Age in whole months between the individual's DOB and today, or null if unknown. */
    private String ageInMonths(Individual individual) {
        if (individual == null || individual.getDateOfBirth() == null) return null;
        try {
            LocalDate dob = individual.getDateOfBirth().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            long months = ChronoUnit.MONTHS.between(dob, LocalDate.now());
            return months >= 0 ? String.valueOf(months) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /** Create a normal DIGIT Referral for the CHW from the inbound coordination (validated create).
     *  Tenant is fixed for this node; the project is resolved per-referral from the SPICE patientId
     *  (SPICE_PATIENT_ID → synced ProjectBeneficiary → its projectId) rather than a static value. */
    private String createInboundReferral(String coordinationId, JsonNode body) {
        String spicePatientId = patientId(body);   // canonical UNIQUE_BENEFICIARY_ID off the PATIENT participant
        RequestInfo ri = RequestInfo.builder()
                .userInfo(User.builder().uuid("ccn-system").tenantId(p.getInboundTenantId()).type("SYSTEM").build())
                .build();
        InboundProjectResolver.Resolution r = projectResolver.resolve(spicePatientId, ri);
        Referral referral = Referral.builder()
                .tenantId(p.getInboundTenantId())
                .clientReferenceId(UUID.randomUUID().toString())
                .projectId(r.getProjectId())                                         // resolved from the patient's beneficiary (else configured fallback project)
                .projectBeneficiaryId(r.getProjectBeneficiaryId())                   // real beneficiary link (null if patient not in HCM)
                .projectBeneficiaryClientReferenceId(r.getProjectBeneficiaryClientReferenceId())
                .referrerId(body.at("/context/bapId").asText(null))                 // origin system
                .recipientType("STAFF")
                .recipientId(p.getInboundRecipientId())                             // CHW/staff to action it
                .reasons(List.of("INBOUND_" + serviceCategory(body)))
                .referralCode(coordinationId)                                        // cross-ref to the coordination
                .build();
        // Stamp the incoming canonical id onto the created referral (additionalFields[abhaId]) — both paths.
        identityResolver.writeAbhaField(referral, spicePatientId);
        ReferralRequest request = ReferralRequest.builder().requestInfo(ri).referral(referral).build();
        Referral created;
        if (r.isResolved()) {
            // patient/beneficiary found → normal validated create
            created = referralService.create(request);
        } else {
            // patient not in HCM → persist anyway, skipping reference-existence validators so it never drops
            log.warn("CCN BPP inbound patient {}={} not resolvable in HCM; persisting referral WITHOUT reference validation",
                    p.getPatientIdentifierType(), spicePatientId);
            created = referralService.createSkippingValidation(request);
        }
        log.info("CCN BPP created inbound Referral id={} for coordinationId={} (project={}, resolved={})",
                created.getId(), coordinationId, r.getProjectId(), r.isResolved());
        return created.getId();
    }

    /**
     * Push an HCM-initiated status back to SPICE for an inbound coordination (HF worker accept / reject
     * / resolve / complete). Uses the configured back action ({@code on_update} per the update API) and
     * resolves the Beckn status.code from config. Only inbound coordinations are pushed.
     */
    public void publishResult(String coordinationId, String lifecycleState) {
        publishResult(coordinationId, lifecycleState, null);
    }

    /** Push with an optional worker reason (e.g. why the referral was rejected) — stored by SPICE. */
    public void publishResult(String coordinationId, String lifecycleState, String reason) {
        CcnReferralLink link = linkRepository.findByCoordinationId(coordinationId, p.getInboundTenantId());
        if (link == null || !CcnReferralLink.INBOUND.equals(link.getDirection())) {
            log.warn("CCN BPP publishResult: no inbound coordination {}", coordinationId);
            return;
        }
        JsonNode lastInbound = parse(link.getLastPayload());
        String statusCode = statusService.statusCodeFor(lifecycleState);
        String onAction = p.getBackAction();
        dispatch(onAction, mapper.statusUpdate(lastInbound, coordinationId, lifecycleState, statusCode, onAction, reason));
        linkRepository.updateState(coordinationId, lifecycleState, "publishResult", System.currentTimeMillis(), p.getInboundTenantId());
        log.info("CCN BPP pushed {} ({}={}) to SPICE for inbound coordinationId={}{}",
                onAction, lifecycleState, statusCode, coordinationId, reason != null ? " reason=" + reason : "");
    }

    private void dispatch(String onAction, JsonNode payload) {
        try {
            onix.sendBpp(onAction, payload);
        } catch (Exception e) {
            log.error("CCN BPP dispatch {} failed: {}", onAction, e.getMessage());
        }
    }

    private void upsertInbound(String coordinationId, String txn, String bapId, String lifecycle,
                               String action, JsonNode body, String createdReferralId) {
        long now = System.currentTimeMillis();
        CcnReferralLink existing = linkRepository.findByCoordinationId(coordinationId, p.getInboundTenantId());
        CcnReferralLink link = CcnReferralLink.builder()
                .coordinationId(coordinationId)
                .transactionId(txn)
                .hfReferralId(createdReferralId != null ? createdReferralId : (existing != null ? existing.getHfReferralId() : null))
                .beneficiaryId(patientId(body))
                .lifecycleState(lifecycle)
                .lastAction(action)
                .direction(CcnReferralLink.INBOUND)
                .localRole("BPP")
                .initiatorSubscriberId(bapId)
                .counterpartySubscriberId(p.getBapId())
                .contractType(contractType(body))
                .serviceCategory(serviceCategory(body))
                .lastPayload(body.toString())
                .tenantId(p.getInboundTenantId())
                .createdTime(existing != null && existing.getCreatedTime() != null ? existing.getCreatedTime() : now)
                .lastModifiedTime(now)
                .build();
        linkRepository.save(link);
    }

    // ── small extractors ──
    private String coordinationId(JsonNode b) {
        String c = b.at("/message/contract/contractAttributes/coordinationId").asText(null);
        return c != null && !c.isBlank() ? c : b.at("/message/contract/id").asText(null);
    }
    private String lifecycleState(JsonNode b) {
        String s = b.at("/message/contract/contractAttributes/lifecycleState").asText(null);
        return s != null ? s : "ACTIVE";
    }
    private String serviceCategory(JsonNode b) {
        String s = b.at("/message/contract/contractAttributes/targetCriteria/serviceCategory/code").asText(null);
        return s != null ? s : "FIELD_DATA_COLLECTION";
    }
    private String contractType(JsonNode b) {
        String t = b.at("/message/contract/contractAttributes/@type").asText("");
        return t.contains("HealthReferral") ? "HealthReferral" : "ServiceCoordination";
    }
    /** Extract the SPICE patientId (national id) from the PATIENT participant. Prefer the healthId whose
     *  system is the configured patient system (SPICE uses "ABHA"); accept the legacy SPICE_PATIENT_ID as
     *  an alternate; fall back to the first healthId only if neither system is present. */
    private String patientId(JsonNode b) {
        for (JsonNode part : b.at("/message/contract/participants")) {
            if ("PATIENT".equals(part.at("/participantAttributes/participantRole").asText())) {
                JsonNode healthIds = part.at("/participantAttributes/healthIds");
                String legacy = null;
                for (JsonNode hid : healthIds) {
                    String system = hid.at("/system").asText();
                    if (p.getPatientHealthIdSystem().equals(system)) {
                        return hid.at("/value").asText(null);
                    }
                    if ("SPICE_PATIENT_ID".equals(system)) {
                        legacy = hid.at("/value").asText(null);
                    }
                }
                if (legacy != null) return legacy;
                return healthIds.at("/0/value").asText(null);
            }
        }
        return null;
    }
    private String currentState(String coordinationId) {
        CcnReferralLink l = linkRepository.findByCoordinationId(coordinationId, p.getInboundTenantId());
        return l != null && l.getLifecycleState() != null ? l.getLifecycleState() : "ACTIVE";
    }
    private JsonNode parse(String s) {
        try { return s == null ? om.createObjectNode() : om.readTree(s); }
        catch (Exception e) { return om.createObjectNode(); }
    }
}
