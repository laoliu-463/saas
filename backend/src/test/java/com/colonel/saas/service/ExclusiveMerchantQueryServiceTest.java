package com.colonel.saas.service;

import com.colonel.saas.dto.performance.ExclusiveMerchantDetailDTO;
import com.colonel.saas.entity.ExclusiveMerchant;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ExclusiveMerchantMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusiveMerchantQueryServiceTest {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    @Mock
    private ExclusiveMerchantMapper exclusiveMerchantMapper;
    @Mock
    private UserDomainFacade userDomainFacade;

    private ExclusiveMerchantQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExclusiveMerchantQueryService(exclusiveMerchantMapper, userDomainFacade);
    }

    @Test
    void getByPartnerId_shouldReturnNonExclusiveWhenPartnerIdBlank() {
        ExclusiveMerchantDetailDTO dto = service.getByPartnerId(" ");

        assertThat(dto.getPartnerId()).isEqualTo(" ");
        assertThat(dto.isExclusive()).isFalse();
        verify(exclusiveMerchantMapper, never()).selectOne(any());
        verify(userDomainFacade, never()).selectById(any());
    }

    @Test
    void getByPartnerId_shouldReturnNonExclusiveWhenNoMatch() {
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(null);

        ExclusiveMerchantDetailDTO dto = service.getByPartnerId(" P100 ");

        assertThat(dto.getPartnerId()).isEqualTo(" P100 ");
        assertThat(dto.isExclusive()).isFalse();
        assertThat(dto.getRecruiterName()).isNull();
        verify(userDomainFacade, never()).selectById(any());
    }

    @Test
    void getByPartnerId_shouldMapActiveMerchantAndRecruiterName() {
        UUID recruiterId = UUID.randomUUID();
        ExclusiveMerchant merchant = merchant("P100", "品牌旗舰店", recruiterId, currentMonth(), 1);
        SysUser recruiter = new SysUser();
        recruiter.setId(recruiterId);
        recruiter.setUsername("biz-user");
        when(exclusiveMerchantMapper.selectOne(any())).thenReturn(merchant);
        when(userDomainFacade.getUserById(recruiterId)).thenReturn(recruiter == null ? null : new com.colonel.saas.dto.user.UserOptionResponse(recruiter.getId(), recruiter.getUsername(), recruiter.getRealName(), recruiter.getDeptId(), null));

        ExclusiveMerchantDetailDTO dto = service.getByPartnerId("P100");

        assertThat(dto.isExclusive()).isTrue();
        assertThat(dto.getPartnerId()).isEqualTo("P100");
        assertThat(dto.getPartnerName()).isEqualTo("品牌旗舰店");
        assertThat(dto.getRecruiterId()).isEqualTo(recruiterId.toString());
        assertThat(dto.getRecruiterName()).isEqualTo("biz-user");
        assertThat(dto.getEffectiveMonth()).isEqualTo(currentMonth());
        assertThat(dto.getExpireMonth()).isEqualTo(YearMonth.now().plusMonths(1).format(MONTH));
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listMyExclusiveMerchants_shouldReturnEmptyWhenRecruiterMissing() {
        List<ExclusiveMerchantDetailDTO> result = service.listMyExclusiveMerchants(null);

        assertThat(result).isEmpty();
        verify(exclusiveMerchantMapper, never()).selectList(any());
    }

    @Test
    void listMyExclusiveMerchants_shouldMapRowsAndTolerateMissingUserAndBadMonth() {
        UUID recruiterId = UUID.randomUUID();
        ExclusiveMerchant active = merchant("P100", "A 店", recruiterId, currentMonth(), 1);
        ExclusiveMerchant inactive = merchant("P200", "B 店", null, "bad-month", 0);
        when(exclusiveMerchantMapper.selectList(any())).thenReturn(List.of(active, inactive));
        when(userDomainFacade.getUserById(recruiterId)).thenReturn(null == null ? null : new com.colonel.saas.dto.user.UserOptionResponse(null.getId(), null.getUsername(), null.getRealName(), null.getDeptId(), null));

        List<ExclusiveMerchantDetailDTO> result = service.listMyExclusiveMerchants(recruiterId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPartnerId()).isEqualTo("P100");
        assertThat(result.get(0).getRecruiterId()).isEqualTo(recruiterId.toString());
        assertThat(result.get(0).getRecruiterName()).isNull();
        assertThat(result.get(0).getExpireMonth()).isEqualTo(YearMonth.now().plusMonths(1).format(MONTH));
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(result.get(1).getPartnerId()).isEqualTo("P200");
        assertThat(result.get(1).getRecruiterId()).isNull();
        assertThat(result.get(1).getExpireMonth()).isNull();
        assertThat(result.get(1).getStatus()).isEqualTo("INACTIVE");
    }

    private static String currentMonth() {
        return YearMonth.now().format(MONTH);
    }

    private static ExclusiveMerchant merchant(
            String merchantId,
            String merchantName,
            UUID userId,
            String effectiveMonth,
            int status) {
        ExclusiveMerchant merchant = new ExclusiveMerchant();
        merchant.setId(UUID.randomUUID());
        merchant.setMerchantId(merchantId);
        merchant.setMerchantName(merchantName);
        merchant.setUserId(userId);
        merchant.setEffectiveMonth(effectiveMonth);
        merchant.setStatus(status);
        merchant.setDeleted(0);
        return merchant;
    }
}
