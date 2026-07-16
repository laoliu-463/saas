package com.colonel.saas.vo.sample;

/**
 * 合作台复制文本的共享返回结构。具体复制业务由对应应用服务实现。
 */
public record SampleCopyTextVO(
        String text,
        boolean promotionLinkGenerated,
        String promotionLink,
        String fallbackReason) {

    /** 保留 Task 3 期间的一字段构造方式。 */
    public SampleCopyTextVO(String text) {
        this(text, false, null, null);
    }
}
