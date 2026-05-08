package com.colonel.saas.service;

import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ExclusiveTalentMapper;
import com.colonel.saas.mapper.TalentMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class ExclusiveTalentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ExclusiveTalentMapper exclusiveTalentMapper;
    @Mock
    private TalentMapper talentMapper;

    private ExclusiveTalentService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveTalentService(jdbcTemplate, exclusiveTalentMapper, talentMapper);
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        when(talentMapper.selectOne(any())).thenReturn(talent);
    }

    private static final String KEY_RATIO = "talent.exclusive.service_fee_ratio";
    private static final String KEY_SAMPLES = "talent.exclusive.monthly_samples";

    private void stubConfig(String ratioValue, String samplesValue) {
        doReturn(ratioValue).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_RATIO));
        doReturn(samplesValue).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_SAMPLES));
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnOwner() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ExclusiveTalent record = new ExclusiveTalent();
        record.setUserId(userId);
        record.setDeptId(deptId);
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(record);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-1");

        assertThat(owner).isNotNull();
        assertThat(owner.userId()).isEqualTo(userId);
        assertThat(owner.deptId()).isEqualTo(deptId);
    }

    @Test
    void findActiveOwnerByTalentUid_shouldReturnNullWhenNotFound() {
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(null);

        AttributionService.ExclusiveOwner owner = service.findActiveOwnerByTalentUid("talent-x");

        assertThat(owner).isNull();
    }

    // ─── evaluateMonth tests ────────────────────────────────────────────────

    @Test
    void evaluateMonth_shouldReturnZeroWhenChannelRowsEmpty() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        stubConfig("70", "10");
        // All data queries return empty
        stubDataQuery(start, end);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
        verify(exclusiveTalentMapper, never()).insert(any());
        verify(exclusiveTalentMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldInsertWhenBothThresholdsMet() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String talentUid = "talent-upsert";
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70", "10");
        stubDataQuery(start, end);
        stubTotalFeeWithEntry(start, end, talentUid, 1000L);
        stubChannelFeeWithEntry(start, end, talentUid, channelUserId, deptId, 800L);
        stubSampleCountWithEntry(start, end, channelUserId, talentUid, 10);
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(null);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        ArgumentCaptor<ExclusiveTalent> captor = ArgumentCaptor.forClass(ExclusiveTalent.class);
        verify(exclusiveTalentMapper).insert(captor.capture());
        assertThat(captor.getValue().getTalentUid()).isEqualTo(talentUid);
        verify(exclusiveTalentMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldUpdateWhenRecordExists() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String talentUid = "talent-existing";
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70", "10");
        stubDataQuery(start, end);
        stubTotalFeeWithEntry(start, end, talentUid, 1000L);
        stubChannelFeeWithEntry(start, end, talentUid, channelUserId, deptId, 800L);
        stubSampleCountWithEntry(start, end, channelUserId, talentUid, 10);
        ExclusiveTalent existing = new ExclusiveTalent();
        when(exclusiveTalentMapper.selectOne(any())).thenReturn(existing);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isEqualTo(1);
        verify(exclusiveTalentMapper).updateById(any(ExclusiveTalent.class));
        verify(exclusiveTalentMapper, never()).insert(any());
    }

    @Test
    void evaluateMonth_shouldSkipRowWhenRatioBelowThreshold() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String talentUid = "talent-low";
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70", "10");
        stubDataQuery(start, end);
        stubTotalFeeWithEntry(start, end, talentUid, 1000L);
        // Channel fee 100 / total fee 1000 = 10% < 70% threshold
        stubChannelFeeWithEntry(start, end, talentUid, channelUserId, deptId, 100L);
        stubSampleCountWithEntry(start, end, channelUserId, talentUid, 10);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
        verify(exclusiveTalentMapper, never()).insert(any());
        verify(exclusiveTalentMapper, never()).updateById(any());
    }

    @Test
    void evaluateMonth_shouldSkipRowWhenSampleBelowThreshold() throws SQLException {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();
        String talentUid = "talent-few-samples";
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        stubConfig("70", "10");
        stubDataQuery(start, end);
        stubTotalFeeWithEntry(start, end, talentUid, 1000L);
        // Channel fee 800/1000 = 80% >= 70% ✓
        stubChannelFeeWithEntry(start, end, talentUid, channelUserId, deptId, 800L);
        // Sample count 5 < 10 threshold ✗
        stubSampleCountWithEntry(start, end, channelUserId, talentUid, 5);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
        verify(exclusiveTalentMapper, never()).insert(any());
    }

    @Test
    void evaluateMonth_shouldUseDefaultThresholdsOnConfigError() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        // Config throws → defaults used (ratio=70, samples=10)
        doThrow(new RuntimeException("db error")).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_RATIO));
        doThrow(new RuntimeException("db error")).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), eq(KEY_SAMPLES));
        stubDataQuery(start, end);

        int result = service.evaluateMonth(stats, apply);

        assertThat(result).isZero();
    }

    @Test
    void evaluateMonth_shouldQueryOrdersBySettleTimeAndSamplesByShipTime() {
        YearMonth stats = YearMonth.of(2024, 6);
        YearMonth apply = YearMonth.of(2024, 7);
        LocalDateTime start = stats.atDay(1).atStartOfDay();
        LocalDateTime end = stats.plusMonths(1).atDay(1).atStartOfDay();

        stubConfig("70", "10");
        stubDataQuery(start, end);

        service.evaluateMonth(stats, apply);

        verify(jdbcTemplate).query(contains("settle_time >= ?"), any(RowCallbackHandler.class), any(Object[].class));
        verify(jdbcTemplate).query(contains("settle_time >= ?"), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate).query(contains("ship_time >= ?"), any(RowCallbackHandler.class), any(Object[].class));
    }

    // ─── Helper stubs ──────────────────────────────────────────────────────

    // Catch-all: stub ALL query overloads to return empty (no rows).
    // Must exclude the three unique column names that the specific stubs target.
    // Registered FIRST so the more-specific stubs (registered later) take precedence.
    // Key insight: use unique SELECT-column names as identifiers:
    //   - total_fee  → identifies the total service fee query (RowCallbackHandler)
    //   - channel_fee → identifies the channel fee query (RowMapper)
    //   - sample_count → identifies the effective sample count query (RowCallbackHandler)
    // "talent_uid" and "channel_user_id" appear in WHERE clauses of ALL three queries,
    // so using them as exclusion predicates in the catch-all WOULD incorrectly block
    // specific stubs. Use the distinct SELECT-column names instead.
    private void stubDataQuery(LocalDateTime start, LocalDateTime end) {
        // RowCallbackHandler overload — must use doNothing() + any(Object[].class)
        doNothing().when(jdbcTemplate)
                .query(argThat(sql -> sql != null
                        && !sql.contains("total_fee")
                        && !sql.contains("channel_fee")
                        && !sql.contains("sample_count")), any(RowCallbackHandler.class), any(Object[].class));
        // RowMapper overload — must use when().thenReturn() + any(Object[].class)
        when(jdbcTemplate.query(argThat(sql -> sql != null
                        && !sql.contains("total_fee")
                        && !sql.contains("channel_fee")
                        && !sql.contains("sample_count")), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
    }

    private void stubTotalFeeWithEntry(LocalDateTime start, LocalDateTime end,
                                        String talentUid, long totalFee) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getLong("total_fee")).thenReturn(totalFee);

        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate)
                .query(contains("total_fee"), any(RowCallbackHandler.class), any(Object[].class));
    }

    private void stubChannelFeeWithEntry(LocalDateTime start, LocalDateTime end,
                                          String talentUid, UUID channelUserId,
                                          UUID deptId, long channelFee) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getString("channel_user_id")).thenReturn(channelUserId.toString());
        when(rs.getString("dept_id")).thenReturn(deptId.toString());
        when(rs.getLong("channel_fee")).thenReturn(channelFee);

        when(jdbcTemplate.query(contains("channel_fee"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 1));
                });
    }

    private void stubSampleCountWithEntry(LocalDateTime start, LocalDateTime end,
                                          UUID channelUserId, String talentUid,
                                          int sampleCount) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getString("channel_user_id")).thenReturn(channelUserId.toString());
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getInt("sample_count")).thenReturn(sampleCount);

        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate)
                .query(contains("sample_count"), any(RowCallbackHandler.class), any(Object[].class));
    }
}
