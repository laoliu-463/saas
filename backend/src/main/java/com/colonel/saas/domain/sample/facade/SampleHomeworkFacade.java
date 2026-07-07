package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.entity.ColonelsettlementOrder;

/**
 * 寄样交作业写入口。
 *
 * <p>订单域只能提交订单事实给本 Facade，不得直接访问寄样 Mapper、
 * 状态日志服务或寄样生命周期状态机。</p>
 */
public interface SampleHomeworkFacade {

    /**
     * 根据已同步订单事实完成匹配的待交作业寄样单。
     *
     * @return 实际完成的寄样单数量
     */
    int completePendingHomeworkByOrder(ColonelsettlementOrder order);
}
