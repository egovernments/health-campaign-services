ALTER TABLE plan_configuration ADD status character varying(64);
UPDATE plan_configuration SET status = 'DRAFT' WHERE status IS NULL;
