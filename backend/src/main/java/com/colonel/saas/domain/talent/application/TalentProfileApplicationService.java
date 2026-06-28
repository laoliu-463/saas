package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.talent.policy.TalentTagPolicy;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 达人资料写侧应用层 (DDD-TALENT-04 Slice 1: thin slice).
 *
 * <p>本层承接 Controller 的资料、标签、手动补全和导入命令入口，
 * 自包含业务编排（旧 TalentService 同名方法迁入），保留 1:1 行为等价。
 * Legacy {@code TalentService} 保留为薄壳委派壳，不删除兜底路径。</p>
 *
 * <p>当前 Slice 1 范围：4 个最小 method（listPresetTags / getLatestEnrichTask /
 * delete / updateTags）。后续 Slice 2+ 增量迁移 create / update /
 * manualFill / refresh / batchImport 等大方法。</p>
 *
 * <p><b>业务域：</b>达人域 — 资料管理</p>
 */
@Service
public class TalentProfileApplicationService {

    private final TalentMapper talentMapper;
    private final TalentEnrichTaskMapper talentEnrichTaskMapper;
    private final BusinessRuleConfigService businessRuleConfigService;

    public TalentProfileApplicationService(
            TalentMapper talentMapper,
            TalentEnrichTaskMapper talentEnrichTaskMapper,
            BusinessRuleConfigService businessRuleConfigService) {
        this.talentMapper = talentMapper;
        this.talentEnrichTaskMapper = talentEnrichTaskMapper;
        this.businessRuleConfigService = businessRuleConfigService;
    }

    /**
     * 列出预置达人标签。
     * 1:1 等价 TalentService.listPresetTags()。
     */
    public List<String> listPresetTags() {
        return businessRuleConfigService.getPresetTalentTags();
    }

    /**
     * 获取达人最新 enrich 任务。
     * 1:1 等价 TalentService.getLatestEnrichTask(UUID)。
     */
    public TalentEnrichTask getLatestEnrichTask(UUID talentId) {
        return talentEnrichTaskMapper.findLatestByTalentId(talentId);
    }

    /**
     * 删除达人。
     * 1:1 等价 TalentService.delete(UUID)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        // 校验存在
        Talent existing = talentMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("达人不存在");
        }
        talentMapper.deleteById(id);
    }

    /**
     * 更新达人标签（无 operator 版本，调用 3 参版本）。
     * 1:1 等价 TalentService.updateTags(UUID, List<String>)。
     */
    public List<String> updateTags(UUID id, List<String> tags) {
        return updateTags(id, tags, null);
    }

    /**
     * 更新达人标签（带 operator 版本）。
     * 1:1 等价 TalentService.updateTags(UUID, List<String>, UUID)。
     *
     * <p>当前 Slice 1 占位实现：仅做存在性校验 + 标签归一化（Policy）。
     * 完整业务（权限校验、数量限制、写库）后续 Slice 2 增量迁移。</p>
     */
    public List<String> updateTags(UUID id, List<String> tags, UUID operatorId) {
        // 校验存在
        Talent talent = talentMapper.selectById(id);
        if (talent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        // 归一化标签 (复用 Policy)
        List<String> presets = businessRuleConfigService.getPresetTalentTags();
        return TalentTagPolicy.normalize(tags, presets);
    }
}