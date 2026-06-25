package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.talent.domain.ExclusiveTalentRepository;
import com.colonel.saas.domain.talent.event.ExclusiveTalentActivatedEvent;
import com.colonel.saas.domain.talent.event.ExclusiveTalentDomainEventPublisher;
import com.colonel.saas.domain.talent.event.ExclusiveTalentExpiredEvent;
import com.colonel.saas.entity.ExclusiveTalent;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.TalentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusiveTalentApplicationServiceSmokeTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ExclusiveTalentRepository repository;
    @Mock private TalentMapper talentMapper;
    @Mock private ConfigDomainFacade configDomainFacade;
    @Mock private ExclusiveTalentDomainEventPublisher eventPublisher;

    private ExclusiveTalentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveTalentApplicationService(
                jdbcTemplate,
                repository,
                talentMapper,
                configDomainFacade,
                eventPublisher);
        when(configDomainFacade.getExclusiveTalentFeeRatio()).thenReturn(new BigDecimal("70"));
        when(configDomainFacade.getExclusiveTalentMonthlySamples()).thenReturn(10);
        when(repository.listByEffectiveMonth(anyString())).thenReturn(List.of());
    }

    @Test
    void evaluateMonth_shouldSaveAndPublishActivatedWhenThresholdsMet() throws Exception {
        String talentUid = "talent-activated";
        UUID channelUserId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        when(talentMapper.selectOne(any())).thenReturn(talent);
        stubTotalFee(talentUid, 1000L);
        stubChannelFee(talentUid, channelUserId, deptId, 800L);
        stubSampleCount(channelUserId, talentUid, 10);
        when(repository.findByTalentUidAndMonth(talentUid, "2026-06")).thenReturn(Optional.empty());

        int result = service.evaluateMonth(YearMonth.of(2026, 5), YearMonth.of(2026, 6));

        assertThat(result).isEqualTo(1);
        verify(repository).save(any(ExclusiveTalent.class));
        verify(eventPublisher).publishActivated(any(ExclusiveTalentActivatedEvent.class));
        verify(eventPublisher, never()).publishExpired(any(ExclusiveTalentExpiredEvent.class));
    }

    @Test
    void evaluateMonth_shouldExpireExistingRecordWhenNotActivated() {
        doNothing().when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(), any());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        ExclusiveTalent existing = new ExclusiveTalent();
        existing.setTalentId(UUID.randomUUID());
        existing.setTalentUid("talent-expired");
        existing.setEffectiveMonth("2026-06");
        existing.setStatus(1);
        when(repository.listByEffectiveMonth("2026-06")).thenReturn(List.of(existing));

        int result = service.evaluateMonth(YearMonth.of(2026, 5), YearMonth.of(2026, 6));

        assertThat(result).isZero();
        assertThat(existing.getStatus()).isZero();
        verify(repository).update(existing);
        verify(eventPublisher).publishExpired(any(ExclusiveTalentExpiredEvent.class));
    }

    private void stubTotalFee(String talentUid, long totalFee) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getLong("total_fee")).thenReturn(totalFee);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("total_fee"), any(RowCallbackHandler.class), any(), any());
    }

    private void stubChannelFee(String talentUid, UUID channelUserId, UUID deptId, long channelFee) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getString("channel_user_id")).thenReturn(channelUserId.toString());
        when(rs.getString("dept_id")).thenReturn(deptId.toString());
        when(rs.getLong("channel_fee")).thenReturn(channelFee);
        when(jdbcTemplate.query(contains("channel_fee"), any(RowMapper.class), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });
    }

    private void stubSampleCount(UUID channelUserId, String talentUid, int sampleCount) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("channel_user_id")).thenReturn(channelUserId.toString());
        when(rs.getString("talent_uid")).thenReturn(talentUid);
        when(rs.getInt("sample_count")).thenReturn(sampleCount);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(contains("sample_count"), any(RowCallbackHandler.class), any(), any());
    }
}
