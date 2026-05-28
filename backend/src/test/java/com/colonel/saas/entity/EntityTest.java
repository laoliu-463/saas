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
    @DisplayName("SysDept 实体")
    class SysDeptTest {

        @Test
        void getterSetter() {
            SysDept dept = new SysDept();
            UUID id = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            dept.setId(id);
            dept.setParentId(parentId);
            dept.setDeptCode("BIZ");
            dept.setDeptName("招商部");
            dept.setLeader("负责人A");
            dept.setPhone("010-88886666");
            dept.setEmail("biz@test.com");
            dept.setSortOrder(10);
            dept.setStatus(1);
            dept.setRemark("默认招商部门");

            assertThat(dept.getId()).isEqualTo(id);
            assertThat(dept.getParentId()).isEqualTo(parentId);
            assertThat(dept.getDeptCode()).isEqualTo("BIZ");
            assertThat(dept.getDeptName()).isEqualTo("招商部");
            assertThat(dept.getLeader()).isEqualTo("负责人A");
            assertThat(dept.getPhone()).isEqualTo("010-88886666");
            assertThat(dept.getEmail()).isEqualTo("biz@test.com");
            assertThat(dept.getSortOrder()).isEqualTo(10);
            assertThat(dept.getStatus()).isEqualTo(1);
            assertThat(dept.getRemark()).isEqualTo("默认招商部门");
        }

        @Test
        void defaults() {
            SysDept dept = new SysDept();
            assertThat(dept.getSortOrder()).isEqualTo(0);
            assertThat(dept.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Product 实体")
    class ProductTest {

        @Test
        void getterSetter() {
            Product product = new Product();
            product.setProductId("P001");
            product.setOuterProductId("OUT001");
            product.setName("测试商品");
            product.setDescription("商品描述");
            product.setMarketPrice(12900L);
            product.setPrice(9900L);
            product.setCosRatio(new BigDecimal("25.00"));
            product.setCosFee(2500L);
            product.setServiceRatio(new BigDecimal("8.00"));
            product.setStatus(1);
            product.setCheckStatus(1);
            product.setCategory("美妆");
            product.setDetailUrl("https://example.com/p/1");
            product.setCategoryDetail(Map.of("first", "美妆"));
            product.setPics(List.of("https://example.com/1.png"));
            product.setSpecPrices(List.of(Map.of("sku", "默认款", "price", 9900)));
            product.setShopName("测试店铺");
            product.setCover("http://img.jpg");
            product.setPriceText("99.00");
            product.setBizStatus("active");
            product.setBizStatusLabel("进行中");
            product.setSelectedToLibrary(true);
            product.setSystemTags(List.of("hot", "new"));
            product.setAlertTags(List.of("low_stock"));

            assertThat(product.getProductId()).isEqualTo("P001");
            assertThat(product.getOuterProductId()).isEqualTo("OUT001");
            assertThat(product.getName()).isEqualTo("测试商品");
            assertThat(product.getDescription()).isEqualTo("商品描述");
            assertThat(product.getMarketPrice()).isEqualTo(12900L);
            assertThat(product.getPrice()).isEqualTo(9900L);
            assertThat(product.getCosRatio()).isEqualByComparingTo("25.00");
            assertThat(product.getCosFee()).isEqualTo(2500L);
            assertThat(product.getServiceRatio()).isEqualByComparingTo("8.00");
            assertThat(product.getStatus()).isEqualTo(1);
            assertThat(product.getCategory()).isEqualTo("美妆");
            assertThat(product.getDetailUrl()).isEqualTo("https://example.com/p/1");
            assertThat(product.getCategoryDetail()).containsEntry("first", "美妆");
            assertThat(product.getPics()).containsExactly("https://example.com/1.png");
            assertThat(product.getSpecPrices()).hasSize(1);
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
            activity.setActivityId("ACT001");
            activity.setName("测试活动");
            activity.setActivityType("招商活动");
            activity.setShopId(3001L);
            activity.setShopName("测试店铺");
            activity.setColonelBuyinId(90001L);
            activity.setCommissionRate(new BigDecimal("0.2000"));
            activity.setServiceRate(new BigDecimal("0.0500"));
            activity.setStartTime(LocalDateTime.now());
            activity.setEndTime(LocalDateTime.now().plusDays(30));
            activity.setLastSyncAt(LocalDateTime.now());
            activity.setMonthsOfProtection(30);
            activity.setExtraData(Map.of("source", "seed"));
            activity.setStatus(1);

            assertThat(activity.getActivityId()).isEqualTo("ACT001");
            assertThat(activity.getName()).isEqualTo("测试活动");
            assertThat(activity.getActivityType()).isEqualTo("招商活动");
            assertThat(activity.getShopId()).isEqualTo(3001L);
            assertThat(activity.getShopName()).isEqualTo("测试店铺");
            assertThat(activity.getColonelBuyinId()).isEqualTo(90001L);
            assertThat(activity.getCommissionRate()).isEqualByComparingTo("0.2000");
            assertThat(activity.getServiceRate()).isEqualByComparingTo("0.0500");
            assertThat(activity.getStartTime()).isNotNull();
            assertThat(activity.getEndTime()).isNotNull();
            assertThat(activity.getLastSyncAt()).isNotNull();
            assertThat(activity.getMonthsOfProtection()).isEqualTo(30);
            assertThat(activity.getExtraData()).containsEntry("source", "seed");
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

    @Nested
    @DisplayName("PickSourceMapping 实体")
    class PickSourceMappingTest {

        @Test
        void getterSetter() {
            PickSourceMapping mapping = new PickSourceMapping();
            UUID id = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID deptId = UUID.randomUUID();
            UUID uuidSeed = UUID.randomUUID();
            UUID promotionLinkId = UUID.randomUUID();

            mapping.setId(id);
            mapping.setUserId(userId);
            mapping.setShortId("SHORT01");
            mapping.setUuidSeed(uuidSeed);
            mapping.setDeptId(deptId);
            mapping.setPickSource("PS_abc123");
            mapping.setColonelBuyinId("COL001");
            mapping.setProductId("P001");
            mapping.setActivityId("ACT001");
            mapping.setSourceUrl("https://original.url");
            mapping.setConvertedUrl("https://converted.url");
            mapping.setPickExtra("extra_data");
            mapping.setPromotionLinkId(promotionLinkId);
            mapping.setChannelUserName("渠道用户");
            mapping.setTalentId("T001");
            mapping.setTalentName("达人名称");
            mapping.setScene("PRODUCT_PROMOTE");
            mapping.setSourceType("MANUAL");
            mapping.setValidFrom(LocalDateTime.now());
            mapping.setValidUntil(LocalDateTime.now().plusDays(30));
            mapping.setStatus(1);

            assertThat(mapping.getId()).isEqualTo(id);
            assertThat(mapping.getUserId()).isEqualTo(userId);
            assertThat(mapping.getShortId()).isEqualTo("SHORT01");
            assertThat(mapping.getUuidSeed()).isEqualTo(uuidSeed);
            assertThat(mapping.getDeptId()).isEqualTo(deptId);
            assertThat(mapping.getPickSource()).isEqualTo("PS_abc123");
            assertThat(mapping.getColonelBuyinId()).isEqualTo("COL001");
            assertThat(mapping.getProductId()).isEqualTo("P001");
            assertThat(mapping.getActivityId()).isEqualTo("ACT001");
            assertThat(mapping.getSourceUrl()).isEqualTo("https://original.url");
            assertThat(mapping.getConvertedUrl()).isEqualTo("https://converted.url");
            assertThat(mapping.getPickExtra()).isEqualTo("extra_data");
            assertThat(mapping.getPromotionLinkId()).isEqualTo(promotionLinkId);
            assertThat(mapping.getChannelUserName()).isEqualTo("渠道用户");
            assertThat(mapping.getTalentId()).isEqualTo("T001");
            assertThat(mapping.getTalentName()).isEqualTo("达人名称");
            assertThat(mapping.getScene()).isEqualTo("PRODUCT_PROMOTE");
            assertThat(mapping.getSourceType()).isEqualTo("MANUAL");
            assertThat(mapping.getValidFrom()).isNotNull();
            assertThat(mapping.getValidUntil()).isNotNull();
            assertThat(mapping.getStatus()).isEqualTo(1);
        }

        @Test
        void defaultStatus_isNull() {
            PickSourceMapping mapping = new PickSourceMapping();
            assertThat(mapping.getStatus()).isNull();
        }
    }

    @Nested
    @DisplayName("PerformanceRecord 实体")
    class PerformanceRecordTest {

        @Test
        void getterSetter() {
            PerformanceRecord record = new PerformanceRecord();
            UUID id = UUID.randomUUID();
            UUID orderRowId = UUID.randomUUID();
            UUID defaultChannelUserId = UUID.randomUUID();
            UUID defaultRecruiterUserId = UUID.randomUUID();
            UUID finalChannelUserId = UUID.randomUUID();
            UUID finalRecruiterUserId = UUID.randomUUID();
            UUID talentId = UUID.randomUUID();

            record.setId(id);
            record.setOrderId("ORD123456");
            record.setOrderRowId(orderRowId);
            record.setDefaultChannelUserId(defaultChannelUserId);
            record.setDefaultRecruiterUserId(defaultRecruiterUserId);
            record.setFinalChannelUserId(finalChannelUserId);
            record.setFinalRecruiterUserId(finalRecruiterUserId);
            record.setChannelAttribution("PICK_SOURCE");
            record.setRecruiterAttribution("CLAIM");
            record.setTalentId(talentId);
            record.setPartnerId(90001L);
            record.setProductId("P001");
            record.setActivityId("ACT001");
            record.setPayAmount(19900L);
            record.setSettleAmount(18900L);
            record.setEstimateServiceFee(1000L);
            record.setEffectiveServiceFee(950L);
            record.setEstimateTechServiceFee(500L);
            record.setEffectiveTechServiceFee(480L);
            record.setEstimateServiceProfit(800L);
            record.setEffectiveServiceProfit(760L);
            record.setEstimateRecruiterCommission(400L);
            record.setEffectiveRecruiterCommission(380L);
            record.setEstimateChannelCommission(300L);
            record.setEffectiveChannelCommission(285L);
            record.setEstimateGrossProfit(1500L);
            record.setEffectiveGrossProfit(1425L);
            record.setRecruiterCommissionRate(new BigDecimal("0.20"));
            record.setChannelCommissionRate(new BigDecimal("0.15"));
            record.setOrderStatus(3);
            record.setSettleTime(LocalDateTime.now());
            record.setOrderCreateTime(LocalDateTime.now().minusDays(7));
            record.setValid(true);
            record.setReversed(false);
            record.setCalculationVersion(1);
            record.setCalculatedAt(LocalDateTime.now());
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());

            assertThat(record.getId()).isEqualTo(id);
            assertThat(record.getOrderId()).isEqualTo("ORD123456");
            assertThat(record.getOrderRowId()).isEqualTo(orderRowId);
            assertThat(record.getDefaultChannelUserId()).isEqualTo(defaultChannelUserId);
            assertThat(record.getDefaultRecruiterUserId()).isEqualTo(defaultRecruiterUserId);
            assertThat(record.getFinalChannelUserId()).isEqualTo(finalChannelUserId);
            assertThat(record.getFinalRecruiterUserId()).isEqualTo(finalRecruiterUserId);
            assertThat(record.getChannelAttribution()).isEqualTo("PICK_SOURCE");
            assertThat(record.getRecruiterAttribution()).isEqualTo("CLAIM");
            assertThat(record.getTalentId()).isEqualTo(talentId);
            assertThat(record.getPartnerId()).isEqualTo(90001L);
            assertThat(record.getProductId()).isEqualTo("P001");
            assertThat(record.getActivityId()).isEqualTo("ACT001");
            assertThat(record.getPayAmount()).isEqualTo(19900L);
            assertThat(record.getSettleAmount()).isEqualTo(18900L);
            assertThat(record.getEstimateServiceFee()).isEqualTo(1000L);
            assertThat(record.getEffectiveServiceFee()).isEqualTo(950L);
            assertThat(record.getEstimateTechServiceFee()).isEqualTo(500L);
            assertThat(record.getEffectiveTechServiceFee()).isEqualTo(480L);
            assertThat(record.getEstimateServiceProfit()).isEqualTo(800L);
            assertThat(record.getEffectiveServiceProfit()).isEqualTo(760L);
            assertThat(record.getEstimateRecruiterCommission()).isEqualTo(400L);
            assertThat(record.getEffectiveRecruiterCommission()).isEqualTo(380L);
            assertThat(record.getEstimateChannelCommission()).isEqualTo(300L);
            assertThat(record.getEffectiveChannelCommission()).isEqualTo(285L);
            assertThat(record.getEstimateGrossProfit()).isEqualTo(1500L);
            assertThat(record.getEffectiveGrossProfit()).isEqualTo(1425L);
            assertThat(record.getRecruiterCommissionRate()).isEqualByComparingTo("0.20");
            assertThat(record.getChannelCommissionRate()).isEqualByComparingTo("0.15");
            assertThat(record.getOrderStatus()).isEqualTo(3);
            assertThat(record.getSettleTime()).isNotNull();
            assertThat(record.getOrderCreateTime()).isNotNull();
            assertThat(record.getValid()).isTrue();
            assertThat(record.getReversed()).isFalse();
            assertThat(record.getCalculationVersion()).isEqualTo(1);
            assertThat(record.getCalculatedAt()).isNotNull();
            assertThat(record.getCreatedAt()).isNotNull();
            assertThat(record.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ColonelPartner 实体")
    class ColonelPartnerTest {

        @Test
        void getterSetter() {
            ColonelPartner partner = new ColonelPartner();
            UUID id = UUID.randomUUID();

            partner.setId(id);
            partner.setColonelBuyinId("COL001");
            partner.setColonelName("测试团长");
            partner.setContactName("联系人");
            partner.setContactPhone("13800138000");
            partner.setAvatarUrl("https://avatar.url");
            partner.setContactWechat("wechat_id");
            partner.setContactRemark("备注信息");
            partner.setSource("BUYIN");
            partner.setFirstSeenAt(LocalDateTime.now());
            partner.setLastSyncAt(LocalDateTime.now());
            partner.setManualContactUpdatedAt(LocalDateTime.now());
            partner.setManualContactUpdatedBy("admin");
            partner.setRawPayload(Map.of("key", "value"));
            partner.setSourceUpdatedAt(LocalDateTime.now());

            assertThat(partner.getId()).isEqualTo(id);
            assertThat(partner.getColonelBuyinId()).isEqualTo("COL001");
            assertThat(partner.getColonelName()).isEqualTo("测试团长");
            assertThat(partner.getContactName()).isEqualTo("联系人");
            assertThat(partner.getContactPhone()).isEqualTo("13800138000");
            assertThat(partner.getAvatarUrl()).isEqualTo("https://avatar.url");
            assertThat(partner.getContactWechat()).isEqualTo("wechat_id");
            assertThat(partner.getContactRemark()).isEqualTo("备注信息");
            assertThat(partner.getSource()).isEqualTo("BUYIN");
            assertThat(partner.getFirstSeenAt()).isNotNull();
            assertThat(partner.getLastSyncAt()).isNotNull();
            assertThat(partner.getManualContactUpdatedAt()).isNotNull();
            assertThat(partner.getManualContactUpdatedBy()).isEqualTo("admin");
            assertThat(partner.getRawPayload()).containsEntry("key", "value");
            assertThat(partner.getSourceUpdatedAt()).isNotNull();
        }
    }
}
