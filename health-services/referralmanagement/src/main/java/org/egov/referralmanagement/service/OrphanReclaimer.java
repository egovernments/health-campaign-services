package org.egov.referralmanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.web.models.DownsyncGenerationJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically scans the tenant schemas for {@code IN_PROGRESS} downsync jobs
 * whose owner has gone away (heartbeat NULL or older than the stale threshold)
 * and drives their resumption on this pod.
 *
 * <p>Complements {@link DownsyncJobResumeRunner}: the resume runner only fires
 * once, at Spring startup. If a pod crashes DURING its lifetime and no other
 * pod restarts within the recovery window, its orphaned jobs would sit idle
 * until some external event (HPA scale, rolling restart) spawned a fresh pod
 * to run its own startup scan. This reclaimer closes that gap by rescanning
 * every {@code egov.downsync.reclaimer.interval.seconds} seconds on every
 * live pod.
 *
 * <h3>Design</h3>
 * <ol>
 *   <li>Every tick: query up to {@code batchSize} stale-heartbeat orphans
 *       across all configured schemas, ordered by createdTime ASC.</li>
 *   <li>Iterate the batch and attempt {@link DownsyncGenerationJobRepository#claimResumeJob}
 *       on each — the row-level CAS on {@code rowVersion} decides which pod
 *       wins each specific job. Multiple pods scanning the same list will
 *       naturally distribute the work: pod A wins row 1, pod B loses row 1
 *       (CAS returns 0) and moves on to row 2, wins that, and so on.</li>
 *   <li>The first job whose claim succeeds is dispatched to
 *       {@link DownsyncJobResumeRunner#resumeAlreadyClaimed} — the same
 *       sweep-and-resume flow the startup runner uses. Processing is
 *       synchronous within the tick, so this pod dedicates its ward-pool
 *       (16 workers by default) to that one job until completion.</li>
 *   <li>{@code @Scheduled(fixedDelay=...)} means the next tick fires
 *       {@code interval} seconds AFTER the current tick returns — natural
 *       self-throttling under load, no separate semaphore needed.</li>
 * </ol>
 *
 * <h3>Failure isolation</h3>
 * <ul>
 *   <li>Query failure — logged, method returns, next tick tries again.</li>
 *   <li>Claim CAS lost — logged at debug, loop moves to next candidate.</li>
 *   <li>Resume failure — the existing try/catch/finally in
 *       {@link DownsyncJobResumeRunner#resumeAlreadyClaimed} handles per-job
 *       errors and cleans up heartbeat + registry slot.</li>
 *   <li>Unhandled runtime exception anywhere — wrapped by the outer try/catch
 *       so the {@code @Scheduled} task is never cancelled by Spring's default
 *       error handler.</li>
 * </ul>
 */
@Component
@Slf4j
public class OrphanReclaimer {

    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncJobResumeRunner resumeRunner;
    @Autowired private ReferralManagementConfiguration config;

    /** How often to scan; matches the fixedDelay attribute below via SpEL. */
    @Value("${egov.downsync.reclaimer.interval.seconds:60}")
    private int intervalSeconds;

    /** Max orphans a single tick will look at; keeps mass-recovery bounded. */
    @Value("${egov.downsync.reclaimer.batch.size:5}")
    private int batchSize;

    /**
     * @Scheduled uses SpEL to read from @Value at bean init — the value passed
     * to fixedDelayString is millis. Falls back to 60000 (60s) if unset.
     */
    @Scheduled(fixedDelayString = "${egov.downsync.reclaimer.interval.ms:60000}")
    public void tick() {
        try {
            long staleThreshold = System.currentTimeMillis()
                    - (config.getHeartbeatStaleThresholdSeconds() * 1000L);

            List<DownsyncGenerationJob> candidates;
            try {
                candidates = jobRepository.findStaleHeartbeatJobs(staleThreshold, batchSize);
            } catch (Exception e) {
                log.warn("OrphanReclaimer — scan failed: {} (will retry next tick)", e.getMessage());
                return;
            }
            if (candidates.isEmpty()) return;

            log.info("OrphanReclaimer — {} stale-heartbeat orphan(s) visible this tick", candidates.size());

            for (DownsyncGenerationJob job : candidates) {
                boolean won;
                try {
                    won = jobRepository.claimResumeJob(
                            job.getTenantId(), job.getId(),
                            job.getRowVersion(), staleThreshold);
                } catch (Exception e) {
                    // Transient DB error on this row — try the next candidate.
                    log.warn("OrphanReclaimer — claim of {} tenant={} threw: {} (skipping)",
                            job.getId(), job.getTenantId(), e.getMessage());
                    continue;
                }

                if (!won) {
                    log.debug("OrphanReclaimer — job {} tenant={} already claimed by another " +
                                    "pod (CAS lost); trying next candidate",
                            job.getId(), job.getTenantId());
                    continue;
                }

                log.info("OrphanReclaimer — claimed orphan job {} tenant={}, resuming",
                        job.getId(), job.getTenantId());
                try {
                    resumeRunner.resumeAlreadyClaimed(job, job.getRowVersion() + 1);
                } catch (Exception e) {
                    // resumeAlreadyClaimed has its own try/catch/finally that logs and cleans up.
                    // This is a belt-and-braces guard against anything that manages to escape.
                    log.error("OrphanReclaimer — resume of job {} tenant={} failed after claim: {}",
                            job.getId(), job.getTenantId(), e.getMessage(), e);
                }
                // One job per tick — the tick has done its work. Return so the next tick
                // fires only after this pod is ready to take on another orphan.
                return;
            }

            log.debug("OrphanReclaimer — all {} candidates already claimed by other pods this tick",
                    candidates.size());
        } catch (RuntimeException e) {
            // Never let anything escape @Scheduled — Spring's default error handler
            // cancels the task on unhandled exceptions.
            log.error("OrphanReclaimer — tick failed with unhandled exception, " +
                    "will retry next interval: {}", e.getMessage(), e);
        }
    }
}
