package org.egov.referralmanagement.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DownsyncJobRegistry {

    /**
     * False until the startup resume scan completes. During this window we don't yet
     * know which tenants are occupied, so ALL incoming generation requests must be
     * rejected with 503.
     */
    private final AtomicBoolean resumeScanComplete = new AtomicBoolean(false);

    /** One slot per tenantId — blocks registry generation for that tenant. */
    private final ConcurrentHashMap<String, String> registrySlots = new ConcurrentHashMap<>();

    /** One slot per "tenantId:rootProjectId" — blocks project generation for that project. */
    private final ConcurrentHashMap<String, String> projectSlots  = new ConcurrentHashMap<>();

    // ── Startup readiness ─────────────────────────────────────────────────────

    public void markScanComplete() {
        resumeScanComplete.set(true);
    }

    public boolean isScanComplete() {
        return resumeScanComplete.get();
    }

    // ── Registry slot ─────────────────────────────────────────────────────────

    public boolean tryAcquireRegistry(String tenantId, String jobId) {
        return registrySlots.putIfAbsent(tenantId, jobId) == null;
    }

    public void releaseRegistry(String tenantId) {
        registrySlots.remove(tenantId);
    }

    public String getActiveRegistryJobId(String tenantId) {
        return registrySlots.get(tenantId);
    }

    // ── Project slot ──────────────────────────────────────────────────────────

    public boolean tryAcquireProject(String tenantId, String rootProjectId, String jobId) {
        return projectSlots.putIfAbsent(projectKey(tenantId, rootProjectId), jobId) == null;
    }

    public void releaseProject(String tenantId, String rootProjectId) {
        if (rootProjectId != null) projectSlots.remove(projectKey(tenantId, rootProjectId));
    }

    public String getActiveProjectJobId(String tenantId, String rootProjectId) {
        return rootProjectId == null ? null : projectSlots.get(projectKey(tenantId, rootProjectId));
    }

    // ── Resume runner helpers (pre-populate before scan flag flips) ───────────

    public void markActive(String tenantId, String rootProjectId, String jobId) {
        registrySlots.put(tenantId, jobId);
        if (rootProjectId != null) projectSlots.put(projectKey(tenantId, rootProjectId), jobId);
    }

    public void release(String tenantId, String rootProjectId) {
        releaseRegistry(tenantId);
        releaseProject(tenantId, rootProjectId);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String projectKey(String tenantId, String rootProjectId) {
        return tenantId + ":" + rootProjectId;
    }
}
