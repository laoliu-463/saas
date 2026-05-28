package com.colonel.saas.mapper;

import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromotionLinkMapper 集成测试 - 使用 Testcontainers 真实 PostgreSQL
 */
@DockerAvailable
class PromotionLinkMapperTest extends BaseIntegrationTest {

    @Autowired
    private PromotionLinkMapper promotionLinkMapper;

    @Nested
    @DisplayName("BaseMapper operations")
    class BaseMapperOperationsTest {

        @Test
        void insertAndSelectById() {
            PromotionLink link = createLink("PRODUCT001", "ACT001", "TALENT001");
            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());

            assertThat(found).isNotNull();
            assertThat(found.getProductId()).isEqualTo("PRODUCT001");
            assertThat(found.getActivityId()).isEqualTo("ACT001");
            assertThat(found.getTalentId()).isEqualTo("TALENT001");
        }

        @Test
        void updateById() {
            PromotionLink link = createLink("PRODUCT001", "ACT001", "TALENT001");
            promotionLinkMapper.insert(link);

            link.setPromotionUrl("https://promotion.example.com/new");
            link.setShortUrl("https://short.url/abc");
            promotionLinkMapper.updateById(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());

            assertThat(found.getPromotionUrl()).isEqualTo("https://promotion.example.com/new");
            assertThat(found.getShortUrl()).isEqualTo("https://short.url/abc");
        }

        @Test
        void deleteById() {
            PromotionLink link = createLink("PRODUCT001", "ACT001", "TALENT001");
            promotionLinkMapper.insert(link);

            int rows = promotionLinkMapper.deleteById(link.getId());

            assertThat(rows).isEqualTo(1);
        }

        @Test
        void selectBatchIds() {
            PromotionLink l1 = createLink("P1", "A1", "T1");
            PromotionLink l2 = createLink("P2", "A2", "T2");
            promotionLinkMapper.insert(l1);
            promotionLinkMapper.insert(l2);

            List<PromotionLink> found = promotionLinkMapper.selectBatchIds(List.of(l1.getId(), l2.getId()));

            assertThat(found).hasSize(2);
        }
    }

    @Nested
    @DisplayName("字段映射测试")
    class FieldMappingTest {

        @Test
        void shouldMapAllFields() {
            PromotionLink link = new PromotionLink();
            link.setId(UUID.randomUUID());
            link.setProductId("PRODUCT001");
            link.setActivityId("ACT001");
            link.setTalentId("TALENT001");
            link.setTalentName("达人名称");
            link.setChannelUserId(UUID.randomUUID());
            link.setChannelUserName("渠道用户");
            link.setOriginalProductUrl("https://original.url");
            link.setPromotionUrl("https://promotion.url");
            link.setShortUrl("https://short.url");
            link.setDoukouling("6.59复制整段话，打开Douyin即可查看~\n👉👉👉 https://...");
            link.setPickSource("channel_abc123");
            link.setPickExtra("channel_abc123");
            link.setLinkStatus("ACTIVE");
            link.setExpireTime(LocalDateTime.now().plusDays(30));
            link.setRawResponse(Map.of("success", true));
            link.setOperatorId(UUID.randomUUID());
            link.setOperatorName("操作员");
            link.setCreatedAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());
            link.setDeleted(0);

            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());

            assertThat(found.getProductId()).isEqualTo("PRODUCT001");
            assertThat(found.getActivityId()).isEqualTo("ACT001");
            assertThat(found.getTalentId()).isEqualTo("TALENT001");
            assertThat(found.getTalentName()).isEqualTo("达人名称");
            assertThat(found.getChannelUserName()).isEqualTo("渠道用户");
            assertThat(found.getOriginalProductUrl()).isEqualTo("https://original.url");
            assertThat(found.getPromotionUrl()).isEqualTo("https://promotion.url");
            assertThat(found.getShortUrl()).isEqualTo("https://short.url");
            assertThat(found.getDoukouling()).isNotEmpty();
            assertThat(found.getPickSource()).isEqualTo("channel_abc123");
            assertThat(found.getPickExtra()).isEqualTo("channel_abc123");
            assertThat(found.getLinkStatus()).isEqualTo("ACTIVE");
            assertThat(found.getRawResponse()).containsEntry("success", true);
            assertThat(found.getOperatorName()).isEqualTo("操作员");
            assertThat(found.getDeleted()).isEqualTo(0);
        }

        @Test
        void shouldHandleNullOptionalFields() {
            PromotionLink link = createLink("PRODUCT001", "ACT001", "TALENT001");
            // 不设置可选字段

            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());

            assertThat(found).isNotNull();
            assertThat(found.getTalentName()).isNull();
            assertThat(found.getShortUrl()).isNull();
            assertThat(found.getDoukouling()).isNull();
            assertThat(found.getExpireTime()).isNull();
            assertThat(found.getRawResponse()).isNull();
        }
    }

    @Nested
    @DisplayName("LinkStatus 状态测试")
    class LinkStatusTest {

        @Test
        void shouldSupportActiveStatus() {
            PromotionLink link = createLink("P1", "A1", "T1");
            link.setLinkStatus("ACTIVE");
            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());
            assertThat(found.getLinkStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void shouldSupportExpiredStatus() {
            PromotionLink link = createLink("P1", "A1", "T1");
            link.setLinkStatus("EXPIRED");
            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());
            assertThat(found.getLinkStatus()).isEqualTo("EXPIRED");
        }

        @Test
        void shouldSupportDisabledStatus() {
            PromotionLink link = createLink("P1", "A1", "T1");
            link.setLinkStatus("DISABLED");
            promotionLinkMapper.insert(link);

            PromotionLink found = promotionLinkMapper.selectById(link.getId());
            assertThat(found.getLinkStatus()).isEqualTo("DISABLED");
        }
    }

    private PromotionLink createLink(String productId, String activityId, String talentId) {
        PromotionLink link = new PromotionLink();
        link.setId(UUID.randomUUID());
        link.setProductId(productId);
        link.setActivityId(activityId);
        link.setTalentId(talentId);
        link.setChannelUserId(UUID.randomUUID());
        link.setPickSource("channel_test");
        link.setLinkStatus("ACTIVE");
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        link.setDeleted(0);
        return link;
    }
}
