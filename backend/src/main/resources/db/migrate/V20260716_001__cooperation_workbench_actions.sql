-- Cooperation workbench action persistence.
CREATE TABLE IF NOT EXISTS sample_private_note (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_request_id UUID NOT NULL,
    user_id           UUID NOT NULL,
    content           VARCHAR(200) NOT NULL,
    version           INTEGER NOT NULL DEFAULT 0,
    deleted           SMALLINT NOT NULL DEFAULT 0,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         UUID,
    update_by         UUID,
    CONSTRAINT fk_sample_private_note_request
        FOREIGN KEY (sample_request_id) REFERENCES sample_request(id),
    CONSTRAINT fk_sample_private_note_user
        FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sample_private_note_owner
    ON sample_private_note(sample_request_id, user_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_complaint (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sample_request_id UUID NOT NULL,
    talent_id        UUID NOT NULL,
    product_id       UUID NOT NULL,
    reporter_user_id UUID NOT NULL,
    reason_code      VARCHAR(64) NOT NULL,
    content          TEXT NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    version          INTEGER NOT NULL DEFAULT 0,
    deleted          SMALLINT NOT NULL DEFAULT 0,
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        UUID,
    update_by        UUID,
    CONSTRAINT fk_talent_complaint_request
        FOREIGN KEY (sample_request_id) REFERENCES sample_request(id),
    CONSTRAINT fk_talent_complaint_talent
        FOREIGN KEY (talent_id) REFERENCES talent(id),
    CONSTRAINT fk_talent_complaint_product
        FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_talent_complaint_reporter
        FOREIGN KEY (reporter_user_id) REFERENCES sys_user(id)
);

CREATE INDEX IF NOT EXISTS idx_talent_complaint_talent_status
    ON talent_complaint(talent_id, status)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_complaint_attachment (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id  UUID NOT NULL,
    storage_key   VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(128) NOT NULL,
    file_size     BIGINT NOT NULL,
    sha256        CHAR(64) NOT NULL,
    deleted       SMALLINT NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     UUID,
    update_by     UUID,
    CONSTRAINT fk_talent_complaint_attachment_complaint
        FOREIGN KEY (complaint_id) REFERENCES talent_complaint(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_complaint_attachment_storage_key
    ON talent_complaint_attachment(storage_key);
CREATE INDEX IF NOT EXISTS idx_talent_complaint_attachment_complaint
    ON talent_complaint_attachment(complaint_id)
    WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS talent_complaint_reminder (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id      UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    read_at           TIMESTAMP,
    version           INTEGER NOT NULL DEFAULT 0,
    deleted           SMALLINT NOT NULL DEFAULT 0,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by         UUID,
    update_by         UUID,
    CONSTRAINT fk_talent_complaint_reminder_complaint
        FOREIGN KEY (complaint_id) REFERENCES talent_complaint(id),
    CONSTRAINT fk_talent_complaint_reminder_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES sys_user(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_talent_complaint_reminder_recipient
    ON talent_complaint_reminder(complaint_id, recipient_user_id)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_talent_complaint_reminder_recipient_read
    ON talent_complaint_reminder(recipient_user_id, read_at)
    WHERE deleted = 0;
