package com.colonel.saas.domain.sample.api;

/**
 * 寄样域应用层端口 — 供其他域（如商品域）发起寄样申请。
 * <p>
 * 商品域快速寄样入口通过此端口委托寄样域完成实际的寄样创建，
 * 商品域不直接写寄样表、不处理寄样状态机。
 * </p>
 *
 * @see ApplySampleFromProductCommand
 * @see ApplySampleFromProductResult
 */
public interface SampleApplicationPort {

    /**
     * 从商品域快速寄样入口批量发起寄样申请。
     * <p>
     * 对命令中的每个达人独立处理，单个达人失败不影响其他达人。
     * 返回汇总结果（含每个达人的明细）。
     * </p>
     *
     * @param command 商品域发起的寄样命令
     * @return 批量申请结果汇总
     */
    ApplySampleFromProductResult applyFromProduct(ApplySampleFromProductCommand command);
}
