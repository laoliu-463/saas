package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionApiTest {

    @Mock
    private DouyinApiClient douyinApiClient;

    @Mock
    private DouyinUpstreamModeSupport upstreamModeSupport;

    @Mock
    private DouyinContractFixtureProvider contractFixtureProvider;

    @InjectMocks
    private InstitutionApi institutionApi;

    @BeforeEach
    void setUp() {
        lenient().when(upstreamModeSupport.isContract()).thenReturn(false);
    }

    @Test
    void info_shouldUseContractFixtureWhenContractModeEnabled() {
        Map<String, Object> fixture = Map.of("data", Map.of("institution", "contract"));
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildInstitutionInfoResponse("app-1")).thenReturn(fixture);

        Map<String, Object> result = institutionApi.info("app-1");

        assertThat(result).isSameAs(fixture);
        verify(contractFixtureProvider).buildInstitutionInfoResponse("app-1");
        verify(douyinApiClient, never()).post(eq("buyin.institutionInfo"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void info_shouldTrimAppIdBeforeCallingRealEndpoint() {
        when(douyinApiClient.post(eq("buyin.institutionInfo"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of("data", Map.of("institution", "real")));

        Map<String, Object> result = institutionApi.info(" app-2 ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.institutionInfo"), captor.capture());
        assertThat(captor.getValue()).containsEntry("appId", "app-2");
        assertThat(result).containsKey("data");
    }

    @Test
    void info_shouldOmitBlankAppId() {
        when(douyinApiClient.post(eq("buyin.institutionInfo"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(Map.of());

        institutionApi.info(" ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(douyinApiClient).post(eq("buyin.institutionInfo"), captor.capture());
        assertThat(captor.getValue()).doesNotContainKey("appId");
    }
}
