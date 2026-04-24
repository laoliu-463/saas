package com.colonel.saas.service;

import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusiveMerchantServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ExclusiveMerchantMapper exclusiveMerchantMapper;

    private ExclusiveMerchantService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveMerchantService(jdbcTemplate, exclusiveMerchantMapper);
    }

    private static final String KEY_RATIO = "merchant.exclusive.service_fee_ratio";

    @Test
    void findActiveOwnerByMerchantId_shouldReturnOwner() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ExclusiveMerchant record = new ExclusiveMerchant();
        record.setUserId(userId);
        record.setDeptId(deptId);
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(record);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByMerchantId("merchant-1");

        assertThat(owner).isNotNull();
        assertThat(owner.userId()).isEqualTo(userId);
        assertThat(owner.deptId()).isEqualTo(deptId);
    }

    @Test
    void findActiveOwnerByMerchantId_shouldReturnNullWhenNotFound() {
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(null);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByMerchantId("merchant-x");

        assertThat(owner).isNull();
    }

    // ─── evaluateMonth tests ────────────────────────────────────────────────

    @Test
    void evaluateMonth_shouldReturnZeroWhenMerchantRowsEmpty() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        stubConfig("70");
        stubUserTotalFeeEmpty(start, end);
        stubMerchantUserFeeEmpty(start, end);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
        verify(exclusiveMerchantMapper, never()).insert(any());
        verify(exclusiveMerchantMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldInsertWhenRatioAtThreshold() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String merchantId = "merchant-upsert";
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70");
        stubUserTotalFeeWithEntry(start, end, userId, 1000L);
        stubMerchantUserFeeWithEntry(start, end, merchantId, userId, deptId, 700L);
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(null);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        ArgumentCaptor<ExclusiveMerchant> captor = ArgumentCaptor.forClass(ExclusiveMerchant.class);
        verify(exclusiveMerchantMapper).insert(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(merchantId);
        verify(exclusiveMerchantMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldUpdateWhenRecordExists() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String merchantId = "merchant-existing";
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70");
        stubUserTotalFeeWithEntry(start, end, userId, 1000L);
        stubMerchantUserFeeWithEntry(start, end, merchantId, userId, deptId, 800L);
        ExclusiveMerchant existing = new ExclusiveMerchant();
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(existing);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        verify(exclusiveMerchantMapper).updateById(any(ExclusiveMerchant.class));
        verify(exclusiveMerchantMapper, never()).insert(any());
    }

    @Test
    void evaluateMonth_shouldSkipRowWhenRatioBelowThreshold() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String merchantId = "merchant-low";
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70");
        stubUserTotalFeeWithEntry(start, end, userId, 1000L);
        // Merchant fee 100 / total fee 1000 = 10% < 70% threshold
        stubMerchantUserFeeWithEntry(start, end, merchantId, userId, deptId, 100L);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
        verify(exclusiveMerchantMapper, never()).insert(any());
        verify(exclusiveMerchantMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldUseDefaultThresholdOnConfigError() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        doThrow(new RuntimeException("db error")).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_RATIO));
        stubUserTotalFeeEmpty(start, end);
        stubMerchantUserFeeEmpty(start, end);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
    }

    // ─── Helper stubs ──────────────────────────────────────────────────────

    private void stubConfig(String value) {
        doReturn(value).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_RATIO));
    }

    private void stubUserTotalFeeEmpty(LocalDateTime start, LocalDateTime end) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(start), eq(end)))
                .thenReturn(List.of());
    }

    private void stubMerchantUserFeeEmpty(LocalDateTime start, LocalDateTime end) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(start), eq(end)))
                .thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private void stubUserTotalFeeWithEntry(LocalDateTime start, LocalDateTime end,
                                            UUID userId, long totalFee) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("user_id")).thenReturn(userId.toString());
        when(rs.getLong("total_fee")).thenReturn(totalFee);

        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            mapper.mapRow(rs, 1);
            return null;
        }).when(jdbcTemplate)
                .query(contains("GROUP BY user_id"), any(RowMapper.class), eq(start), eq(end));
        when(jdbcTemplate.query(contains("GROUP BY user_id"), any(RowMapper.class), eq(start), eq(end)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
    }

    @SuppressWarnings("unchecked")
    private void stubMerchantUserFeeWithEntry(LocalDateTime start, LocalDateTime end,
                                               String merchantId, UUID userId,
                                               UUID deptId, long merchantFee) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("merchant_id")).thenReturn(merchantId);
        when(rs.getString("user_id")).thenReturn(userId.toString());
        when(rs.getString("dept_id")).thenReturn(deptId.toString());
        when(rs.getLong("merchant_fee")).thenReturn(merchantFee);
        when(rs.getString("shop_name")).thenReturn("Test Shop");
        when(rs.getObject("shop_id")).thenReturn(1L);

        doAnswer(inv -> {
            RowMapper<?> mapper = inv.getArgument(1);
            mapper.mapRow(rs, 1);
            return null;
        }).when(jdbcTemplate)
                .query(contains("merchant_id"), any(RowMapper.class), eq(start), eq(end));
        when(jdbcTemplate.query(contains("merchant_id"), any(RowMapper.class), eq(start), eq(end)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
    }
}
