package com.colonel.saas.service;

import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full attribution chain:
 * <pre>
 *   TestDouyinOrderGateway → colonel_order_info nested payload
 *     → AttributionSourceNormalizer.flatten(colonel_order_info)
 *     → AttributionService Layer 3 (native colonel_buyin_id lookup)
 *     → order.attributed
 * </pre>
 *
 * <p>Requires: PostgreSQL {@code saas_test} + Redis db1 (started by
 * {@code docker-compose.test.yml} or running locally).</p>
 */
@SpringBootTest(properties = {
        "debug=false",
        "spring.main.banner-mode=off",
        "spring.main.log-startup-info=false",
        "spring.devtools.restart.enabled=false",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.boot=INFO",
        "logging.level.org.springframework.boot.autoconfigure=ERROR",
        "logging.level.org.springframework.web=INFO"
})
@ActiveProfiles("test")
class OrderSyncAttributionIT {

    private static final String COLONEL_BUYIN_ID = "7351155267604218149";
    private static final String TEST_TALENT_ID = "IT_TALENT_001";
    private static final String TEST_TALENT_NAME = "归因集成测试达人";
    private static final String TEST_PRODUCT_ID = "IT_PROD_001";
    private static final String TEST_ACTIVITY_ID = "IT_ACT_001";
    private static final String TEST_PICK_SOURCE = "IT_PICK_NATIVE";

    @Autowired
    private OrderSyncService orderSyncService;

    @Autowired
    private PickSourceMappingService pickSourceMappingService;

    @Autowired
    private PickSourceMappingMapper pickSourceMappingMapper;

    @Autowired
    private ColonelsettlementOrderMapper orderMapper;

    @BeforeEach
    void seedNativeMapping() {
        // Clean any prior test data
        orderMapper.delete(new LambdaQueryWrapper<ColonelsettlementOrder>()
                .likeRight(ColonelsettlementOrder::getOrderId, "MOCK_ORD_ATTR_"));
        pickSourceMappingMapper.delete(new LambdaQueryWrapper<PickSourceMapping>()
                .eq(PickSourceMapping::getPickSource, TEST_PICK_SOURCE));

        UUID userId = UUID.nameUUIDFromBytes("it-test-user".getBytes());
        UUID deptId = UUID.nameUUIDFromBytes("it-test-dept".getBytes());

        // Seed a NATIVE pick_source_mapping with the colonel_buyin_id
        // that TestDouyinOrderGateway uses in its colonel_order_info payload.
        // Uses the 17-param terminal overload which stores sourceType=NATIVE.
        pickSourceMappingService.saveOrUpdate(
                userId,                           // userId
                null,                             // channelUserName
                deptId,                           // deptId
                TEST_TALENT_ID,                   // talentId (maps to rawPayload.talent_uid)
                TEST_TALENT_NAME,                 // talentName
                "ITNATIVE",                       // shortId
                UUID.nameUUIDFromBytes(TEST_PICK_SOURCE.getBytes()), // uuidSeed
                TEST_PICK_SOURCE,                 // pickSource
                TEST_PRODUCT_ID,                  // productId
                TEST_ACTIVITY_ID,                 // activityId
                "https://test.source.local/it",   // sourceUrl
                "https://test.promote.link/it",   // convertedUrl
                null,                             // promotionLinkId
                "PRODUCT_LIBRARY",                // scene
                TEST_PICK_SOURCE,                 // pickExtra
                COLONEL_BUYIN_ID,                 // colonelBuyinId
                PickSourceMappingService.SOURCE_TYPE_NATIVE  // sourceType
        );
    }

    @Test
    @DisplayName("syncLatestWindow attributes order via colonel_order_info → native buyin mapping")
    void syncLatestWindow_attributesOrderViaColonelOrderInfo() {
        // Act — sync triggers TestDouyinOrderGateway which builds an order with
        // colonel_order_info containing COLONEL_BUYIN_ID. The normalizer flattens
        // it to top-level colonel_buyin_id, then AttributionService Layer 3
        // finds the NATIVE mapping we seeded above.
        OrderSyncService.SyncResult result = orderSyncService.syncLatestWindow();

        // Assert — at least one order was attributed
        assertThat(result.attributed())
                .as("At least one order should be attributed via colonel_order_info")
                .isGreaterThanOrEqualTo(1);

        // Verify the persisted order has correct attribution fields
        ColonelsettlementOrder attributed = orderMapper.selectOne(
                new LambdaQueryWrapper<ColonelsettlementOrder>()
                        .likeRight(ColonelsettlementOrder::getOrderId, "MOCK_ORD_ATTR_")
                        .last("limit 1"));

        assertThat(attributed).isNotNull();
        assertThat(attributed.getAttributionStatus())
                .isEqualTo("ATTRIBUTED");
        assertThat(attributed.getAttributionRemark())
                .isEqualTo("COLONEL_ORDER_INFO");
    }
}
