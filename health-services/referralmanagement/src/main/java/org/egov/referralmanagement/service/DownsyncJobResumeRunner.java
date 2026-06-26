package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.web.models.DownsyncGenerationJob;
import org.egov.referralmanagement.web.models.DownsyncGenerationLocality;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class DownsyncJobResumeRunner implements ApplicationRunner {

    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncFileGenService service;
    @Autowired private DownsyncJobRegistry jobRegistry;
    @Autowired private JobHeartbeatScheduler heartbeat;
    @Autowired private ReferralManagementConfiguration config;

    @Override
    public void run(ApplicationArguments args) {
        List<DownsyncGenerationJob> stuckJobs = jobRepository.findInProgressJobs();
        if (stuckJobs.isEmpty()) {
            jobRegistry.markScanComplete();
            log.info("No IN_PROGRESS downsync jobs found on startup — service ready");
            return;
        }

        log.info("Found {} IN_PROGRESS job(s) to resume on startup", stuckJobs.size());

        // Populate registry BEFORE marking scan complete — occupied slots are blocked
        // the moment the scan flag flips, no race window
        stuckJobs.forEach(j -> jobRegistry.markActive(j.getTenantId(), j.getProjectId(), j.getId()));
        jobRegistry.markScanComplete();
        log.info("Startup scan complete — {} tenant(s) marked occupied, accepting new requests for free tenants",
                stuckJobs.size());

        // Resume each job in parallel — tenants are independent
        List<CompletableFuture<Void>> futures = stuckJobs.stream()
                .map(job -> CompletableFuture.runAsync(() -> resumeJob(job)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("All startup resume jobs completed");
    }

    private void resumeJob(DownsyncGenerationJob job) {
        // Heartbeat-aware claim: only win if the existing heartbeat is stale (or NULL).
        // A live competitor will have a fresh heartbeat → our claim returns 0 → we skip.
        long staleThreshold = System.currentTimeMillis()
                - (config.getHeartbeatStaleThresholdSeconds() * 1000L);
        if (!jobRepository.claimResumeJob(job.getTenantId(), job.getId(),
                job.getRowVersion(), staleThreshold)) {
            log.info("Job {} not claimable — another pod's heartbeat is fresh, or another pod " +
                     "already won the rowVersion CAS. Skipping.", job.getId());
            jobRegistry.release(job.getTenantId(), job.getProjectId());
            return;
        }
        // Our claim incremented rowVersion. From here on, the heartbeat scheduler
        // must use the NEW rowVersion (claimed value + 1) as its CAS expectation.
        long ownedRowVersion = job.getRowVersion() + 1;
        log.info("Resuming job {} for tenant {} (ownedRowVersion={})",
                job.getId(), job.getTenantId(), ownedRowVersion);

        // Sweep any IN_PROGRESS file rows under this job to FAILED. Safe because
        // job-level heartbeat was stale AND we won the claim → no live worker exists.
        // The attempt-per-row flow will INSERT a fresh attempt for each swept row.
        int swept = jobRepository.sweepStaleFilesForJob(job.getTenantId(), job.getId());
        if (swept > 0) {
            log.info("Job {} — swept {} stale IN_PROGRESS file row(s) to FAILED for retry",
                    job.getId(), swept);
        }

        // Start heartbeating BEFORE we start any work. From this moment on, any
        // other pod racing to claim will see our fresh heartbeat and skip.
        heartbeat.start(job.getTenantId(), job.getId(), ownedRowVersion);

        try {
            List<DownsyncGenerationLocality> resumable =
                    jobRepository.findResumableLocalities(job.getTenantId(), job.getId());

            if (!resumable.isEmpty()) {
                if (jobRepository.shouldRefreshMv(job.getTenantId())) {
                    log.info("Refreshing household_address_mv on resume — tenant={}", job.getTenantId());
                    jobRepository.refreshHouseholdAddressMv(job.getTenantId());
                } else {
                    log.info("Skipping household_address_mv refresh on resume — tenant={}", job.getTenantId());
                }

                List<LocalityDownsyncCriteria> registryCriteria = resumable.stream()
                        .filter(l -> "REGISTRY".equals(l.getCategory()))
                        .map(l -> LocalityDownsyncCriteria.builder()
                                .localityRowId(l.getId()).tenantId(l.getTenantId())
                                .locality(l.getLocality()).category("REGISTRY").build())
                        .toList();

                List<LocalityDownsyncCriteria> projectCriteria = resumable.stream()
                        .filter(l -> "PROJECT".equals(l.getCategory()))
                        .map(l -> LocalityDownsyncCriteria.builder()
                                .localityRowId(l.getId()).tenantId(l.getTenantId())
                                .projectId(l.getProjectId()).locality(l.getLocality())
                                .rootProjectId(job.getProjectId()).category("PROJECT").build())
                        .toList();

                log.info("Job {} — resuming {} registry + {} project localities",
                        job.getId(), registryCriteria.size(), projectCriteria.size());

                CompletableFuture<Void> regFuture = registryCriteria.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.runAsync(() -> service.generateRegistry(registryCriteria, job.getId()));
                CompletableFuture<Void> prjFuture = projectCriteria.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.runAsync(() -> service.generateProject(projectCriteria, job.getId()));

                CompletableFuture.allOf(regFuture, prjFuture).join();

            } else {
                log.info("Job {} — all localities already done, finalizing", job.getId());
            }

            Map<String, Integer> counts = jobRepository.countOutcomes(job.getTenantId(), job.getId());
            int succeeded = counts.getOrDefault("succeeded", 0);
            int failed    = counts.getOrDefault("failed", 0);
            String status = failed == 0 ? "COMPLETED" : (succeeded == 0 ? "FAILED" : "PARTIAL_FAILURE");

            jobRepository.updateJob(DownsyncGenerationJob.builder()
                    .id(job.getId()).tenantId(job.getTenantId())
                    .totalRequested(job.getTotalRequested() != null ? job.getTotalRequested() : succeeded + failed)
                    .totalSucceeded(succeeded).totalFailed(failed)
                    .status(status).lastModifiedBy("system-resume")
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());

            log.info("Job {} resume complete — status={}", job.getId(), status);
        } catch (Exception e) {
            log.error("Resume failed for job {}: {}", job.getId(), e.getMessage(), e);
        } finally {
            // Stop heartbeating BEFORE releasing the registry slot. Once heartbeat
            // stops bumping, the row's lastHeartbeat will age past staleness and
            // any future pod restart can reclaim if needed.
            heartbeat.stop(job.getId());
            jobRegistry.release(job.getTenantId(), job.getProjectId());
        }
    }
}
