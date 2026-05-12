-- Bootstrap minimum auth data so OAuth + userInfo-validating APIs work.
-- Loaded automatically by Postgres on fresh-volume init (after 01-full-dump.sql).
-- Idempotent: uses CREATE TABLE IF NOT EXISTS and ON CONFLICT DO NOTHING.

-- 1. Spring Security OAuth2 client table (standard schema)
CREATE TABLE IF NOT EXISTS public.oauth_client_details (
    client_id               VARCHAR(256) PRIMARY KEY,
    resource_ids            VARCHAR(256),
    client_secret           VARCHAR(256),
    scope                   VARCHAR(256),
    authorized_grant_types  VARCHAR(256),
    web_server_redirect_uri VARCHAR(256),
    authorities             VARCHAR(256),
    access_token_validity   INTEGER,
    refresh_token_validity  INTEGER,
    additional_information  VARCHAR(4096),
    autoapprove             VARCHAR(256)
);

-- 2. Default OAuth client (egov-user-client / egov-user-secret)
INSERT INTO public.oauth_client_details
    (client_id, client_secret, scope, authorized_grant_types,
     access_token_validity, refresh_token_validity)
VALUES
    ('egov-user-client',
     'egov-user-secret',
     'read,write',
     'password,refresh_token,authorization_code,client_credentials',
     28800, 86400)
ON CONFLICT (client_id) DO NOTHING;

-- 3. Single admin user — used as RequestInfo.userInfo on every API call.
--    Password column is required NOT NULL; we store a BCrypt placeholder.
--    Password-grant login won't work (placeholder is not a real hash of any known
--    cleartext), but any API that only validates uuid / tenantid / role membership
--    will accept this user. Replace with a real BCrypt hash if you need login.
INSERT INTO public.eg_user
    (id, uuid, tenantid, username, password, name, type, active,
     mobilenumber, version, createddate, lastmodifieddate)
VALUES
    (1,
     'dc6fffba-8f0a-460f-aeeb-6f7e5b2fa7f3',
     'mz',
     'SYSTEM',
     '$2a$10$sCAU70UOb5cO0Jqe.6VCOONNrwZrvzE4qPW91OBDqLZU4zdLsERfm',
     'System Admin',
     'EMPLOYEE',
     true,
     '9999999999',
     0,
     NOW(),
     NOW())
ON CONFLICT (id, tenantid) DO NOTHING;

-- 4. Role + role-mapping so role-based access checks pass.
INSERT INTO public.eg_role (id, tenantid, code, name, description, version, createddate)
VALUES
    (1, 'mz', 'SUPERUSER', 'Super User', 'Full access', 0, NOW())
ON CONFLICT (id, tenantid) DO NOTHING;

INSERT INTO public.eg_userrole_v1 (user_id, user_tenantid, role_code, role_tenantid, lastmodifieddate)
VALUES
    (1, 'mz', 'SUPERUSER', 'mz', NOW())
ON CONFLICT DO NOTHING;
