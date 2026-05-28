package com.colonel.saas.constant;

/**
 * 寄样域领域事件类型常量类。
 * <p>
 * 定义寄样（样品管理）域中所有领域事件的类型标识。寄样流程涵盖从创建到完结的完整生命周期，
 * 通过事件驱动的方式与其他域（如订单域、业绩域）进行解耦通信。
 * </p>
 * <p>
 * 寄样生命周期事件流转：
 * <pre>
 * SAMPLE_CREATED → SAMPLE_APPROVED → SAMPLE_SHIPPED → SAMPLE_SIGNED → SAMPLE_COMPLETED
 *                                    ↘ SAMPLE_REJECTED
 *                                                       ↘ SAMPLE_CLOSED
 * </pre>
 * </p>
 * <p>
 * 核心交互：
 * <ul>
 *   <li>订单已同步事件触发后，寄样域判断"交作业"是否完成</li>
 *   <li>寄样完成事件可能触发业绩域的归属计算</li>
 * </ul>
 * </p>
 */
public final class SampleDomainEventTypes {

    /** 防止实例化 */
    private SampleDomainEventTypes() {
    }

    /** 寄样创建事件 — 新建寄样申请时触发 */
    public static final String SAMPLE_CREATED = "SampleCreated";

    /** 寄样审批通过事件 — 寄样申请被审核通过时触发 */
    public static final String SAMPLE_APPROVED = "SampleApproved";

    /** 寄样审批驳回事件 — 寄样申请被审核驳回时触发 */
    public static final String SAMPLE_REJECTED = "SampleRejected";

    /** 寄样发货事件 — 样品实际发出时触发 */
    public static final String SAMPLE_SHIPPED = "SampleShipped";

    /** 寄样签收事件 — 达人确认签收样品时触发 */
    public static final String SAMPLE_SIGNED = "SampleSigned";

    /** 寄样完成事件 — 寄样流程正常结束时触发，可能关联"交作业"判定 */
    public static final String SAMPLE_COMPLETED = "SampleCompleted";

    /** 寄样关闭事件 — 寄样流程异常终止或超时关闭时触发 */
    public static final String SAMPLE_CLOSED = "SampleClosed";
}
