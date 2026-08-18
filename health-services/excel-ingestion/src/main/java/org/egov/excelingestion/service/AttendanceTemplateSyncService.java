package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.excelingestion.repository.GeneratedFileRepository;
import org.egov.excelingestion.web.models.AttendanceSyncEvent;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerationSearchCriteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the generated attendance templates in step with the attendance service. A register deleted
 * or a person de-enrolled there leaves this service's last completed template stale, and a download
 * serves that file as-is — so the record is expired and the next download regenerates it.
 */
@Service
@Slf4j
public class AttendanceTemplateSyncService {

    private static final String LOG_PREFIX = "ATTENDANCE TEMPLATE SYNC ::";
    private static final String SYSTEM_MODIFIER = "excel-ingestion-attendance-sync";

    private final GeneratedFileRepository generatedFileRepository;
    private final GenerationService generationService;

    public AttendanceTemplateSyncService(GeneratedFileRepository generatedFileRepository,
                                         GenerationService generationService) {
        this.generatedFileRepository = generatedFileRepository;
        this.generationService = generationService;
    }

    /** Registers deleted in the attendance service: their templates must not be served again. */
    public void handleRegisterDeletes(List<AttendanceSyncEvent.DeletedRegister> registers) {
        if (registers == null || registers.isEmpty()) {
            log.warn("{} delete event carried no registers, skipping", LOG_PREFIX);
            return;
        }

        Map<String, Set<String>> registerIdsByTenant = new LinkedHashMap<>();
        for (AttendanceSyncEvent.DeletedRegister register : registers) {
            addRegister(registerIdsByTenant, register.getTenantId(), register.getId(), "delete");
        }
        expireTemplates(registerIdsByTenant, "register delete");
    }

    /**
     * Attendee/staff de-enrolments. Entries without a de-enrolment date are ignored: the same topic
     * also carries tag edits and plain enrolments, which leave the template valid.
     */
    public void handleDeEnrolments(List<AttendanceSyncEvent.Enrolment> entries, String label) {
        if (entries == null || entries.isEmpty()) {
            log.warn("{} {} event carried no entries, skipping", LOG_PREFIX, label);
            return;
        }

        Map<String, Set<String>> registerIdsByTenant = new LinkedHashMap<>();
        for (AttendanceSyncEvent.Enrolment entry : entries) {
            Long denrollmentDate = entry.getDenrollmentDate();
            if (denrollmentDate == null || denrollmentDate <= 0) {
                continue;
            }
            addRegister(registerIdsByTenant, entry.getTenantId(), entry.getRegisterId(), label);
        }

        if (registerIdsByTenant.isEmpty()) {
            log.info("{} no {} entry carried a de-enrolment date, nothing to expire", LOG_PREFIX, label);
            return;
        }
        expireTemplates(registerIdsByTenant, label + " de-enrolment");
    }

    private void addRegister(Map<String, Set<String>> registerIdsByTenant, String tenantId,
                             String registerId, String label) {
        String trimmedTenantId = trim(tenantId);
        String trimmedRegisterId = trim(registerId);
        if (trimmedTenantId.isEmpty() || trimmedRegisterId.isEmpty()) {
            log.warn("{} skipping {} entry missing tenantId/registerId", LOG_PREFIX, label);
            return;
        }
        registerIdsByTenant.computeIfAbsent(trimmedTenantId, key -> new HashSet<>()).add(trimmedRegisterId);
    }

    /**
     * Each tenant is expired independently so one failing tenant does not skip the rest, then the
     * failures are rethrown: the listener must not commit the event while a template it should have
     * invalidated is still being served. Re-expiring an already-expired record is a no-op, so a
     * redelivery only retries what actually failed.
     */
    private void expireTemplates(Map<String, Set<String>> registerIdsByTenant, String reason) {
        List<String> failedTenants = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : registerIdsByTenant.entrySet()) {
            String tenantId = entry.getKey();
            try {
                int expired = expireForTenant(tenantId, entry.getValue());
                log.info("{} {} — expired {} template(s) for {} register(s) in tenant {}",
                        LOG_PREFIX, reason, expired, entry.getValue().size(), tenantId);
            } catch (Exception e) {
                failedTenants.add(tenantId);
                log.error("{} {} — failed to expire templates for tenant {}: {}",
                        LOG_PREFIX, reason, tenantId, e.getMessage(), e);
            }
        }

        if (!failedTenants.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "%s %s — template expiry failed for %d of %d tenant(s): %s",
                    LOG_PREFIX, reason, failedTenants.size(), registerIdsByTenant.size(),
                    String.join(", ", failedTenants)));
        }
    }

    private int expireForTenant(String tenantId, Set<String> registerIds) throws InvalidTenantIdException {
        List<GenerateResource> live = new ArrayList<>();
        for (String registerId : registerIds) {
            live.addAll(findLiveTemplates(tenantId, registerId));
        }
        if (live.isEmpty()) {
            return 0;
        }
        return generationService.expireRecords(tenantId, live, SYSTEM_MODIFIER);
    }

    /**
     * A register-scoped template carries the register as its referenceId. Type is left unconstrained
     * so any template generated per register is swept, not just the attendee sheet.
     */
    private List<GenerateResource> findLiveTemplates(String tenantId, String registerId) throws InvalidTenantIdException {
        List<GenerateResource> found = generatedFileRepository.search(
                GenerationSearchCriteria.builder()
                        .tenantId(tenantId)
                        .referenceIds(List.of(registerId))
                        .referenceTypes(List.of(ProcessingConstants.REFERENCE_TYPE_ATTENDANCE_REGISTER))
                        .statuses(GenerationConstants.LIVE_STATUSES)
                        .build());
        return found == null ? Collections.emptyList() : found;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
