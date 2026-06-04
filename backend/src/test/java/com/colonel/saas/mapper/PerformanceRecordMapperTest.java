package com.colonel.saas.mapper;

import com.colonel.saas.entity.PerformanceRecord;
import com.colonel.saas.testsupport.BaseIntegrationTest;
import com.colonel.saas.testsupport.DockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerformanceRecordMapper 集成测试 - 使用 Testcontainers 真实 PostgreSQL
 */
@DockerAvailable
class PerformanceRecordMapperTest extends BaseIntegrationTest {

    @Autowired
    private PerformanceRecordMapper performanceRecordMapper;

    @Nested
    @DisplayName("findByOrderId")
    class FindByOrderIdTest {

        @Test
        void shouldReturnNullWhenNotFound() {
            PerformanceRecord result = performanceRecordMapper.findByOrderId("NONEXISTENT");
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnRecordWhenFound() {
            PerformanceRecord record = createRecord("ORD123456");
            performanceRecordMapper.upsert(record);

            PerformanceRecord result = performanceRecordMapper.findByOrderId("ORD123456");

            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo("ORD123456");
            assertThat(result.getPayAmount()).isEqualTo(19900L);
        }

        @Test
        void shouldReturnFirstRecordWhenMultipleExist() {
            // 创建两条记录，虽然通常不会发生
            PerformanceRecord record1 = createRecord("ORD_DUP");
            PerformanceRecord record2 = createRecord("ORD_DUP");
            record2.setId(UUID.randomUUID()); // 不同的 ID
            performanceRecordMapper.upsert(record1);
            performanceRecordMapper.upsert(record2);

            // UPSERT 会更新而不是插入，所以应该只有一条
            PerformanceRecord result = performanceRecordMapper.findByOrderId("ORD_DUP");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("upsert")
    class UpsertTest {

        @Test
        void shouldInsertNewRecord() {
            PerformanceRecord record = createRecord("ORD_INSERT");

            int rows = performanceRecordMapper.upsert(record);

            assertThat(rows).isGreaterThanOrEqualTo(1);
            PerformanceRecord found = performanceRecordMapper.findByOrderId("ORD_INSERT");
            assertThat(found).isNotNull();
            assertThat(found.getOrderId()).isEqualTo("ORD_INSERT");
        }

        @Test
        void shouldUpdateExistingRecord() {
            PerformanceRecord record = createRecord("ORD_UPDATE");
            performanceRecordMapper.upsert(record);

            // 修改数据
            record.setPayAmount(29900L);
            record.setEffectiveServiceFee(1500L);
            performanceRecordMapper.upsert(record);

            PerformanceRecord found = performanceRecordMapper.findByOrderId("ORD_UPDATE");

            assertThat(found).isNotNull();
            assertThat(found.getPayAmount()).isEqualTo(29900L);
            assertThat(found.getEffectiveServiceFee()).isEqualTo(1500L);
        }

        @Test
        void shouldIncrementCalculationVersionOnUpdate() {
            PerformanceRecord record = createRecord("ORD_VERSION");
            record.setCalculationVersion(1);
            performanceRecordMapper.upsert(record);

            PerformanceRecord first = performanceRecordMapper.findByOrderId("ORD_VERSION");
            assertThat(first.getCalculationVersion()).isEqualTo(1);

            // 再次 UPSERT 应该增加版本号
            performanceRecordMapper.upsert(record);

            PerformanceRecord second = performanceRecordMapper.findByOrderId("ORD_VERSION");
            assertThat(second.getCalculationVersion()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("字段映射测试")
    class FieldMappingTest {

        @Test
        void shouldMapAllFields() {
            PerformanceRecord record = new PerformanceRecord();
            UUID id = UUID.randomUUID();
            UUID orderRowId = UUID.randomUUID();
            UUID defaultChannelUserId = UUID.randomUUID();
            UUID defaultRecruiterUserId = UUID.randomUUID();
            UUID finalChannelUserId = UUID.randomUUID();
            UUID finalRecruiterUserId = UUID.randomUUID();
            UUID talentId = UUID.randomUUID();

            record.setId(id);
            record.setOrderId("ORD_FIELDS");
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

            performanceRecordMapper.upsert(record);

            PerformanceRecord found = performanceRecordMapper.findByOrderId("ORD_FIELDS");

            assertThat(found).isNotNull();
            assertThat(found.getOrderId()).isEqualTo("ORD_FIELDS");
            assertThat(found.getOrderRowId()).isEqualTo(orderRowId);
            assertThat(found.getDefaultChannelUserId()).isEqualTo(defaultChannelUserId);
            assertThat(found.getDefaultRecruiterUserId()).isEqualTo(defaultRecruiterUserId);
            assertThat(found.getFinalChannelUserId()).isEqualTo(finalChannelUserId);
            assertThat(found.getFinalRecruiterUserId()).isEqualTo(finalRecruiterUserId);
            assertThat(found.getChannelAttribution()).isEqualTo("PICK_SOURCE");
            assertThat(found.getRecruiterAttribution()).isEqualTo("CLAIM");
            assertThat(found.getTalentId()).isEqualTo(talentId);
            assertThat(found.getPartnerId()).isEqualTo(90001L);
            assertThat(found.getProductId()).isEqualTo("P001");
            assertThat(found.getActivityId()).isEqualTo("ACT001");
            assertThat(found.getPayAmount()).isEqualTo(19900L);
            assertThat(found.getSettleAmount()).isEqualTo(18900L);
            assertThat(found.getEstimateServiceFee()).isEqualTo(1000L);
            assertThat(found.getEffectiveServiceFee()).isEqualTo(950L);
            assertThat(found.getEstimateTechServiceFee()).isEqualTo(500L);
            assertThat(found.getEffectiveTechServiceFee()).isEqualTo(480L);
            assertThat(found.getEstimateServiceProfit()).isEqualTo(800L);
            assertThat(found.getEffectiveServiceProfit()).isEqualTo(760L);
            assertThat(found.getEstimateRecruiterCommission()).isEqualTo(400L);
            assertThat(found.getEffectiveRecruiterCommission()).isEqualTo(380L);
            assertThat(found.getEstimateChannelCommission()).isEqualTo(300L);
            assertThat(found.getEffectiveChannelCommission()).isEqualTo(285L);
            assertThat(found.getEstimateGrossProfit()).isEqualTo(1500L);
            assertThat(found.getEffectiveGrossProfit()).isEqualTo(1425L);
            assertThat(found.getRecruiterCommissionRate()).isEqualByComparingTo("0.20");
            assertThat(found.getChannelCommissionRate()).isEqualByComparingTo("0.15");
            assertThat(found.getOrderStatus()).isEqualTo(3);
            assertThat(found.getSettleTime()).isNotNull();
            assertThat(found.getOrderCreateTime()).isNotNull();
            assertThat(found.getValid()).isTrue();
            assertThat(found.getReversed()).isFalse();
            assertThat(found.getCalculationVersion()).isEqualTo(1);
            assertThat(found.getCalculatedAt()).isNotNull();
            assertThat(found.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findByOrderIds")
    class FindByOrderIdsTest {

        @Test
        void shouldReturnEmptyForEmptyList() {
            List<PerformanceRecord> result = performanceRecordMapper.findByOrderIds(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnMatchingRecords() {
            PerformanceRecord r1 = createRecord("BATCH_001");
            PerformanceRecord r2 = createRecord("BATCH_002");
            PerformanceRecord r3 = createRecord("BATCH_003");
            performanceRecordMapper.upsert(r1);
            performanceRecordMapper.upsert(r2);
            performanceRecordMapper.upsert(r3);

            List<PerformanceRecord> result = performanceRecordMapper.findByOrderIds(
                    List.of("BATCH_001", "BATCH_003", "NONEXISTENT"));

            assertThat(result).hasSize(2);
            List<String> orderIds = result.stream().map(PerformanceRecord::getOrderId).toList();
            assertThat(orderIds).containsExactlyInAnyOrder("BATCH_001", "BATCH_003");
        }

        @Test
        void shouldExcludeInvalidRecords() {
            PerformanceRecord record = createRecord("BATCH_INVALID");
            record.setValid(false);
            performanceRecordMapper.upsert(record);

            List<PerformanceRecord> result = performanceRecordMapper.findByOrderIds(List.of("BATCH_INVALID"));
            assertThat(result).isEmpty();
        }
    }

    private PerformanceRecord createRecord(String orderId) {
        PerformanceRecord record = new PerformanceRecord();
        record.setId(UUID.randomUUID());
        record.setOrderId(orderId);
        record.setOrderRowId(UUID.randomUUID());
        record.setDefaultChannelUserId(UUID.randomUUID());
        record.setDefaultRecruiterUserId(UUID.randomUUID());
        record.setFinalChannelUserId(UUID.randomUUID());
        record.setFinalRecruiterUserId(UUID.randomUUID());
        record.setChannelAttribution("PICK_SOURCE");
        record.setRecruiterAttribution("CLAIM");
        record.setTalentId(UUID.randomUUID());
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
        record.setValid(true);
        record.setReversed(false);
        record.setCalculationVersion(1);
        record.setCalculatedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }
}
