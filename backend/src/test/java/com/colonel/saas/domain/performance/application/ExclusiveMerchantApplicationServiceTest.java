package com.colonel.saas.domain.performance.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.config.facade.dto.ExclusiveRulesDTO;
import com.colonel.saas.domain.performance.domain.ExclusiveMerchantRepository;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.entity.ExclusiveMerchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExclusiveMerchantApplicationServiceTest {

    @Mock
    private ConfigDomainFacade configDomainFacade;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ExclusiveMerchantRepository repository;
    @Mock
    private UserDomainFacade userDomainFacade;

    private ExclusiveMerchantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveMerchantApplicationService(configDomainFacade, jdbcTemplate, repository, userDomainFacade);
    }

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
        verify(repository, never()).save(any());
        verify(repository, never()).update(any());
    }

    @Test
    void evaluateMonth_shouldUsePerformanceRecordsDefaultRecruiterStatistics() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        stubConfig("70");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(start), eq(end)))
                .thenReturn(List.of());

        service.evaluateMonth(stats, apply);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(RowMapper.class), eq(start), eq(end));
        String combinedSql = String.join("\n", sqlCaptor.getAllValues()).toLowerCase();
        assertThat(combinedSql).contains("performance_records");
        assertThat(combinedSql).contains("default_recruiter_user_id");
        assertThat(combinedSql).contains("partner_id");
        assertThat(combinedSql).contains("effective_service_fee");
        assertThat(combinedSql).contains("settle_time");
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
        when(repository.findByMerchantIdAndMonth(any(), any())).thenReturn(Optional.empty());

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        ArgumentCaptor<ExclusiveMerchant> captor = ArgumentCaptor.forClass(ExclusiveMerchant.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo(merchantId);
        assertThat(captor.getValue().getDeptId()).isEqualTo(deptId);
        verify(repository, never()).update(any());
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
        when(repository.findByMerchantIdAndMonth(any(), any())).thenReturn(Optional.of(existing));

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        verify(repository).update(any(ExclusiveMerchant.class));
        verify(repository, never()).save(any());
    }

    private void stubConfig(String value) {
        when(configDomainFacade.getExclusiveRules())
                .thenReturn(new ExclusiveRulesDTO(new java.math.BigDecimal(value)));
    }

    private void stubUserTotalFeeEmpty(LocalDateTime start, LocalDateTime end) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(start), eq(end)))
                .thenReturn(List.of());
    }

    private void stubMerchantUserFeeEmpty(LocalDateTime start, LocalDateTime end) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(start), eq(end)))
                .thenReturn(List.of());
    }

    private void stubUserTotalFeeWithEntry(LocalDateTime start, LocalDateTime end,
                                            UUID userId, long totalFee) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("user_id")).thenReturn(userId.toString());
        when(rs.getLong("total_fee")).thenReturn(totalFee);

        when(jdbcTemplate.query(contains("GROUP BY default_recruiter_user_id"), any(RowMapper.class), eq(start), eq(end)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
    }

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

        when(jdbcTemplate.query(contains("partner_id"), any(RowMapper.class), eq(start), eq(end)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(userDomainFacade.loadUserOwnershipReferencesByIds(any()))
                .thenReturn(Map.of(userId, new UserOwnershipReference(userId, deptId)));
    }
}
