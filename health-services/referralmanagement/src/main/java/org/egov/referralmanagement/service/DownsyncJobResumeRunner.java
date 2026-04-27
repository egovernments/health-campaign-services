package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
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
        log.info("Resuming job {} for tenant {}", job.getId(), job.getTenantId());
        try {
            List<DownsyncGenerationLocality> resumable =
                    jobRepository.findResumableLocalities(job.getTenantId(), job.getId());

            if (!resumable.isEmpty()) {
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
                    .totalRequested(succeeded + failed)
                    .totalSucceeded(succeeded).totalFailed(failed)
                    .status(status).lastModifiedBy("system-resume")
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());

            log.info("Job {} resume complete — status={}", job.getId(), status);
        } catch (Exception e) {
            log.error("Resume failed for job {}: {}", job.getId(), e.getMessage(), e);
        } finally {
            jobRegistry.release(job.getTenantId(), job.getProjectId());
        }
    }
}
