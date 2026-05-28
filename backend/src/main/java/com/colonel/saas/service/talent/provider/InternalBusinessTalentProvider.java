package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 内部业务数据源 —— 达人补全 Provider（P0 占位实现）。
 *
 * <p>在"真实模式"（{@code talent.enrich.mode=real}，默认值）下由 Spring 自动装配；
 * 当模式切换为 {@code test} 时本 Bean 不会注册，由 {@link TestTalentProvider} 接管。</p>
 *
 * <ul>
 *   <li><b>职责</b>：预留从内部业务聚合视图（订单、业绩、寄样等）提取达人指标的扩展点</li>
 *   <li><b>优先级</b>：priority = 20，介于第三方 API (10) 与手动数据 (90) 之间</li>
 *   <li><b>数据源标识</b>：{@link TalentDataSource#INTERNAL_BUSINESS}</li>
 *   <li><b>架构角色</b>：策略模式中的一个具体策略，由 {@link com.colonel.saas.service.talent.TalentEnrichOrchestrator} 统一调度</li>
 *   <li><b>业务域</b>：达人域 → 数据补全子领域</li>
 *   <li><b>访问控制</b>：仅内部服务调用，不对外暴露</li>
 * </ul>
 *
 * @see com.colonel.saas.service.talent.TalentDataProvider  策略接口
 * @see com.colonel.saas.service.talent.TalentEnrichOrchestrator  编排器
 * @see TalentDataSource#INTERNAL_BUSINESS  数据源枚举值
 */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "real", matchIfMissing = true)
public class InternalBusinessTalentProvider implements TalentDataProvider {

    /**
     * 返回本 Provider 对应的数据源标识。
     *
     * @return {@link TalentDataSource#INTERNAL_BUSINESS}，用于字段来源追踪
     */
    @Override
    public TalentDataSource source() {
        return TalentDataSource.INTERNAL_BUSINESS;
    }

    /**
     * 返回本 Provider 的调度优先级。
     *
     * <p>值越小越优先。本 Provider 优先级为 20，
     * 低于第三方 API (10)，高于手动数据 (90)。</p>
     *
     * @return 优先级数值 20
     */
    @Override
    public int priority() {
        return 20;
    }

    /**
     * 判断当前上下文是否满足本 Provider 的执行条件。
     *
     * @param context 达人补全上下文，包含待补全的 {@code Talent} 实体
     * @return {@code true} 当上下文和达人实体均非空时
     */
    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    /**
     * 执行达人数据补全。
     *
     * <p>P0 阶段占位实现 —— 暂不进行任何数据拉取，直接返回空结果。
     * 后续迭代中可在此方法中聚合内部业务数据（如历史订单 GMV、合作次数、寄样完成率等）。</p>
     *
     * @param context 达人补全上下文
     * @return 始终返回 {@link TalentEnrichResult#empty}，附带 "无映射" 提示信息
     */
    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        // P0 占位：后续可在此处接入内部业务聚合字段
        return TalentEnrichResult.empty(source(), "internal business provider has no mapping yet");
    }
}
