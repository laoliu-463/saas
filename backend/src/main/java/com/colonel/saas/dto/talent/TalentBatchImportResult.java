package com.colonel.saas.dto.talent;

import java.util.List;
import java.util.UUID;

public record TalentBatchImportResult(
        int total,
        int created,
        int skipped,
        int failed,
        List<TalentBatchImportItemResult> items) {

    public record TalentBatchImportItemResult(
            String account,
            String status,
            UUID talentId,
            String message) {
    }
}
