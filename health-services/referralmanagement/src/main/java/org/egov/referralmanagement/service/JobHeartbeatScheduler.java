package org.egov.referralmanagement.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Maintains a periodic heartbeat on each downsync_generation_job row that this
 * pod currently owns. The heartbeat is the primary signal another pod uses to
 * decide whether a job is abandoned: if {@code lastHeartbeat} is older than
 * {@code egov.downsync.heartbeat.stale.threshold.seconds}, another pod can
 * claim the job and run a sweep against its IN_PROGRESS file rows.
 *
 * <p>Each registered job runs as a single periodic task. The task is a
 * compare-and-set: it only bumps {@code lastHeartbeat} when the row's
 * {@code rowVersion} still matches the value this pod claimed with. If another
 * pod has legitimately stolen ownership (heartbeat went stale, the other pod
 * claimed and incremented rowVersion), our heartbeat returns 0 and this
 * scheduler self-cancels — preventing zombie heartbeats from a JVM that briefly
 * hung and woke up after losing the claim.
 *
 * <p>Heartbeats are cancelled explicitly on job finalization via {@link #stop}.
 */
@Component
@Slf4j
public class JobHeartbeatScheduler {

    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private ReferralManagementConfiguration config;

    private ScheduledExecutorService executor;

    /** Tracks the active heartbeat task per jobId so we can cancel it on stop. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> active = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        // Single-threaded scheduler is plenty — heartbeats are cheap UPDATEs and
        // run at multi-second intervals. A daemon thread factory ensures the
        // scheduler doesn't keep the JVM alive on shutdown.
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "downsync-heartbeat");
            t.setDaemon(true);
            return t;
        });
        log.info("JobHeartbeatScheduler initialised (interval={}s, staleThreshold={}s)",
                config.getHeartbeatIntervalSeconds(), config.getHeartbeatStaleThresholdSeconds());
    }

    @PreDestroy
    void shutdown() {
        if (executor != null) executor.shutdownNow();
    }

    /**
     * Begin heartbeating for a freshly-owned job. Idempotent — calling start()
     * twice for the same jobId leaves the existing task running.
     *
     * @param tenantId       tenant schema where the job row lives
     * @param jobId          the job id
     * @param claimedRowVersion the rowVersion this pod sees as its own; the
     *                          heartbeat self-cancels if the row's rowVersion
     *                          ever differs (someone stole ownership)
     */
    public void start(String tenantId, String jobId, long claimedRowVersion) {
        active.computeIfAbsent(jobId, id -> {
            long interval = config.getHeartbeatIntervalSeconds();
            Runnable task = () -> tick(tenantId, jobId, claimedRowVersion);
            // First beat after one interval — the row's lastHeartbeat was set
            // to "now" by the caller's claim/insert, so we don't need to bump it
            // immediately.
            ScheduledFuture<?> f = executor.scheduleAtFixedRate(task, interval, interval, TimeUnit.SECONDS);
            log.info("Heartbeat started for job {} (rowVersion={}, every {}s)", jobId, claimedRowVersion, interval);
            return f;
        });
    }

    /** Cancel heartbeating for a job — call from the controller / resume runner's finally block. */
    public void stop(String jobId) {
        ScheduledFuture<?> f = active.remove(jobId);
        if (f != null) {
            f.cancel(false);
            log.info("Heartbeat stopped for job {}", jobId);
        }
    }

    private void tick(String tenantId, String jobId, long claimedRowVersion) {
        try {
            boolean stillMine = jobRepository.bumpJobHeartbeat(tenantId, jobId, claimedRowVersion);
            if (!stillMine) {
                log.warn("Heartbeat for job {} returned 0 rows — ownership lost (another pod claimed, " +
                         "or job finalised). Self-cancelling.", jobId);
                stop(jobId);
            }
        } catch (Exception e) {
            // Don't let one transient DB hiccup cancel the heartbeat — that would
            // hand ownership to a competing pod prematurely. Log and try again
            // next tick.
            log.warn("Heartbeat for job {} failed transiently: {}", jobId, e.getMessage());
        }
    }
}
