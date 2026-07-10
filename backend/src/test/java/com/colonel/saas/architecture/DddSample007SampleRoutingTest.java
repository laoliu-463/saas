package com.colonel.saas.architecture;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.application.SampleQueryApplicationService;
import com.colonel.saas.domain.sample.application.port.SampleDetailQueryPort;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleVO;
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

@ExtendWith(MockitoExtension.class)
class DddSample007SampleRoutingTest {

    @Mock private SampleQueryService sampleQueryService;
    @Mock private SampleDetailQueryPort sampleDetailQueryPort;
    @Mock private SampleDomainFacade sampleDomainFacade;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch sampleApplicationSwitch;

    @InjectMocks
    private SampleQueryApplicationService sampleQueryApplicationService;

    private final UUID sampleId = UUID.randomUUID();

    @Test
    @DisplayName("开关关闭时 getSampleById 直接委派旧查询服务")
    void getSampleById_shouldDelegateLegacyWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        SampleVO expected = new SampleVO();
        when(sampleDetailQueryPort.getSampleById(eq(sampleId), any(), any(), any(), any())).thenReturn(expected);

        SampleVO actual = sampleQueryApplicationService.getSampleById(
                sampleId, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL, null);

        assertThat(actual).isSameAs(expected);
        verify(sampleDomainFacade, never()).existsById(any());
    }

    @Test
    @DisplayName("开关开启且 Facade 不存在时 getSampleById 抛 NOT_FOUND")
    void getSampleById_shouldRejectMissingSampleWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getSampleApplication()).thenReturn(sampleApplicationSwitch);
        when(sampleApplicationSwitch.isEnabled()).thenReturn(true);
        when(sampleDomainFacade.existsById(sampleId)).thenReturn(false);

        assertThatThrownBy(() -> sampleQueryApplicationService.getSampleById(
                sampleId, UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sample request not found");

        verify(sampleDetailQueryPort, never()).getSampleById(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启且 Facade 存在时 getSampleById 继续委派旧查询服务")
    void getSampleById_shouldDelegateAfterFacadeCheckWhenRoutingEnabled() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getSampleApplication()).thenReturn(sampleApplicationSwitch);
        when(sampleApplicationSwitch.isEnabled()).thenReturn(true);
        when(sampleDomainFacade.existsById(sampleId)).thenReturn(true);
        SampleVO expected = new SampleVO();
        when(sampleDetailQueryPort.getSampleById(eq(sampleId), any(), any(), any(), any())).thenReturn(expected);

        SampleVO actual = sampleQueryApplicationService.getSampleById(
                sampleId, UUID.randomUUID(), UUID.randomUUID(), DataScope.PERSONAL, null);

        assertThat(actual).isSameAs(expected);
        verify(sampleDomainFacade).existsById(sampleId);
        verify(sampleDetailQueryPort).getSampleById(eq(sampleId), any(), any(), any(), any());
    }
}
