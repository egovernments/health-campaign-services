CREATE TABLE eg_push_device_tokens(

  id character varying(256) NOT NULL,
  userid character varying(256) NOT NULL,
  devicetoken character varying(512) NOT NULL,
  devicetype character varying(64) NOT NULL,
  tenantid character varying(256) NOT NULL,
  active boolean NOT NULL DEFAULT true,
  createdby character varying(256) NOT NULL,
  createdtime bigint NOT NULL,
  lastmodifiedby character varying(256),
  lastmodifiedtime bigint,

  CONSTRAINT pk_eg_push_device_tokens PRIMARY KEY (id),
  CONSTRAINT uk_eg_push_device_tokens UNIQUE (userid, devicetoken)

);

CREATE INDEX idx_eg_push_device_tokens_userid ON eg_push_device_tokens(userid);
CREATE INDEX idx_eg_push_device_tokens_active ON eg_push_device_tokens(active);
