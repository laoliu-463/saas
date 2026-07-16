package com.colonel.saas.dto.talent;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.vo.TalentVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TalentDtoTest {

    @Test
    void createRequest_shouldRequireAtLeastOneIdentity() {
        TalentCreateRequest request = new TalentCreateRequest();
        assertThat(request.isIdentityPresent()).isFalse();

        request.setProfileUrl(" https://www.douyin.com/user/abc ");

        assertThat(request.isIdentityPresent()).isTrue();
    }

    @Test
    void createRequest_shouldTrimTextFieldsWhenConvertingToTalent() {
        TalentCreateRequest request = new TalentCreateRequest();
        request.setDouyinUid(" uid-1 ");
        request.setDouyinNo(" douyin-no ");
        request.setUid(" internal-uid ");
        request.setSecUid(" sec-1 ");
        request.setProfileUrl(" https://profile ");
        request.setNickname(" 达人 ");
        request.setFansCount(123L);
        request.setLevel(" L3 ");
        request.setTalentLevel(" LV2 ");
        request.setSales30d(45678L);
        request.setAvatarUrl(" https://avatar ");
        request.setCategories(" 美妆 ");
        request.setContactPhone(" 13800000000 ");
        request.setIntro(" 简介 ");

        Talent talent = request.toTalent();

        assertThat(talent.getDouyinUid()).isEqualTo("uid-1");
        assertThat(talent.getDouyinNo()).isEqualTo("douyin-no");
        assertThat(talent.getUid()).isEqualTo("internal-uid");
        assertThat(talent.getSecUid()).isEqualTo("sec-1");
        assertThat(talent.getProfileUrl()).isEqualTo("https://profile");
        assertThat(talent.getNickname()).isEqualTo("达人");
        assertThat(talent.getFans()).isEqualTo(123L);
        assertThat(talent.getLevel()).isEqualTo("L3");
        assertThat(talent.getTalentLevel()).isEqualTo("LV2");
        assertThat(talent.getSales30d()).isEqualTo(45678L);
        assertThat(talent.getAvatarUrl()).isEqualTo("https://avatar");
        assertThat(talent.getCategories()).isEqualTo("美妆");
        assertThat(talent.getContactPhone()).isEqualTo("13800000000");
        assertThat(talent.getIntro()).isEqualTo("简介");
    }

    @Test
    void createRequest_shouldMapManualPrefillFieldsWhenConvertingToTalent() {
        TalentCreateRequest request = new TalentCreateRequest();
        request.setDouyinNo(" douyin-no ");
        request.setDouyinAccount(" account-no ");
        request.setDataSource(" manual ");
        request.setSyncStatus(" success ");
        request.setUnsupportedFields(List.of("talentLevel", "sales30d"));

        Talent talent = request.toTalent();

        assertThat(talent.getDouyinAccount()).isEqualTo("account-no");
        assertThat(talent.getDataSource()).isEqualTo("manual");
        assertThat(talent.getSyncStatus()).isEqualTo("success");
        assertThat(talent.getUnsupportedFields()).containsExactly("talentLevel", "sales30d");
    }

    @Test
    void createRequest_shouldConvertBlankTextFieldsToNull() {
        TalentCreateRequest request = new TalentCreateRequest();
        request.setDouyinUid(" ");
        request.setDouyinNo("");
        request.setUid(null);
        request.setSecUid(" ");
        request.setProfileUrl(" ");
        request.setNickname(" ");
        request.setLevel(" ");
        request.setAvatarUrl(" ");
        request.setCategories(" ");
        request.setContactPhone(" ");
        request.setIntro(" ");

        Talent talent = request.toTalent();

        assertThat(talent.getDouyinUid()).isNull();
        assertThat(talent.getDouyinNo()).isNull();
        assertThat(talent.getUid()).isNull();
        assertThat(talent.getSecUid()).isNull();
        assertThat(talent.getProfileUrl()).isNull();
        assertThat(talent.getNickname()).isNull();
        assertThat(talent.getLevel()).isNull();
        assertThat(talent.getAvatarUrl()).isNull();
        assertThat(talent.getCategories()).isNull();
        assertThat(talent.getContactPhone()).isNull();
        assertThat(talent.getIntro()).isNull();
    }

    @Test
    void updateRequest_shouldBuildBasicAndManualFillTalent() {
        TalentUpdateRequest request = new TalentUpdateRequest();
        request.setNickname(" 更新达人 ");
        request.setFansCount(456L);
        request.setLevel(" L4 ");
        request.setStatus(1);
        request.setAvatarUrl(" https://avatar2 ");
        request.setLikesCount(10L);
        request.setFollowingCount(20L);
        request.setWorksCount(30L);
        request.setIpLocation(" 上海 ");
        request.setTalentLevel(" LV2 ");
        request.setSales30d(12345L);
        request.setContactPhone(" 13900000000 ");
        request.setIntro(" 更新简介 ");

        Talent updateTalent = request.toUpdateTalent();
        Talent manualFillTalent = request.toManualFillTalent();

        assertThat(updateTalent.getNickname()).isEqualTo("更新达人");
        assertThat(updateTalent.getFans()).isEqualTo(456L);
        assertThat(updateTalent.getLevel()).isEqualTo("L4");
        assertThat(updateTalent.getStatus()).isEqualTo(1);
        assertThat(updateTalent.getContactPhone()).isEqualTo("13900000000");
        assertThat(updateTalent.getIntro()).isEqualTo("更新简介");
        assertThat(manualFillTalent.getAvatarUrl()).isEqualTo("https://avatar2");
        assertThat(manualFillTalent.getLikesCount()).isEqualTo(10L);
        assertThat(manualFillTalent.getFollowingCount()).isEqualTo(20L);
        assertThat(manualFillTalent.getWorksCount()).isEqualTo(30L);
        assertThat(manualFillTalent.getIpLocation()).isEqualTo("上海");
        assertThat(manualFillTalent.getTalentLevel()).isEqualTo("LV2");
        assertThat(manualFillTalent.getSales30d()).isEqualTo(12345L);
    }

    @Test
    void pageQueryAndProfilePayload_shouldExposeAssignedValues() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        TalentPageQuery query = new TalentPageQuery();
        query.setPage(2);
        query.setSize(50);
        query.setKeyword("达人");
        query.setRegion("华东");
        query.setPoolStatus("CLAIMED");
        query.setOwnerKeyword("小王");
        query.setMinFans(1000L);
        query.setMaxFans(100000L);
        query.setView("pool");
        query.setCategory("美妆");
        query.setClaimStatus("ACTIVE");
        query.setLiveSalesBand("10w+");
        query.setLiveViewBand("1w+");
        query.setLiveGpmBand("high");
        query.setVideoSalesBand("5w+");
        query.setVideoPlayBand("10w+");
        query.setVideoGpmBand("mid");
        query.setLevel("L5");
        query.setGender("female");
        query.setContactStatus("HAS_CONTACT");
        query.setUserId(userId);
        query.setDeptId(deptId);
        query.setDataScope(DataScope.ALL);

        TalentProfilePayload payload = TalentProfilePayload.builder()
                .douyinAccount("dy-1")
                .talentUid("uid-1")
                .secUid("sec-1")
                .nickname("达人")
                .avatarUrl("https://avatar")
                .fansCount(1L)
                .likeCount(2L)
                .followingCount(3L)
                .worksCount(4L)
                .ipLocation("上海")
                .talentLevel("L5")
                .sales30d(5L)
                .build();

        assertThat(query.getPage()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(50);
        assertThat(query.getKeyword()).isEqualTo("达人");
        assertThat(query.getRegion()).isEqualTo("华东");
        assertThat(query.getPoolStatus()).isEqualTo("CLAIMED");
        assertThat(query.getOwnerKeyword()).isEqualTo("小王");
        assertThat(query.getMinFans()).isEqualTo(1000L);
        assertThat(query.getMaxFans()).isEqualTo(100000L);
        assertThat(query.getView()).isEqualTo("pool");
        assertThat(query.getCategory()).isEqualTo("美妆");
        assertThat(query.getClaimStatus()).isEqualTo("ACTIVE");
        assertThat(query.getLiveSalesBand()).isEqualTo("10w+");
        assertThat(query.getLiveViewBand()).isEqualTo("1w+");
        assertThat(query.getLiveGpmBand()).isEqualTo("high");
        assertThat(query.getVideoSalesBand()).isEqualTo("5w+");
        assertThat(query.getVideoPlayBand()).isEqualTo("10w+");
        assertThat(query.getVideoGpmBand()).isEqualTo("mid");
        assertThat(query.getLevel()).isEqualTo("L5");
        assertThat(query.getGender()).isEqualTo("female");
        assertThat(query.getContactStatus()).isEqualTo("HAS_CONTACT");
        assertThat(query.getUserId()).isEqualTo(userId);
        assertThat(query.getDeptId()).isEqualTo(deptId);
        assertThat(query.getDataScope()).isEqualTo(DataScope.ALL);
        assertThat(payload.getDouyinAccount()).isEqualTo("dy-1");
        assertThat(payload.getTalentUid()).isEqualTo("uid-1");
        assertThat(payload.getSecUid()).isEqualTo("sec-1");
        assertThat(payload.getNickname()).isEqualTo("达人");
        assertThat(payload.getAvatarUrl()).isEqualTo("https://avatar");
        assertThat(payload.getFansCount()).isEqualTo(1L);
        assertThat(payload.getLikeCount()).isEqualTo(2L);
        assertThat(payload.getFollowingCount()).isEqualTo(3L);
        assertThat(payload.getWorksCount()).isEqualTo(4L);
        assertThat(payload.getIpLocation()).isEqualTo("上海");
        assertThat(payload.getTalentLevel()).isEqualTo("L5");
        assertThat(payload.getSales30d()).isEqualTo(5L);
    }

    @Test
    void talentVo_shouldExposeProfileMetrics() {
        Talent talent = new Talent();
        talent.setTalentLevel("LV2");
        talent.setSales30d(68000L);
        talent.setUnsupportedFields(java.util.List.of("talentLevel"));

        TalentVO vo = TalentVO.from(talent);

        assertThat(vo.getTalentLevel()).isEqualTo("LV2");
        assertThat(vo.getSales30d()).isEqualTo(68000L);
        assertThat(vo.getUnsupportedFields()).containsExactly("talentLevel");
    }
}
