package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.domain.talent.application.ExclusiveTalentApplicationService;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 独家达人评估遗留服务（DDD-TALENT-004 兼容层）。
 *
 * <p>评估逻辑委托 {@link ExclusiveTalentApplicationService}；
 * 当前月独家归属查询保留 mapper 直接读（只读、短链路、不需要事件）。</p>
 */
@Slf4j
@Service
@Primary
public class ExclusiveTalentService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExclusiveTalentApplicationService applicationService;
    private final ExclusiveTalentMapper exclusiveTalentMapper;

    public ExclusiveTalentService(
            ExclusiveTalentApplicationService applicationService,
            ExclusiveTalentMapper exclusiveTalentMapper) {
        this.applicationService = applicationService;
        this.exclusiveTalentMapper = exclusiveTalentMapper;
    }

    @Deprecated
    public int evaluatePreviousMonthAndApplyCurrentMonth() {
        try {
            return applicationService.evaluatePreviousMonthAndApplyCurrentMonth();
        } catch (RuntimeException ex) {
            log.warn("evaluatePreviousMonthAndApplyCurrentMonth delegated call failed", ex);
            return 0;
        }
    }

    @Deprecated
    public int evaluateMonth(YearMonth statsMonth, YearMonth applyMonth) {
        try {
            return applicationService.evaluateMonth(statsMonth, applyMonth);
        } catch (RuntimeException ex) {
            log.warn("evaluateMonth delegated call failed statsMonth={} applyMonth={}", statsMonth, applyMonth, ex);
            return 0;
        }
    }

    @Deprecated
    public AttributionService.ExclusiveOwner findActiveOwnerByTalentUid(String talentUid) {
        return findActiveOwnerByTalentUidAt(talentUid, LocalDate.now());
    }

    /** 按订单业务日期读取有效独家达人，避免重算使用当前月份。 */
    public AttributionService.ExclusiveOwner findActiveOwnerByTalentUidAt(String talentUid, LocalDate businessDate) {
        if (talentUid == null || talentUid.isEmpty()) {
            return null;
        }
        String month = (businessDate == null ? YearMonth.now() : YearMonth.from(businessDate)).format(MONTH_FORMATTER);
        ExclusiveTalent match = exclusiveTalentMapper.selectOne(new LambdaQueryWrapper<ExclusiveTalent>()
                .eq(ExclusiveTalent::getTalentUid, talentUid)
                .eq(ExclusiveTalent::getEffectiveMonth, month)
                .eq(ExclusiveTalent::getStatus, 1)
                .eq(ExclusiveTalent::getDeleted, 0)
                .orderByDesc(ExclusiveTalent::getCreateTime)
                .last("limit 1"));
        if (match == null || match.getUserId() == null) {
            return null;
        }
        return new AttributionService.ExclusiveOwner(match.getUserId(), match.getDeptId());
    }
}
