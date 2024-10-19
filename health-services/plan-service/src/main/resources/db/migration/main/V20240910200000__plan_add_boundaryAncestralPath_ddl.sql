ALTER TABLE plan
ADD boundary_ancestral_path TEXT NOT NULL,
ADD status character varying(64) NOT NULL,
ADD assignee varchar(64);
