ALTER TABLE eg_pgr_service_v2
    ADD COLUMN dateOfComplaint bigint,
    ADD COLUMN administrativeArea character varying(256),
    ADD COLUMN selfComplaint boolean,
    ADD COLUMN supervisorId character varying(256),
    ADD COLUMN additionalAttachments character varying(256);
