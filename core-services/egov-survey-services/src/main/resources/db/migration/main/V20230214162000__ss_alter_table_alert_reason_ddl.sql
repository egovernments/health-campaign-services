ALTER TABLE eg_ss_question ADD COLUMN extraInfo character varying(256);

ALTER TABLE eg_ss_answer ADD COLUMN additionalComments character varying(256);
ALTER TABLE eg_ss_answer ADD COLUMN entityId character varying(128);

ALTER TABLE eg_ss_survey ADD COLUMN tags character varying(2048);
ALTER TABLE eg_ss_survey ADD COLUMN entityType character varying(128);
ALTER TABLE eg_ss_survey ADD COLUMN entityId character varying(128);