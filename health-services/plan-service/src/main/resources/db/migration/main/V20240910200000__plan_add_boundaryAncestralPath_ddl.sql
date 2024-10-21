ALTER TABLE plan
ADD boundary_ancestral_path TEXT,
ADD status character varying(64) NOT NULL DEFAULT 'VALIDATED',
ADD assignee varchar(64);
