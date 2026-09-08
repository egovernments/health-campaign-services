package org.egov.referralmanagement.web.controllers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.ResponseInfoFactory;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncFileGenService;
import org.egov.referralmanagement.service.DownsyncJobRegistry;
import org.egov.referralmanagement.web.models.DownsyncFileGenRequest;
import org.egov.referralmanagement.web.models.DownsyncFileGenResponse;
import org.egov.referralmanagement.web.models.DownsyncGenerationJob;
import org.egov.referralmanagement.web.models.DownsyncGenerationLocality;
import org.egov.referralmanagement.web.models.DownsyncJobConflictResponse;
import org.egov.referralmanagement.web.models.DownsyncJobDetail;
import org.egov.referralmanagement.web.models.DownsyncJobSearchRequest;
import org.egov.referralmanagement.web.models.DownsyncJobSearchResponse;
import org.egov.referralmanagement.web.models.DownsyncLocalityFile;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Controller
@RequestMapping("/downsync")
@Validated
public class DownsyncFileGenController {

    @Autowired private DownsyncFileGenService downsyncFileGenService;
    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncJobRegistry jobRegistry;

    @PostMapping("/v1/_generate")
    public ResponseEntity<?> generate(@Valid @RequestBody DownsyncFileGenRequest request) {

        String tenantId      = request.getTenantId();
        String rootProjectId = request.getRootProjectId();
        String createdBy = request.getRequestInfo().getUserInfo() != null
                ? request.getRequestInfo().getUserInfo().getUuid() : "system";

        // ── Gate 1: startup scan not yet complete ─────────────────────────────
        if (!jobRegistry.isScanComplete()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "10")
                    .body(Map.of(
                            "code", "SERVICE_INITIALIZING",
                            "message", "Service is resuming interrupted jobs on startup. Try again in a few seconds."));
        }

        // ── Gate 2: registry lock — a job already running for this tenant ─────
        String activeRegistryJobId = jobRegistry.getActiveRegistryJobId(tenantId);
        if (activeRegistryJobId == null) {
            DownsyncGenerationJob dbJob = jobRepository.findInProgressJobByTenant(tenantId);
            if (dbJob != null) activeRegistryJobId = dbJob.getId();
        }
        if (activeRegistryJobId != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildConflictResponse(tenantId, activeRegistryJobId, "Registry"));
        }

        // ── Gate 3: project lock — same project already running ───────────────
        if (StringUtils.hasText(rootProjectId)) {
            String activeProjectJobId = jobRegistry.getActiveProjectJobId(tenantId, rootProjectId);
            if (activeProjectJobId != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(buildConflictResponse(tenantId, activeProjectJobId, "Project"));
            }
        }

        // ── Fetch localities ──────────────────────────────────────────────────
        List<String> allLocalities = jobRepository.fetchAllLocalities(tenantId);
        log.info("Resolved {} localities for tenant {}", allLocalities.size(), tenantId);

        // Fetch project-locality mapping if rootProjectId present
        List<String[]> projectLocPairs = List.of(); // [projectId, locality]
        if (StringUtils.hasText(rootProjectId)) {
            projectLocPairs = jobRepository.fetchProjectLocalityMapping(tenantId, rootProjectId);
            if (projectLocPairs.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("code", "PROJECT_NOT_FOUND",
                                "message", "No leaf projects found under rootProjectId: " + rootProjectId));
            }
        }

        // ── Insert job row ────────────────────────────────────────────────────
        String jobId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        int totalRequested = allLocalities.size() + projectLocPairs.size();

        jobRepository.insertJob(DownsyncGenerationJob.builder()
                .id(jobId).tenantId(tenantId).projectId(rootProjectId)
                .totalRequested(totalRequested).totalSucceeded(0).totalFailed(0)
                .status("IN_PROGRESS").createdBy(createdBy).createdTime(now)
                .lastModifiedBy(createdBy).lastModifiedTime(now).rowVersion(1L)
                .build());

        // ── Build and insert REGISTRY locality + file rows ────────────────────
        List<LocalityDownsyncCriteria> registryCriteria = new ArrayList<>();
        for (String loc : allLocalities) {
            String rowId = UUID.randomUUID().toString();
            jobRepository.insertLocality(DownsyncGenerationLocality.builder()
                    .id(rowId).jobId(jobId).tenantId(tenantId)
                    .projectId(null).locality(loc).category("REGISTRY")
                    .status("PENDING").createdTime(now).build());
            for (String ft : DownsyncFileGenService.REGISTRY_FILE_TYPES) {
                jobRepository.insertFile(tenantId, DownsyncLocalityFile.builder()
                        .id(UUID.randomUUID().toString()).localityRowId(rowId).jobId(jobId)
                        .fileType(ft).status("PENDING").build());
            }
            registryCriteria.add(LocalityDownsyncCriteria.builder()
                    .locality(loc).tenantId(tenantId).localityRowId(rowId)
                    .category("REGISTRY").forceRefresh(request.isForceRefresh()).build());
        }

        // ── Build and insert PROJECT locality + file rows ─────────────────────
        List<LocalityDownsyncCriteria> projectCriteria = new ArrayList<>();
        for (String[] pair : projectLocPairs) {
            String leafProjectId = pair[0];
            String loc = pair[1];
            String rowId = UUID.randomUUID().toString();
            jobRepository.insertLocality(DownsyncGenerationLocality.builder()
                    .id(rowId).jobId(jobId).tenantId(tenantId)
                    .projectId(leafProjectId).locality(loc).category("PROJECT")
                    .status("PENDING").createdTime(now).build());
            for (String ft : DownsyncFileGenService.PROJECT_FILE_TYPES) {
                jobRepository.insertFile(tenantId, DownsyncLocalityFile.builder()
                        .id(UUID.randomUUID().toString()).localityRowId(rowId).jobId(jobId)
                        .fileType(ft).status("PENDING").build());
            }
            projectCriteria.add(LocalityDownsyncCriteria.builder()
                    .locality(loc).tenantId(tenantId).projectId(leafProjectId)
                    .rootProjectId(rootProjectId).localityRowId(rowId)
                    .category("PROJECT").forceRefresh(request.isForceRefresh()).build());
        }

        // ── Acquire locks — after inserts, before async kick-off ─────────────
        if (!jobRegistry.tryAcquireRegistry(tenantId, jobId)) {
            cancelJob(jobId, tenantId, createdBy);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "JOB_IN_PROGRESS",
                            "message", "A generation job is already running for tenant: " + tenantId));
        }
        if (StringUtils.hasText(rootProjectId) && !jobRegistry.tryAcquireProject(tenantId, rootProjectId, jobId)) {
            jobRegistry.releaseRegistry(tenantId);
            cancelJob(jobId, tenantId, createdBy);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "JOB_IN_PROGRESS",
                            "message", "A generation job is already running for project: " + rootProjectId));
        }

        // ── Return 202 immediately, run generation in background ──────────────
        final List<LocalityDownsyncCriteria> finalRegistry = registryCriteria;
        final List<LocalityDownsyncCriteria> finalProject  = projectCriteria;
        final String finalRootProjectId = rootProjectId;

        // household_address_mv is the locality-scoping join for both registry and project queries —
        // refresh only if household data has changed since the last completed job.
        CompletableFuture<Void> mvFuture = CompletableFuture.runAsync(() -> {
            try {
                if (jobRepository.shouldRefreshMv(tenantId)) {
                    log.info("Refreshing household_address_mv — tenant={}", tenantId);
                    jobRepository.refreshHouseholdAddressMv(tenantId);
                } else {
                    log.info("Skipping household_address_mv refresh, no household changes since last job — tenant={}", tenantId);
                }
            } catch (Exception e) {
                log.error("household_address_mv refresh failed for tenant={}: {}", tenantId, e.getMessage(), e);
                throw e;
            }
        });

        CompletableFuture<Void> registryFuture = finalRegistry.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : mvFuture.thenRunAsync(() -> downsyncFileGenService.generateRegistry(finalRegistry, jobId));

        CompletableFuture<Void> projectFuture = finalProject.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : mvFuture.thenRunAsync(() -> downsyncFileGenService.generateProject(finalProject, jobId));

        CompletableFuture.allOf(registryFuture, projectFuture).whenComplete((v, ex) -> {
            jobRegistry.releaseRegistry(tenantId);
            jobRegistry.releaseProject(tenantId, finalRootProjectId);

            if (ex != null) {
                log.error("Job {} failed with unhandled exception: {}", jobId, ex.getMessage(), ex);
                jobRepository.updateJob(DownsyncGenerationJob.builder()
                        .id(jobId).tenantId(tenantId)
                        .totalRequested(totalRequested).totalSucceeded(0).totalFailed(totalRequested)
                        .status("FAILED").lastModifiedBy(createdBy)
                        .lastModifiedTime(System.currentTimeMillis()).build());
                return;
            }

            Map<String, Integer> counts = jobRepository.countOutcomes(tenantId, jobId);
            int succeeded = counts.getOrDefault("succeeded", 0);
            int failed    = counts.getOrDefault("failed", 0);
            String finalStatus = failed == 0 ? "COMPLETED"
                    : (succeeded == 0 ? "FAILED" : "PARTIAL_FAILURE");

            jobRepository.updateJob(DownsyncGenerationJob.builder()
                    .id(jobId).tenantId(tenantId)
                    .totalRequested(totalRequested)
                    .totalSucceeded(succeeded).totalFailed(failed)
                    .status(finalStatus).lastModifiedBy(createdBy)
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());

            log.info("Job {} finished — status={}, succeeded={}, failed={}", jobId, finalStatus, succeeded, failed);
        });

        log.info("Job {} accepted — {} registry + {} project localities, tenant={}",
                jobId, registryCriteria.size(), projectCriteria.size(), tenantId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DownsyncFileGenResponse.builder()
                        .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                        .jobId(jobId)
                        .status("IN_PROGRESS")
                        .message("Generation started. Poll /downsync/v1/jobs/_search?jobId=" + jobId + " for status.")
                        .build());
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @PostMapping("/v1/jobs/_search")
    public ResponseEntity<?> searchJob(@Valid @RequestBody DownsyncJobSearchRequest request) {
        DownsyncJobDetail detail = jobRepository.findJobDetail(request.getJobId());
        if (detail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "JOB_NOT_FOUND",
                            "message", "No job found with id: " + request.getJobId()));
        }
        return ResponseEntity.ok(DownsyncJobSearchResponse.builder()
                .responseInfo(ResponseInfoFactory.createResponseInfo(request.getRequestInfo(), true))
                .job(detail)
                .build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cancelJob(String jobId, String tenantId, String createdBy) {
        try {
            jobRepository.updateJob(DownsyncGenerationJob.builder()
                    .id(jobId).tenantId(tenantId).totalRequested(0).totalSucceeded(0).totalFailed(0)
                    .status("FAILED").lastModifiedBy(createdBy)
                    .lastModifiedTime(System.currentTimeMillis()).build());
        } catch (Exception ignored) {}
    }

    private DownsyncJobConflictResponse buildConflictResponse(String tenantId, String jobId, String lockType) {
        DownsyncJobConflictResponse.JobInfo jobInfo = null;
        if (!"unknown".equals(jobId)) {
            try {
                DownsyncGenerationJob job = jobRepository.findJobById(jobId);
                if (job != null) {
                    int done = jobRepository.countLocalitiesDone(tenantId, jobId);
                    int pending = job.getTotalRequested() != null ? job.getTotalRequested() - done : 0;
                    jobInfo = DownsyncJobConflictResponse.JobInfo.builder()
                            .jobId(job.getId())
                            .tenantId(job.getTenantId())
                            .projectId(job.getProjectId())
                            .status(job.getStatus())
                            .startedAt(job.getCreatedTime())
                            .totalLocalities(job.getTotalRequested())
                            .localitiesCompleted(done)
                            .localitiesPending(Math.max(pending, 0))
                            .build();
                }
            } catch (Exception e) {
                log.warn("Could not fetch job info for conflict response: {}", e.getMessage());
            }
        }
        return DownsyncJobConflictResponse.builder()
                .code("JOB_IN_PROGRESS")
                .message(lockType + " generation already running for tenant: " + tenantId
                        + ". Check currentJob for progress details.")
                .currentJob(jobInfo)
                .build();
    }
}
