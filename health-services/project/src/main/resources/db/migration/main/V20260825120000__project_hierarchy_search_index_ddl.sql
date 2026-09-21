-- Ancestor and descendant searches match projecthierarchy by prefix; a plain btree is only
-- usable for LIKE under the C collation, hence varchar_pattern_ops.
CREATE INDEX IF NOT EXISTS idx_project_projecthierarchy ON project (projecthierarchy varchar_pattern_ops);
