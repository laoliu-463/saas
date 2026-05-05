package com.colonel.saas.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖核心实体类 getter/setter 和默认值。
 */
class EntityTest {

    @Nested
    @DisplayName("SysUser 实体")
    class SysUserTest {

        @Test
        void getterSetter() {
            SysUser user = new SysUser();
            UUID id = UUID.randomUUID();
            user.setId(id);
            user.setUsername("admin");
            user.setPassword("hashed");
            user.setRealName("管理员");
            user.setPhone("13800138000");
            user.setEmail("admin@test.com");
            user.setDeptId(UUID.randomUUID());
            user.setChannelCode("CH01");
            user.setStatus(1);
            user.setLastLoginAt(LocalDateTime.now());

            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getUsername()).isEqualTo("admin");
            assertThat(user.getPassword()).isEqualTo("hashed");
            assertThat(user.getRealName()).isEqualTo("管理员");
            assertThat(user.getPhone()).isEqualTo("13800138000");
            assertThat(user.getEmail()).isEqualTo("admin@test.com");
            assertThat(user.getDeptId()).isNotNull();
            assertThat(user.getChannelCode()).isEqualTo("CH01");
            assertThat(user.getStatus()).isEqualTo(1);
            assertThat(user.getLastLoginAt()).isNotNull();
        }

        @Test
        void defaultStatus_isOne() {
            assertThat(new SysUser().getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SysRole 实体")
    class SysRoleTest {

        @Test
        void getterSetter() {
            SysRole role = new SysRole();
            role.setRoleCode("admin");
            role.setRoleName("管理员");
            role.setDataScope(3);
            role.setPermissions(Map.of("order", "rw"));
            role.setMenuConfig(Map.of("dashboard", true));
            role.setStatus(1);
            role.setRemark("系统管理员");

            assertThat(role.getRoleCode()).isEqualTo("admin");
            assertThat(role.getRoleName()).isEqualTo("管理员");
            assertThat(role.getDataScope()).isEqualTo(3);
            assertThat(role.getPermissions()).containsEntry("order", "rw");
            assertThat(role.getMenuConfig()).containsEntry("dashboard", true);
            assertThat(role.getStatus()).isEqualTo(1);
            assertThat(role.getRemark()).isEqualTo("系统管理员");
        }

        @Test
        void defaults() {
            SysRole role = new SysRole();
            assertThat(role.getDataScope()).isEqualTo(1);
            assertThat(role.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Product 实体")
    class ProductTest {

        @Test
        void getterSetter() {
            Product product = new Product();
            product.setProductId("P001");
            product.setName("测试商品");
            product.setPrice(9900L);
            product.setStatus(1);
            product.setCheckStatus(1);
            product.setCategory("美妆");
            product.setShopName("测试店铺");
            product.setCover("http://img.jpg");
            product.setPriceText("99.00");
            product.setBizStatus("active");
            product.setBizStatusLabel("进行中");
            product.setSelectedToLibrary(true);
            product.setSystemTags(List.of("hot", "new"));
            product.setAlertTags(List.of("low_stock"));

            assertThat(product.getProductId()).isEqualTo("P001");
            assertThat(product.getName()).isEqualTo("测试商品");
            assertThat(product.getPrice()).isEqualTo(9900L);
            assertThat(product.getStatus()).isEqualTo(1);
            assertThat(product.getCategory()).isEqualTo("美妆");
            assertThat(product.getShopName()).isEqualTo("测试店铺");
            assertThat(product.getSelectedToLibrary()).isTrue();
            assertThat(product.getSystemTags()).containsExactly("hot", "new");
            assertThat(product.getAlertTags()).containsExactly("low_stock");
        }
    }

    @Nested
    @DisplayName("Talent 实体")
    class TalentTest {

        @Test
        void getterSetter() {
            Talent talent = new Talent();
            talent.setDouyinUid("12345");
            talent.setDouyinNo("DY001");
            talent.setNickname("达人小王");
            talent.setFans(100000L);
            talent.setLevel("S");
            talent.setAvatarUrl("http://avatar.jpg");
            talent.setCategories("美妆,护肤");
            talent.setBlacklisted(false);
            talent.setStatus(1);
            talent.setLikesCount(50000L);
            talent.setWorksCount(200L);

            assertThat(talent.getDouyinUid()).isEqualTo("12345");
            assertThat(talent.getNickname()).isEqualTo("达人小王");
            assertThat(talent.getFans()).isEqualTo(100000L);
            assertThat(talent.getLevel()).isEqualTo("S");
            assertThat(talent.getBlacklisted()).isFalse();
            assertThat(talent.getLikesCount()).isEqualTo(50000L);
        }

        @Test
        void computedFields_defaultNull() {
            Talent talent = new Talent();
            assertThat(talent.getMonthlySales()).isNull();
            assertThat(talent.getOwnerId()).isNull();
            assertThat(talent.getPoolStatus()).isNull();
            assertThat(talent.getOwnerName()).isNull();
            assertThat(talent.getSampleCount()).isNull();
            assertThat(talent.getOrderCount()).isNull();
            assertThat(talent.getMainCategory()).isNull();
            assertThat(talent.getLiveSalesBand()).isNull();
        }
    }

    @Nested
    @DisplayName("Merchant 实体")
    class MerchantTest {

        @Test
        void getterSetter() {
            Merchant merchant = new Merchant();
            merchant.setMerchantId("M001");
            merchant.setMerchantName("测试商家");
            merchant.setShopId(10001L);
            merchant.setShopName("测试店铺");
            merchant.setSourceOrderId("ORD001");
            merchant.setStatus(1);
            merchant.setExtraData(Map.of("key", "value"));

            assertThat(merchant.getMerchantId()).isEqualTo("M001");
            assertThat(merchant.getMerchantName()).isEqualTo("测试商家");
            assertThat(merchant.getShopId()).isEqualTo(10001L);
            assertThat(merchant.getShopName()).isEqualTo("测试店铺");
            assertThat(merchant.getSourceOrderId()).isEqualTo("ORD001");
            assertThat(merchant.getStatus()).isEqualTo(1);
            assertThat(merchant.getExtraData()).containsEntry("key", "value");
        }
    }

    @Nested
    @DisplayName("OperationLog 实体")
    class OperationLogTest {

        @Test
        void getterSetter() {
            OperationLog log = new OperationLog();
            log.setId(UUID.randomUUID());
            log.setUserId(UUID.randomUUID());
            log.setUsername("admin");
            log.setModule("商品管理");
            log.setAction("创建");
            log.setRequestMethod("POST");
            log.setRequestUrl("/api/products");
            log.setResponseCode("200");
            log.setIpAddress("127.0.0.1");
            log.setDurationMs(150L);
            log.setCreateTime(LocalDateTime.now());

            assertThat(log.getId()).isNotNull();
            assertThat(log.getUserId()).isNotNull();
            assertThat(log.getUsername()).isEqualTo("admin");
            assertThat(log.getModule()).isEqualTo("商品管理");
            assertThat(log.getAction()).isEqualTo("创建");
            assertThat(log.getRequestMethod()).isEqualTo("POST");
            assertThat(log.getResponseCode()).isEqualTo("200");
            assertThat(log.getDurationMs()).isEqualTo(150L);
        }
    }

    @Nested
    @DisplayName("ProductOperationState 实体")
    class ProductOperationStateTest {

        @Test
        void getterSetter() {
            ProductOperationState state = new ProductOperationState();
            state.setActivityId("ACT001");
            state.setProductId("P001");
            state.setBizStatus("active");
            state.setAssigneeId(UUID.randomUUID());
            state.setAuditStatus(1);
            state.setPromoteLink("http://promote");
            state.setSelectedToLibrary(true);

            assertThat(state.getActivityId()).isEqualTo("ACT001");
            assertThat(state.getProductId()).isEqualTo("P001");
            state.setPromoteLink("http://promote");
            assertThat(state.getAuditStatus()).isEqualTo(1);
            assertThat(state.getSelectedToLibrary()).isTrue();
        }
    }

    @Nested
    @DisplayName("ColonelsettlementActivity 实体")
    class ColonelsettlementActivityTest {

        @Test
        void getterSetter() {
            ColonelsettlementActivity activity = new ColonelsettlementActivity();
            activity.setName("测试活动");
            activity.setStartTime(LocalDateTime.now());
            activity.setEndTime(LocalDateTime.now().plusDays(30));
            activity.setStatus(1);

            assertThat(activity.getName()).isEqualTo("测试活动");
            assertThat(activity.getStartTime()).isNotNull();
            assertThat(activity.getEndTime()).isNotNull();
            assertThat(activity.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SampleRequest 实体")
    class SampleRequestTest {

        @Test
        void getterSetter() {
            SampleRequest request = new SampleRequest();
            request.setTalentId(UUID.randomUUID());
            request.setTalentCreditScore(new BigDecimal("4.5"));
            request.setExpectedSampleNum(3);
            request.setActualSampleNum(2);
            request.setStatus(1);

            assertThat(request.getTalentId()).isNotNull();
            assertThat(request.getTalentCreditScore()).isEqualByComparingTo(new BigDecimal("4.5"));
            assertThat(request.getExpectedSampleNum()).isEqualTo(3);
            assertThat(request.getActualSampleNum()).isEqualTo(2);
            assertThat(request.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ExclusiveTalent 实体")
    class ExclusiveTalentTest {

        @Test
        void getterSetter() {
            ExclusiveTalent et = new ExclusiveTalent();
            et.setTalentId(UUID.randomUUID());
            et.setServiceFee(5000L);
            et.setServiceFeeRatio(new BigDecimal("0.15"));

            assertThat(et.getTalentId()).isNotNull();
            assertThat(et.getServiceFee()).isEqualTo(5000L);
            assertThat(et.getServiceFeeRatio()).isEqualByComparingTo(new BigDecimal("0.15"));
        }
    }

    @Nested
    @DisplayName("ExclusiveMerchant 实体")
    class ExclusiveMerchantTest {

        @Test
        void getterSetter() {
            ExclusiveMerchant em = new ExclusiveMerchant();
            em.setUserId(UUID.randomUUID());
            em.setServiceFee(8000L);
            em.setShopId(100L);
            em.setServiceFeeRatio(new BigDecimal("0.20"));

            assertThat(em.getUserId()).isNotNull();
            assertThat(em.getServiceFee()).isEqualTo(8000L);
            assertThat(em.getShopId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("TalentClaim 实体")
    class TalentClaimTest {

        @Test
        void getterSetter() {
            TalentClaim claim = new TalentClaim();
            claim.setTalentId(UUID.randomUUID());
            claim.setUserId(UUID.randomUUID());
            claim.setTalentUid("UID001");
            claim.setClaimType(1);
            claim.setStatus(1);

            assertThat(claim.getTalentId()).isNotNull();
            assertThat(claim.getUserId()).isNotNull();
            assertThat(claim.getTalentUid()).isEqualTo("UID001");
            assertThat(claim.getClaimType()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SystemConfig 实体")
    class SystemConfigTest {

        @Test
        void getterSetter() {
            SystemConfig config = new SystemConfig();
            config.setConfigKey("theme");
            config.setConfigValue("dark");
            config.setConfigType("string");
            config.setConfigGroup("ui");
            config.setConfigName("主题设置");
            config.setSortOrder(1);
            config.setStatus(1);

            assertThat(config.getConfigKey()).isEqualTo("theme");
            assertThat(config.getConfigValue()).isEqualTo("dark");
            assertThat(config.getConfigType()).isEqualTo("string");
            assertThat(config.getSortOrder()).isEqualTo(1);
        }

        @Test
        void defaultSortOrder_isZero() {
            assertThat(new SystemConfig().getSortOrder()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("SysUserRole 实体")
    class SysUserRoleTest {

        @Test
        void getterSetter() {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(UUID.randomUUID());
            ur.setRoleId(UUID.randomUUID());
            ur.setCreateTime(LocalDateTime.now());
            ur.setDeleted(0);

            assertThat(ur.getUserId()).isNotNull();
            assertThat(ur.getRoleId()).isNotNull();
            assertThat(ur.getCreateTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ProductSnapshot 实体")
    class ProductSnapshotTest {

        @Test
        void getterSetter() {
            ProductSnapshot snap = new ProductSnapshot();
            snap.setActivityId("ACT001");
            snap.setProductId("P001");
            snap.setTitle("商品快照");
            snap.setPrice(9900L);
            snap.setStatus(1);

            assertThat(snap.getActivityId()).isEqualTo("ACT001");
            assertThat(snap.getProductId()).isEqualTo("P001");
            assertThat(snap.getTitle()).isEqualTo("商品快照");
            assertThat(snap.getPrice()).isEqualTo(9900L);
        }
    }
}
