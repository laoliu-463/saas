package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.entity.CommissionRule;
import com.colonel.saas.mapper.CommissionRuleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionRuleServiceTest {

    @Mock
    private CommissionRuleMapper commissionRuleMapper;

    private CommissionRuleService service;

    @BeforeEach
    void setUp() {
        if (TableInfoHelper.getTableInfo(CommissionRule.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, CommissionRule.class);
        }
        service = new CommissionRuleService(commissionRuleMapper);
    }

    @Test
    void resolveRatio_shouldPreferProductRuleOverActivityRule() {
        CommissionRule productRule = activeRule(
                CommissionRuleService.DIMENSION_PRODUCT,
                "P-1",
                CommissionRuleService.TYPE_RECRUITER,
                "0.25");
        when(commissionRuleMapper.selectOne(any())).thenReturn(productRule);

        BigDecimal ratio = service.resolveRatio(
                CommissionRuleService.TYPE_RECRUITER,
                new CommissionRuleService.CommissionResolutionContext("A-1", "P-1", UUID.randomUUID()),
                LocalDateTime.now());

        assertThat(ratio).isEqualByComparingTo("0.25");
    }

    @Test
    void resolveRatio_shouldFallbackFromProductToActivityAndStopAtFirstMatch() {
        CommissionRule activityRule = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        when(commissionRuleMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(activityRule);

        BigDecimal ratio = service.resolveRatio(
                CommissionRuleService.TYPE_CHANNEL,
                new CommissionRuleService.CommissionResolutionContext("A-1", "P-1", UUID.randomUUID()),
                LocalDateTime.now());

        assertThat(ratio).isEqualByComparingTo("0.18");
        verify(commissionRuleMapper, times(2)).selectOne(any());
    }

    @Test
    void resolveRule_shouldExposeMatchedRuleVersionEvidence() {
        UUID ruleId = UUID.randomUUID();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 8, 10, 30);
        CommissionRule productRule = activeRule(
                CommissionRuleService.DIMENSION_PRODUCT,
                "P-1",
                CommissionRuleService.TYPE_RECRUITER,
                "0.25");
        productRule.setId(ruleId);
        productRule.setVersion(7);
        productRule.setUpdateTime(updatedAt);
        when(commissionRuleMapper.selectOne(any())).thenReturn(productRule);

        CommissionRuleService.CommissionRuleResolution resolution = service.resolveRule(
                CommissionRuleService.TYPE_RECRUITER,
                new CommissionRuleService.CommissionResolutionContext("A-1", "P-1", UUID.randomUUID()),
                LocalDateTime.now());

        assertThat(resolution.ratio()).isEqualByComparingTo("0.25");
        assertThat(resolution.ruleId()).isEqualTo(ruleId);
        assertThat(resolution.ruleVersion()).isEqualTo(7);
        assertThat(resolution.ruleUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void create_shouldPersistValidatedRule() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(CommissionRuleService.DIMENSION_ACTIVITY);
        rule.setDimensionId("3559407");
        rule.setCommissionType(CommissionRuleService.TYPE_CHANNEL);
        rule.setRatio(new BigDecimal("0.18"));
        rule.setVersion(99);

        when(commissionRuleMapper.insert(any(CommissionRule.class))).thenReturn(1);

        CommissionRule created = service.create(rule);

        assertThat(created.getId()).isNotNull();
        ArgumentCaptor<CommissionRule> captor = ArgumentCaptor.forClass(CommissionRule.class);
        verify(commissionRuleMapper).insert(captor.capture());
        assertThat(captor.getValue().getDimensionType()).isEqualTo(CommissionRuleService.DIMENSION_ACTIVITY);
        assertThat(captor.getValue().getCommissionType()).isEqualTo(CommissionRuleService.TYPE_CHANNEL);
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void create_shouldNormalizeGlobalRuleWithoutDimensionId() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(" GLOBAL ");
        rule.setDimensionId("SHOULD_DROP");
        rule.setCommissionType(" RECRUITER ");
        rule.setRatio(new BigDecimal("0.16"));

        when(commissionRuleMapper.insert(any(CommissionRule.class))).thenReturn(1);

        CommissionRule created = service.create(rule);

        assertThat(created.getDimensionType()).isEqualTo(CommissionRuleService.DIMENSION_GLOBAL);
        assertThat(created.getDimensionId()).isNull();
        assertThat(created.getCommissionType()).isEqualTo(CommissionRuleService.TYPE_RECRUITER);
    }

    @Test
    void create_shouldRejectRuleWhenRatioOutOfRange() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(CommissionRuleService.DIMENSION_ACTIVITY);
        rule.setDimensionId("A-1");
        rule.setCommissionType(CommissionRuleService.TYPE_CHANNEL);
        rule.setRatio(new BigDecimal("1.5"));

        assertThatThrownBy(() -> service.create(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0~1");
    }

    @Test
    void create_shouldRejectRuleWhenEffectiveEndIsBeforeStart() {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(CommissionRuleService.DIMENSION_ACTIVITY);
        rule.setDimensionId("A-1");
        rule.setCommissionType(CommissionRuleService.TYPE_CHANNEL);
        rule.setRatio(new BigDecimal("0.15"));
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        rule.setEffectiveStart(start);
        rule.setEffectiveEnd(start.minusDays(1));

        assertThatThrownBy(() -> service.create(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生效结束时间不能早于开始时间");
    }

    @Test
    void findPage_shouldOnlyApplySoftDeleteAndOrderByWhenNoFiltersProvided() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        IPage<CommissionRule> result = service.findPage(null, null, null, null, null, 1, 20);

        assertThat(result).isNotNull();
        // 至少有一次 selectPage 被调用；具体 SQL 在覆盖性用例中验证
        verify(commissionRuleMapper, times(1))
                .selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void findPage_shouldCombineDimensionTypeAndCommissionType() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        IPage<CommissionRule> result = service.findPage("activity", "channel", null, null, null, 1, 20);

        assertThat(result).isNotNull();
        ArgumentCaptor<Page<CommissionRule>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<Wrapper<CommissionRule>> wrapperCaptor = captorWrapper();
        verify(commissionRuleMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        Page<CommissionRule> page = pageCaptor.getValue();
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(20L);
        // 通过自定义 toString 检查关键 SQL 片段（MyBatis-Plus wrapper 不暴露谓词列表）
        Wrapper<CommissionRule> wrapper = wrapperCaptor.getValue();
        String sql = wrapperSql(wrapper);
        assertThat(sql).contains("dimension_type =");
        assertThat(sql).contains("commission_type =");
        assertThat(sql).contains("deleted =");
        assertThat(wrapperParams(wrapper).values()).contains(0, "activity", "channel");
    }

    @Test
    void findPage_shouldApplyStatusFilterWhenProvided() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        service.findPage(null, null, 1, null, null, 1, 20);

        Wrapper<CommissionRule> wrapper = captorWrapper().getValue();
        String sql = wrapperSql(wrapper);
        assertThat(sql).contains("status =");
        assertThat(wrapperParams(wrapper).values()).contains(1);
    }

    @Test
    void findPage_shouldIgnoreInvalidStatusValuesInsteadOfThrowing() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        service.findPage(null, null, 99, null, null, 1, 20);

        String sql = wrapperSql(captorWrapper().getValue());
        assertThat(sql).doesNotContain("status =");
    }

    @Test
    void findPage_shouldOverlapQueryRangeWithRuleEffectiveWindow() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        LocalDateTime queryStart = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime queryEnd = LocalDateTime.of(2026, 6, 30, 23, 59, 59);

        service.findPage(null, null, null, queryStart, queryEnd, 1, 20);

        Wrapper<CommissionRule> wrapper = captorWrapper().getValue();
        String sql = wrapperSql(wrapper);
        // 区间重叠：rule.end IS NULL OR rule.end >= query.start
        //          AND rule.start IS NULL OR rule.start <= query.end
        assertThat(sql).contains("effective_end >=");
        assertThat(sql).contains("effective_start <=");
        assertThat(wrapperParams(wrapper).values()).contains(queryStart, queryEnd);
    }

    @Test
    void findPage_shouldOnlyApplyLowerBoundWhenOnlyStartProvided() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        service.findPage(null, null, null, LocalDateTime.of(2026, 6, 1, 0, 0), null, 1, 20);

        Wrapper<CommissionRule> wrapper = captorWrapper().getValue();
        String sql = wrapperSql(wrapper);
        assertThat(sql).contains("effective_end >=");
        assertThat(sql).doesNotContain("effective_start <=");
        assertThat(wrapperParams(wrapper).values()).contains(LocalDateTime.of(2026, 6, 1, 0, 0));
    }

    @Test
    void findPage_shouldOnlyApplyUpperBoundWhenOnlyEndProvided() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        service.findPage(null, null, null, null, LocalDateTime.of(2026, 6, 30, 0, 0), 1, 20);

        Wrapper<CommissionRule> wrapper = captorWrapper().getValue();
        String sql = wrapperSql(wrapper);
        assertThat(sql).doesNotContain("effective_end >=");
        assertThat(sql).contains("effective_start <=");
        assertThat(wrapperParams(wrapper).values()).contains(LocalDateTime.of(2026, 6, 30, 0, 0));
    }

    @Test
    void findPage_shouldRejectInvertedQueryRange() {
        assertThatThrownBy(() -> service.findPage(
                null, null, null,
                LocalDateTime.of(2026, 6, 30, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0),
                1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("查询生效区间");
    }

    @Test
    void findPage_shouldCombineAllFiltersWithAndSemantics() {
        when(commissionRuleMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>());

        service.findPage(
                "activity",
                "channel",
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 0, 0),
                1, 20);

        Wrapper<CommissionRule> wrapper = captorWrapper().getValue();
        String sql = wrapperSql(wrapper);
        assertThat(sql).contains("dimension_type =");
        assertThat(sql).contains("commission_type =");
        assertThat(sql).contains("status =");
        assertThat(sql).contains("effective_end >=");
        assertThat(sql).contains("effective_start <=");
        assertThat(wrapperParams(wrapper).values()).contains(
                "activity",
                "channel",
                1,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 30, 0, 0));
    }

    @Test
    void delete_shouldSoftDeleteExistingRule() {
        CommissionRule existing = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        existing.setId(UUID.randomUUID());
        when(commissionRuleMapper.selectById(existing.getId())).thenReturn(existing);
        when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(1);

        service.delete(existing.getId());

        ArgumentCaptor<CommissionRule> captor = ArgumentCaptor.forClass(CommissionRule.class);
        verify(commissionRuleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
    }

    @Test
    void update_shouldKeepLoadedVersionForOptimisticLockEvidence() {
        UUID id = UUID.randomUUID();
        CommissionRule existing = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        existing.setId(id);
        existing.setVersion(5);
        CommissionRule request = activeRule(
                CommissionRuleService.DIMENSION_PRODUCT,
                "P-1",
                CommissionRuleService.TYPE_RECRUITER,
                "0.25");
        request.setVersion(99);
        when(commissionRuleMapper.selectById(id)).thenReturn(existing);
        when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(1);

        service.update(id, request);

        ArgumentCaptor<CommissionRule> captor = ArgumentCaptor.forClass(CommissionRule.class);
        verify(commissionRuleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getVersion()).isEqualTo(5);
        assertThat(captor.getValue().getDimensionType()).isEqualTo(CommissionRuleService.DIMENSION_PRODUCT);
    }

    @Test
    void update_shouldThrowConflictWhenOptimisticLockUpdatesNoRows() {
        UUID id = UUID.randomUUID();
        CommissionRule existing = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        existing.setId(id);
        existing.setVersion(5);
        CommissionRule request = activeRule(
                CommissionRuleService.DIMENSION_PRODUCT,
                "P-1",
                CommissionRuleService.TYPE_RECRUITER,
                "0.25");
        when(commissionRuleMapper.selectById(id)).thenReturn(existing);
        when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(0);

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提成规则已被他人修改")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.CONFLICT.getCode()));
    }

    @Test
    void delete_shouldThrowConflictWhenOptimisticLockUpdatesNoRows() {
        UUID id = UUID.randomUUID();
        CommissionRule existing = activeRule(
                CommissionRuleService.DIMENSION_ACTIVITY,
                "A-1",
                CommissionRuleService.TYPE_CHANNEL,
                "0.18");
        existing.setId(id);
        existing.setVersion(5);
        when(commissionRuleMapper.selectById(id)).thenReturn(existing);
        when(commissionRuleMapper.updateById(any(CommissionRule.class))).thenReturn(0);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提成规则已被他人修改")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ResultCode.CONFLICT.getCode()));
    }

    @Test
    void delete_shouldThrowNotFoundWhenRuleMissing() {
        UUID id = UUID.randomUUID();
        when(commissionRuleMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提成规则不存在");
    }

    private CommissionRule activeRule(String dimensionType, String dimensionId, String commissionType, String ratio) {
        CommissionRule rule = new CommissionRule();
        rule.setDimensionType(dimensionType);
        rule.setDimensionId(dimensionId);
        rule.setCommissionType(commissionType);
        rule.setRatio(new BigDecimal(ratio));
        rule.setStatus(1);
        rule.setDeleted(0);
        return rule;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Wrapper<CommissionRule>> captorWrapper() {
        ArgumentCaptor<Wrapper> raw = ArgumentCaptor.forClass(Wrapper.class);
        verify(commissionRuleMapper).selectPage(any(Page.class), raw.capture());
        return (ArgumentCaptor) raw;
    }

    /**
     * 序列化 MyBatis-Plus Wrapper 内部 SQL 片段。Wrapper 未暴露 predicate 列表，
     * 这里通过 {@code AbstractWrapper#getSqlSegment()} 反射拿到当前 SQL。
     */
    private String wrapperSql(Wrapper<CommissionRule> wrapper) {
        try {
            java.lang.reflect.Method m = wrapper.getClass().getMethod("getSqlSegment");
            Object result = m.invoke(wrapper);
            return result == null ? "" : result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取 wrapper SQL 片段: " + wrapper, ex);
        }
    }

    private Map<String, Object> wrapperParams(Wrapper<CommissionRule> wrapper) {
        if (wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper) {
            return abstractWrapper.getParamNameValuePairs();
        }
        throw new IllegalStateException("无法读取 wrapper 参数: " + wrapper);
    }
}
