package com.colonel.saas.douyin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DoudianTokenGatewayTest {

    @Test
    void maskSecret_shouldNotRevealTokenPrefixOrSuffix() throws Exception {
        DoudianTokenGateway gateway = new DoudianTokenGateway(new DouyinConfig());
        Method maskSecret = DoudianTokenGateway.class.getDeclaredMethod("maskSecret", String.class);
        maskSecret.setAccessible(true);

        String masked = (String) maskSecret.invoke(gateway, "access-token-123456");

        assertThat(masked).isEqualTo("****");
        assertThat(masked).doesNotContain("acce", "3456");
    }
}
