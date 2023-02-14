ALTER TABLE eg_ss_question ADD COLUMN alert character varying(256);
ALTER TABLE eg_ss_question ADD COLUMN alertOnOption character varying(128);
ALTER TABLE eg_ss_question ADD COLUMN reasonOnOption character varying(128);

ALTER TABLE eg_ss_answer ADD COLUMN reason character varying(256);

ALTER TABLE eg_ss_survey ADD COLUMN tag text[];
ALTER TABLE eg_ss_survey ADD COLUMN entityType character varying(128);