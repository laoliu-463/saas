-- ColonelPartnerSyncService.loadFromPickSourceMapping 查询 pick_source_mapping.colonel_name。
ALTER TABLE pick_source_mapping
    ADD COLUMN IF NOT EXISTS colonel_name VARCHAR(256);
