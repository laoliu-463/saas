-- Dormant authorization foundation. No role-permission seed data is defined here.

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS authz_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS sys_permission (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_code     VARCHAR(128) NOT NULL,
    domain_code         VARCHAR(64) NOT NULL,
    resource_code       VARCHAR(64) NOT NULL,
    action_code         VARCHAR(64) NOT NULL,
    data_scope_required BOOLEAN NOT NULL DEFAULT FALSE,
    status              SMALLINT NOT NULL DEFAULT 1,
    deleted             SMALLINT NOT NULL DEFAULT 0,
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           UUID,
    update_by           UUID,
    remark              VARCHAR(500),
    CONSTRAINT ck_sys_permission_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_sys_permission_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_sys_permission_resource_code
        CHECK (resource_code ~ '^[a-z][a-z0-9-]{0,62}$'),
    CONSTRAINT ck_sys_permission_action_code
        CHECK (action_code ~ '^[a-z][a-z0-9-]{0,62}$'),
    CONSTRAINT ck_sys_permission_code_parts CHECK (
        permission_code = resource_code || ':' || action_code
        AND permission_code = LOWER(permission_code)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_permission_code
    ON sys_permission(permission_code);
CREATE INDEX IF NOT EXISTS idx_sys_permission_domain_status_deleted
    ON sys_permission(domain_code, status, deleted);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    CONSTRAINT fk_sys_role_permission_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_permission_permission
    ON sys_role_permission(permission_id);

CREATE TABLE IF NOT EXISTS sys_role_domain_scope (
    role_id     UUID NOT NULL,
    domain_code VARCHAR(64) NOT NULL,
    scope_code  VARCHAR(16) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   UUID,
    update_by   UUID,
    CONSTRAINT fk_sys_role_domain_scope_role
        FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT ck_sys_role_domain_scope_scope
        CHECK (scope_code IN ('SELF', 'GROUP', 'ALL')),
    PRIMARY KEY (role_id, domain_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_domain_scope_domain_scope
    ON sys_role_domain_scope(domain_code, scope_code);

CREATE TABLE IF NOT EXISTS sys_authz_change_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_action   VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    target_id       UUID NOT NULL,
    actor_user_id   UUID,
    before_snapshot JSONB,
    after_snapshot  JSONB,
    request_id      VARCHAR(128),
    trace_id        VARCHAR(128),
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_authz_change_log_target_time
    ON sys_authz_change_log(target_type, target_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_sys_authz_change_log_actor_time
    ON sys_authz_change_log(actor_user_id, changed_at DESC);
