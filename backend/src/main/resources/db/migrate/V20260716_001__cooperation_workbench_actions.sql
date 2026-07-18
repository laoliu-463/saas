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
