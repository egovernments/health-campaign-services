CREATE TABLE id_transaction_log (
    id SERIAL PRIMARY KEY,
    id_reference VARCHAR(64) NOT NULL,
    user_uuid VARCHAR(64) NOT NULL,
    device_uuid VARCHAR(64) NOT NULL,
    device_info JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_id_reference ON id_transaction_log (id_reference);
CREATE INDEX idx_user_uuid ON id_transaction_log (user_uuid);
CREATE INDEX idx_device_uuid ON id_transaction_log (device_uuid);
CREATE INDEX idx_timestamp ON id_transaction_log (timestamp DESC);
