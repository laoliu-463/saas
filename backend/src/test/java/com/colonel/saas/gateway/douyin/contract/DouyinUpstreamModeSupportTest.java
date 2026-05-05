package com.colonel.saas.gateway.douyin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DouyinUpstreamModeSupportTest {

    @Test
    @DisplayName("null 输入应默认为 live 模式")
    void nullInput_defaultsToLive() {
        var support = new DouyinUpstreamModeSupport(null);
        assertThat(support.isLive()).isTrue();
        assertThat(support.isContract()).isFalse();
        assertThat(support.value()).isEqualTo("live");
    }

    @Test
    @DisplayName("空字符串应默认为 live 模式")
    void emptyString_defaultsToLive() {
        var support = new DouyinUpstreamModeSupport("");
        assertThat(support.isLive()).isTrue();
        assertThat(support.value()).isEqualTo("live");
    }

    @Test
    @DisplayName("纯空格应默认为 live 模式")
    void whitespaceOnly_defaultsToLive() {
        var support = new DouyinUpstreamModeSupport("   ");
        assertThat(support.isLive()).isTrue();
        assertThat(support.value()).isEqualTo("live");
    }

    @Test
    @DisplayName("live 字符串正确识别")
    void live_lowercase() {
        var support = new DouyinUpstreamModeSupport("live");
        assertThat(support.isLive()).isTrue();
        assertThat(support.isContract()).isFalse();
    }

    @Test
    @DisplayName("LIVE 大写应归一化为 live")
    void live_uppercase() {
        var support = new DouyinUpstreamModeSupport("LIVE");
        assertThat(support.isLive()).isTrue();
        assertThat(support.value()).isEqualTo("live");
    }

    @Test
    @DisplayName("contract 字符串正确识别")
    void contract_lowercase() {
        var support = new DouyinUpstreamModeSupport("contract");
        assertThat(support.isContract()).isTrue();
        assertThat(support.isLive()).isFalse();
        assertThat(support.value()).isEqualTo("contract");
    }

    @Test
    @DisplayName("CONTRACT 大写应归一化为 contract")
    void contract_uppercase() {
        var support = new DouyinUpstreamModeSupport("CONTRACT");
        assertThat(support.isContract()).isTrue();
        assertThat(support.value()).isEqualTo("contract");
    }

    @Test
    @DisplayName("Contract 混合大小写应归一化为 contract")
    void contract_mixedCase() {
        var support = new DouyinUpstreamModeSupport("Contract");
        assertThat(support.isContract()).isTrue();
    }

    @Test
    @DisplayName("contract 带前后空格应 trim 后识别")
    void contract_withWhitespace() {
        var support = new DouyinUpstreamModeSupport("  Contract  ");
        assertThat(support.isContract()).isTrue();
        assertThat(support.value()).isEqualTo("contract");
    }

    @Test
    @DisplayName("未知字符串应默认为 live")
    void unknownString_defaultsToLive() {
        var support = new DouyinUpstreamModeSupport("random");
        assertThat(support.isLive()).isTrue();
        assertThat(support.isContract()).isFalse();
    }

    @Test
    @DisplayName("类似 contract 的拼写应默认为 live")
    void almostContract_defaultsToLive() {
        var support = new DouyinUpstreamModeSupport("contracts");
        assertThat(support.isLive()).isTrue();
    }
}
