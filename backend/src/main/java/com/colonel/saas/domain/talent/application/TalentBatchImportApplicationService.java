package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 达人批量导入应用层 (DDD-TALENT-04 Slice 7).
 *
 * <p>承接 Controller 的批量导入命令入口，自包含批量循环业务编排。
 * 复用 {@link TalentProfileApplicationService#create} 单条创建逻辑，
 * 每条独立 try-catch 实现 fail-isolation（单条失败不影响其他条）。
 * 1:1 等价 TalentService.batchImport(List, UUID) 60 行业务。</p>
 *
 * <p><b>业务域：</b>达人域 — 批量导入</p>
 */
@Service
public class TalentBatchImportApplicationService {

    private final TalentMapper talentMapper;
    private final TalentProfileApplicationService talentProfileApplicationService;
    private final OperationLogService operationLogService;

    public TalentBatchImportApplicationService(
            TalentMapper talentMapper,
            @Lazy TalentProfileApplicationService talentProfileApplicationService,
            OperationLogService operationLogService) {
        this.talentMapper = talentMapper;
        this.talentProfileApplicationService = talentProfileApplicationService;
        this.operationLogService = operationLogService;
    }

    /**
     * 批量导入达人账号。
     * 1:1 等价 TalentService.batchImport(List<String>, UUID) 60 行业务。
     *
     * <p>流程：循环每条账号 → 解析 douyinUid → 查重 → 委派 create。
     * 单条失败被 try-catch 捕获，继续处理后续账号（fail-isolation）。</p>
     */
    public TalentBatchImportResult batchImport(List<String> accounts, UUID operatorId) {
        if (accounts == null || accounts.isEmpty()) {
            return new TalentBatchImportResult(0, 0, 0, 0, List.of());
        }
        List<TalentBatchImportResult.TalentBatchImportItemResult> items = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (String rawAccount : accounts) {
            String account = rawAccount == null ? null : rawAccount.trim();
            if (!StringUtils.hasText(account)) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        rawAccount, "FAILED", null, "账号为空"));
                continue;
            }
            try {
                TalentInputParseResult parsed = TalentInputParser.parse(account);
                if (!StringUtils.hasText(parsed.getDouyinUid())) {
                    failed++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "FAILED", null, "无法解析达人账号"));
                    continue;
                }
                Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDouyinUid, parsed.getDouyinUid())
                        .last("limit 1"));
                if (existing != null) {
                    skipped++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "SKIPPED", existing.getId(), "达人已存在"));
                    continue;
                }
                Talent request = new Talent();
                request.setDouyinUid(parsed.getDouyinUid());
                request.setDouyinNo(parsed.getDouyinNo());
                request.setUid(parsed.getUid());
                request.setSecUid(parsed.getSecUid());
                request.setProfileUrl(parsed.getProfileUrl());
                Talent saved = talentProfileApplicationService.create(request);
                created++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "CREATED", saved.getId(), null));
                operationLogService.recordSystemAction(
                        operatorId,
                        "达人批量导入",
                        "创建达人",
                        "POST",
                        "talent",
                        saved.getId() == null ? account : saved.getId().toString(),
                        saved.getNickname(),
                        "batch_import_talents");
            } catch (RuntimeException ex) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "FAILED", null, ex.getMessage()));
            }
        }
        return new TalentBatchImportResult(accounts.size(), created, skipped, failed, items);
    }
}