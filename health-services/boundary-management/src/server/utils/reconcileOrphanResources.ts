import config from "../config";
import { logger } from "./logger";
import { executeQuery, getTableName } from "./db";
import { resourceDataStatuses } from "../config/constants";

// Only "data-accepted" (an in-flight CREATE) is heartbeat-backed and can get stuck by a pod restart,
// so it is the only status we tick and sweep. "validation-started" (action=validate) is deliberately
// EXCLUDED: validate runs in-request with no heartbeat, so sweeping it would false-fail live/legit
// validations. Scoping to create keeps the reconciler correct and never touches the validate flow.
const CREATE_IN_PROGRESS = [resourceDataStatuses.accepted]; // "data-accepted"

/**
 * HEARTBEAT — called for the duration of a detached create (boundaryBulkUpload). Every heartbeatMs
 * it stamps lastModifiedTime = now on the resource row (only while still in the data-accepted (create-in-progress) status),
 * so a genuinely-running create keeps a fresh "still alive" marker. This is metadata only: it never
 * touches codegen, the boundary tree, entities, relationships or validation, so the created data is
 * byte-for-byte identical whether or not the heartbeat runs. Best-effort — a failed tick is logged
 * and ignored; the interval is unref'd so it can never keep the process alive.
 *
 * When the owning pod restarts mid-run the ticks stop; the reconciler below then flips the row to
 * 'failed' once it has been stale for stalenessMs. A live run keeps ticking, so it is never touched.
 */
export function startResourceHeartbeat(request: any): NodeJS.Timeout | null {
  try {
    if (!config?.orphanReconcile?.enabled) return null;
    if (config?.isEnvironmentCentralInstance) return null; // per-tenant schema not resolvable at this layer
    const id = request?.body?.ResourceDetails?.id;
    const tenantId = request?.body?.ResourceDetails?.tenantId;
    if (!id) return null;
    const table = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, tenantId || "");
    const tick = async () => {
      try {
        // Only tick while still in-progress: once completed/failed the WHERE matches nothing (no-op),
        // so a heartbeat can never resurrect or clobber a finished resource.
        await executeQuery(
          `UPDATE ${table} SET lastModifiedTime = $1 WHERE id = $2 AND status = ANY($3)`,
          [Date.now(), id, CREATE_IN_PROGRESS]
        );
      } catch (e: any) {
        logger.warn(`resource heartbeat tick failed for ${id}: ${e?.message || e}`);
      }
    };
    const handle = setInterval(tick, config.orphanReconcile.heartbeatMs);
    if (typeof (handle as any)?.unref === "function") (handle as any).unref();
    return handle;
  } catch (e: any) {
    logger.warn(`startResourceHeartbeat failed (ignored): ${e?.message || e}`);
    return null;
  }
}

export function stopResourceHeartbeat(handle: NodeJS.Timeout | null): void {
  if (handle) {
    try { clearInterval(handle); } catch { /* ignore */ }
  }
}

/**
 * RECONCILER — one sweep: mark as 'failed' any resource still in the data-accepted (create-in-progress) status whose
 * lastModifiedTime is older than stalenessMs, i.e. whose heartbeat has stopped because the owning
 * pod restarted mid-create. This unblocks the UI (which otherwise polls the stuck row forever) and
 * makes a manual re-upload a clean, deliberate retry (the create path is idempotent, so re-upload
 * converges). Bounded single UPDATE; best-effort (never throws to the caller).
 *
 * MULTI-REPLICA SAFE (1 pod today, correct if scaled to N>1): the heartbeat is per-resource, so each
 * pod keeps ITS OWN in-flight runs fresh; this sweep only fails a resource whose heartbeat has actually
 * stopped, so a live run on another pod (fresh ticks) is never touched, and the UPDATE is idempotent
 * (WHERE status=data-accepted) when several pods sweep at once. DO NOT replace this with a startup-only
 * "fail all data-accepted on boot": that is safe only at replicas=1 and would false-fail other pods'
 * live runs once scaled.
 */
export async function reconcileOrphanedResources(): Promise<void> {
  try {
    if (!config?.orphanReconcile?.enabled) return;
    if (config?.isEnvironmentCentralInstance) return;
    // Never fail faster than 3x the heartbeat, even if stalenessMs is misconfigured below heartbeatMs
    // (that would risk failing a live run between two ticks).
    const stalenessMs: number = Math.max(config.orphanReconcile.stalenessMs, config.orphanReconcile.heartbeatMs * 3);
    const now = Date.now();
    const cutoff = now - stalenessMs;
    const table = getTableName(config.DB_CONFIG.DB_RESOURCE_DETAILS_TABLE_NAME, "");
    const note = JSON.stringify({
      error:
        `Marked failed by orphan reconciler: no processing heartbeat for over ` +
        `${Math.round(stalenessMs / 1000)}s, indicating the create pod restarted mid-run. ` +
        `Re-upload the same sheet to retry (the create is idempotent).`,
    });
    // Mixed-case identifiers fold to the lowercase DDL columns; ::jsonb keeps the merge robust.
    const result = await executeQuery(
      `UPDATE ${table}
       SET status = $1,
           lastModifiedTime = $2,
           additionalDetails = COALESCE(additionalDetails::jsonb, '{}'::jsonb) || $3::jsonb
       WHERE status = ANY($4) AND lastModifiedTime < $5
       RETURNING id, tenantId, hierarchyType`,
      [resourceDataStatuses.failed, now, note, CREATE_IN_PROGRESS, cutoff]
    );
    const rows = result?.rows || [];
    if (rows.length > 0) {
      const summary = rows.map((r: any) => `${r.id}(${r.hierarchytype}/${r.tenantid})`).join(", ");
      logger.info(
        `Orphan reconciler: marked ${rows.length} abandoned boundary resource(s) '${resourceDataStatuses.failed}' ` +
        `(no heartbeat > ${Math.round(stalenessMs / 1000)}s): ${summary}`
      );
    }
  } catch (e: any) {
    logger.error(`Orphan reconciler failed (non-fatal): ${e?.message || e}`);
  }
}

/** Start the reconciler: one sweep at boot + a periodic sweep every sweepIntervalMs. */
export function startOrphanReconciler(): void {
  if (!config?.orphanReconcile?.enabled) {
    logger.info("Orphan-resource reconciler disabled (BOUNDARY_ORPHAN_RECONCILE_ENABLED=false).");
    return;
  }
  if (config?.isEnvironmentCentralInstance) {
    logger.warn("Orphan-resource reconciler skipped on central-instance deployment (per-tenant schemas not enumerable here).");
    return;
  }
  reconcileOrphanedResources().catch(() => { /* logged inside */ });
  const handle = setInterval(() => {
    reconcileOrphanedResources().catch(() => { /* logged inside */ });
  }, config.orphanReconcile.sweepIntervalMs);
  if (typeof (handle as any)?.unref === "function") (handle as any).unref();
  logger.info(
    `Orphan-resource reconciler started: sweep every ${config.orphanReconcile.sweepIntervalMs}ms, ` +
    `staleness ${config.orphanReconcile.stalenessMs}ms, heartbeat ${config.orphanReconcile.heartbeatMs}ms.`
  );
}
