-- Safe for historical data only: in pre-reconciler code mappingId was set exclusively on
-- successful project-service creation (no speculative assignment, no adopt-existing pre-pass).
-- Therefore failed + mappingId IS NOT NULL unambiguously means a demap attempt failed.
-- Do NOT apply this heuristic in runtime code; use explicit status columns instead.
UPDATE eg_cm_campaign_mapping_data
  SET status = 'deMapFailed'
  WHERE status = 'failed'
    AND mappingId IS NOT NULL;

