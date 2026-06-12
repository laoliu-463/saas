package com.colonel.saas.architecture;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.talent.application.TalentQueryApplicationService;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.service.TalentQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DDD-TALENT-003：TalentController 读路径经应用层，开关开启时先走 TalentDomainFacade。
 */
@ExtendWith(MockitoExtension.class)
class DddTalent003TalentRoutingTest {

    @Mock private TalentQueryService talentQueryService;
    @Mock private TalentDomainFacade talentDomainFacade;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch talentFacadeSwitch;

    @InjectMocks
    private TalentQueryApplicationService talentQueryApplicationService;

    private final UUID talentId = UUID.randomUUID();

    @Test
    @DisplayName("开关关闭时 detail 直接委派 TalentQueryService")
    void detail_shouldDelegateLegacyWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        TalentDetailResponse expected = new TalentDetailResponse();
        when(talentQueryService.detail(eq(talentId), any(), any(), any())).thenReturn(expected);

        TalentDetailResponse actual = talentQueryApplicationService.detail(
                talentId, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL);

        assertThat(actual).isSameAs(expected);
        verify(talentDomainFacade, never()).existsById(any());
    }

    @Test
    @DisplayName("开关开启且 Facade 不存在时 detail 抛 NOT_FOUND")
    void detail_shouldRejectMissingTalentWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getTalentFacade()).thenReturn(talentFacadeSwitch);
        when(talentFacadeSwitch.isEnabled()).thenReturn(true);
        when(talentDomainFacade.existsById(talentId)).thenReturn(false);

        assertThatThrownBy(() -> talentQueryApplicationService.detail(
                talentId, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("达人不存在");

        verify(talentQueryService, never()).detail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启且 Facade 存在时 detail 继续委派旧查询服务")
    void detail_shouldDelegateAfterFacadeCheckWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getTalentFacade()).thenReturn(talentFacadeSwitch);
        when(talentFacadeSwitch.isEnabled()).thenReturn(true);
        when(talentDomainFacade.existsById(talentId)).thenReturn(true);
        TalentDetailResponse expected = new TalentDetailResponse();
        when(talentQueryService.detail(eq(talentId), any(), any(), any())).thenReturn(expected);

        TalentDetailResponse actual = talentQueryApplicationService.detail(
                talentId, UUID.randomUUID(), UUID.randomUUID(), DataScope.PERSONAL);

        assertThat(actual).isSameAs(expected);
        verify(talentDomainFacade).existsById(talentId);
    }
}
