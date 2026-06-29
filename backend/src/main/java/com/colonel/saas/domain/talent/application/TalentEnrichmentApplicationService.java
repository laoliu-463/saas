package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 达人信息补全调度应用层 (DDD-TALENT-04 Slice 9).
 *
 * <p>承接定时任务 (TalentWeeklyRefreshJob) 触发的"查找活跃达人"命令入口。
 * 1:1 等价 TalentService.findActiveTalentIdsForRefresh() 9 行业务。
 * 返回未删除、启用状态的活跃达人 ID 列表，供 Job 调度使用。</p>
 *
 * <p><b>业务域：</b>达人域 — 信息补全调度</p>
 */
@Service
public class TalentEnrichmentApplicationService {

    private final TalentMapper talentMapper;

    public TalentEnrichmentApplicationService(TalentMapper talentMapper) {
        this.talentMapper = talentMapper;
    }

    /**
     * 查找所有需要刷新的活跃达人 ID 列表。
     * 1:1 等价 TalentService.findActiveTalentIdsForRefresh() 9 行业务。
     *
     * <p>过滤条件：未删除 (deleted=0) + 启用 (status=1)。</p>
     */
    public List<UUID> findActiveTalentIdsForRefresh() {
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1))
                .stream()
                .map(Talent::getId)
                .filter(Objects::nonNull)
                .toList();
    }
}