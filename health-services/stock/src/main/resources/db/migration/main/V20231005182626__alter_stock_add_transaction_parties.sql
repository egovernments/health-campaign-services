ALTER TABLE STOCK ADD COLUMN IF NOT EXISTS senderType character varying(128);
ALTER TABLE STOCK ADD COLUMN IF NOT EXISTS receiverType character varying(128);
ALTER TABLE STOCK ADD COLUMN IF NOT EXISTS senderid character varying(128);
ALTER TABLE STOCK ADD COLUMN IF NOT EXISTS receiverid character varying(128);
