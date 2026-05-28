package com.colonel.saas.mapper;

import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ColonelPartnerMapper 集成测试 - 使用 Testcontainers 真实 PostgreSQL
 */
@DockerAvailable
class ColonelPartnerMapperTest extends BaseIntegrationTest {

    @Autowired
    private ColonelPartnerMapper colonelPartnerMapper;

    @Nested
    @DisplayName("BaseMapper operations")
    class BaseMapperOperationsTest {

        @Test
        void insertAndSelectById() {
            ColonelPartner partner = createPartner("COL001", "测试团长");
            colonelPartnerMapper.insert(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());

            assertThat(found).isNotNull();
            assertThat(found.getColonelBuyinId()).isEqualTo("COL001");
            assertThat(found.getColonelName()).isEqualTo("测试团长");
        }

        @Test
        void updateById() {
            ColonelPartner partner = createPartner("COL001", "测试团长");
            colonelPartnerMapper.insert(partner);

            partner.setContactPhone("13900139000");
            partner.setContactWechat("new_wechat");
            colonelPartnerMapper.updateById(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());

            assertThat(found.getContactPhone()).isEqualTo("13900139000");
            assertThat(found.getContactWechat()).isEqualTo("new_wechat");
        }

        @Test
        void deleteById() {
            ColonelPartner partner = createPartner("COL001", "测试团长");
            colonelPartnerMapper.insert(partner);

            int rows = colonelPartnerMapper.deleteById(partner.getId());

            assertThat(rows).isEqualTo(1);
        }

        @Test
        void selectBatchIds() {
            ColonelPartner p1 = createPartner("COL001", "团长1");
            ColonelPartner p2 = createPartner("COL002", "团长2");
            colonelPartnerMapper.insert(p1);
            colonelPartnerMapper.insert(p2);

            List<ColonelPartner> found = colonelPartnerMapper.selectBatchIds(List.of(p1.getId(), p2.getId()));

            assertThat(found).hasSize(2);
        }
    }

    @Nested
    @DisplayName("字段映射测试")
    class FieldMappingTest {

        @Test
        void shouldMapAllFields() {
            ColonelPartner partner = new ColonelPartner();
            partner.setId(UUID.randomUUID());
            partner.setColonelBuyinId("COL001");
            partner.setColonelName("测试团长");
            partner.setContactName("联系人");
            partner.setContactPhone("13800138000");
            partner.setAvatarUrl("https://avatar.url/pic.jpg");
            partner.setContactWechat("wechat_id");
            partner.setContactRemark("备注信息");
            partner.setSource("BUYIN");
            partner.setFirstSeenAt(LocalDateTime.now());
            partner.setLastSyncAt(LocalDateTime.now());
            partner.setManualContactUpdatedAt(LocalDateTime.now());
            partner.setManualContactUpdatedBy("admin");
            partner.setRawPayload(Map.of("sync_version", 1));
            partner.setSourceUpdatedAt(LocalDateTime.now());

            colonelPartnerMapper.insert(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());

            assertThat(found.getColonelBuyinId()).isEqualTo("COL001");
            assertThat(found.getColonelName()).isEqualTo("测试团长");
            assertThat(found.getContactName()).isEqualTo("联系人");
            assertThat(found.getContactPhone()).isEqualTo("13800138000");
            assertThat(found.getAvatarUrl()).isEqualTo("https://avatar.url/pic.jpg");
            assertThat(found.getContactWechat()).isEqualTo("wechat_id");
            assertThat(found.getContactRemark()).isEqualTo("备注信息");
            assertThat(found.getSource()).isEqualTo("BUYIN");
            assertThat(found.getFirstSeenAt()).isNotNull();
            assertThat(found.getLastSyncAt()).isNotNull();
            assertThat(found.getManualContactUpdatedAt()).isNotNull();
            assertThat(found.getManualContactUpdatedBy()).isEqualTo("admin");
            assertThat(found.getRawPayload()).containsEntry("sync_version", 1);
        }

        @Test
        void shouldHandleNullOptionalFields() {
            ColonelPartner partner = createPartner("COL001", "测试团长");
            // 不设置可选字段

            colonelPartnerMapper.insert(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());

            assertThat(found).isNotNull();
            assertThat(found.getContactName()).isNull();
            assertThat(found.getContactPhone()).isNull();
            assertThat(found.getAvatarUrl()).isNull();
            assertThat(found.getContactWechat()).isNull();
            assertThat(found.getRawPayload()).isNull();
        }
    }

    @Nested
    @DisplayName("数据来源测试")
    class SourceTest {

        @Test
        void shouldSupportBuyinSource() {
            ColonelPartner partner = createPartner("COL001", "百应团长");
            partner.setSource("BUYIN");
            colonelPartnerMapper.insert(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());
            assertThat(found.getSource()).isEqualTo("BUYIN");
        }

        @Test
        void shouldSupportManualSource() {
            ColonelPartner partner = createPartner("COL002", "手动录入团长");
            partner.setSource("MANUAL");
            colonelPartnerMapper.insert(partner);

            ColonelPartner found = colonelPartnerMapper.selectById(partner.getId());
            assertThat(found.getSource()).isEqualTo("MANUAL");
        }
    }

    private ColonelPartner createPartner(String buyinId, String name) {
        ColonelPartner partner = new ColonelPartner();
        partner.setId(UUID.randomUUID());
        partner.setColonelBuyinId(buyinId);
        partner.setColonelName(name);
        partner.setSource("BUYIN");
        partner.setFirstSeenAt(LocalDateTime.now());
        partner.setLastSyncAt(LocalDateTime.now());
        return partner;
    }
}
