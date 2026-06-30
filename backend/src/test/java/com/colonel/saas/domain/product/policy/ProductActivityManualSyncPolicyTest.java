package com.colonel.saas.domain.product.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductActivityManualSyncPolicyTest {

    @Test
    void messageFor_shouldPreserveLegacyManualSyncMessages() {
        assertThat(ProductActivityManualSyncPolicy.messageFor(ProductActivityManualSyncPolicy.STATUS_ACCEPTED))
                .isEqualTo("商品同步已转入后台执行");
        assertThat(ProductActivityManualSyncPolicy.messageFor(ProductActivityManualSyncPolicy.STATUS_RUNNING))
                .isEqualTo("商品同步已在后台执行，请稍后刷新列表");
        assertThat(ProductActivityManualSyncPolicy.messageFor(ProductActivityManualSyncPolicy.STATUS_INVALID))
                .isEqualTo("商品同步已转入后台执行");
    }
}
