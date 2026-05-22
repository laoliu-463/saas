package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentInputType;
import com.colonel.saas.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentInputParserTest {

    @Test
    void parseShouldRejectBlankInput() {
        assertThatThrownBy(() -> TalentInputParser.parse(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void parseShouldExtractSecUidFromProfileUrl() {
        TalentInputParseResult result = TalentInputParser.parse(" https://www.douyin.com/user/MS4wLjABAAA?sec_uid=SEC_123&x=1 ");

        assertThat(result.getInputType()).isEqualTo(TalentInputType.SEC_UID);
        assertThat(result.getSecUid()).isEqualTo("SEC_123");
        assertThat(result.getDouyinUid()).isEqualTo("SEC_123");
        assertThat(result.getProfileUrl()).startsWith("https://www.douyin.com/user/");
    }

    @Test
    void parseShouldHandleShortUrlUidDouyinNoAndUnknownText() {
        assertThat(TalentInputParser.parse("v.douyin.com/abc123").getInputType()).isEqualTo(TalentInputType.PROFILE_URL);

        TalentInputParseResult uid = TalentInputParser.parse("1234567890");
        assertThat(uid.getInputType()).isEqualTo(TalentInputType.UID);
        assertThat(uid.getUid()).isEqualTo("1234567890");

        TalentInputParseResult douyinNo = TalentInputParser.parse("creator.no-1");
        assertThat(douyinNo.getInputType()).isEqualTo(TalentInputType.DOUYIN_NO);
        assertThat(douyinNo.getDouyinNo()).isEqualTo("creator.no-1");

        TalentInputParseResult unknown = TalentInputParser.parse("含中文的输入");
        assertThat(unknown.getInputType()).isEqualTo(TalentInputType.UNKNOWN);
        assertThat(unknown.getDouyinUid()).isEqualTo("含中文的输入");
    }

    @Test
    void parseResultShouldSupportBuilderAndSetters() {
        TalentInputParseResult result = TalentInputParseResult.builder()
                .inputType(TalentInputType.DOUYIN_NO)
                .rawInput("raw")
                .douyinNo("no")
                .uid("uid")
                .secUid("sec")
                .profileUrl("https://profile")
                .douyinUid("douyin")
                .build();
        result.setDouyinUid("updated");

        assertThat(result.getRawInput()).isEqualTo("raw");
        assertThat(result.getDouyinNo()).isEqualTo("no");
        assertThat(result.getUid()).isEqualTo("uid");
        assertThat(result.getSecUid()).isEqualTo("sec");
        assertThat(result.getProfileUrl()).isEqualTo("https://profile");
        assertThat(result.getDouyinUid()).isEqualTo("updated");
        assertThat(result.toString()).contains("updated");
    }
}
