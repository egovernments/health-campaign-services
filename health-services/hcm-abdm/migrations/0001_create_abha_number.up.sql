-- Create the abha_number table

CREATE TABLE IF NOT EXISTS abha_number (
    id BIGSERIAL PRIMARY KEY,
    external_id UUID UNIQUE NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    abha_number TEXT UNIQUE,
    health_id TEXT UNIQUE,
    email TEXT,
    first_name TEXT,
    middle_name TEXT,
    last_name TEXT,
    profile_photo TEXT,
    access_token TEXT,
    refresh_token TEXT,
    address TEXT,
    date_of_birth TEXT,
    district TEXT,
    gender TEXT,
    name TEXT,
    pincode TEXT,
    state TEXT,
    mobile TEXT,
    created_by BIGINT,
    last_modified_by BIGINT,
    created_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    modified_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    new BOOLEAN DEFAULT FALSE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_abha_number_created_date ON abha_number(created_date);
CREATE INDEX IF NOT EXISTS idx_abha_number_modified_date ON abha_number(modified_date);
CREATE INDEX IF NOT EXISTS idx_abha_number_deleted ON abha_number(deleted);
CREATE INDEX IF NOT EXISTS idx_abha_number_email ON abha_number(email);
CREATE INDEX IF NOT EXISTS idx_abha_number_mobile ON abha_number(mobile);
CREATE INDEX IF NOT EXISTS idx_abha_number_name ON abha_number(name);
CREATE INDEX IF NOT EXISTS idx_abha_number_state ON abha_number(state);
CREATE INDEX IF NOT EXISTS idx_abha_number_district ON abha_number(district);
CREATE INDEX IF NOT EXISTS idx_abha_number_access_token ON abha_number(access_token);