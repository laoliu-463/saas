package com.colonel.saas.dto.talent;

import java.util.List;
import java.util.UUID;

/**
 * 达人批量导入结果 DTO。
 * <p>
 * 返回批量导入操作的汇总统计及每条记录的处理结果。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
public record TalentBatchImportResult(
        /** 总导入条数 */
        int total,
        /** 新建成功条数 */
        int created,
        /** 跳过条数（已存在） */
        int skipped,
        /** 失败条数 */
        int failed,
        /** 每条记录的详细处理结果 */
        List<TalentBatchImportItemResult> items) {

    /**
     * 批量导入单项结果。
     */
    public record TalentBatchImportItemResult(
            /** 原始账号标识 */
            String account,
            /** 处理状态（created/skipped/failed） */
            String status,
            /** 导入成功后的达人 ID */
            UUID talentId,
            /** 处理结果说明信息 */
            String message) {
    }
}
