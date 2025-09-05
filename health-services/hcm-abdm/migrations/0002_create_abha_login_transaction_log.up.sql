-- abha_login_transaction_log
CREATE TABLE abha_login_transaction_log
(
    id SERIAL PRIMARY KEY,
    abha_number       VARCHAR(50),
    request_type      VARCHAR(50),  -- e.g., SEND_OTP, VERIFY_OTP, CHECK_AUTH
    endpoint          TEXT,
    request_payload   JSONB,
    response_payload  JSONB,
    response_status   INT,
    error_message     TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
