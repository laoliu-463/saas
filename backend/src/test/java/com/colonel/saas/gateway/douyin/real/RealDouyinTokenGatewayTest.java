package com.colonel.saas.gateway.douyin.real;

import com.colonel.saas.douyin.DoudianTokenGateway;
import com.colonel.saas.douyin.DouyinConfig;
import com.colonel.saas.douyin.api.InstitutionApi;
import com.colonel.saas.gateway.douyin.DouyinTokenGateway;
import com.colonel.saas.gateway.douyin.contract.DouyinContractFixtureProvider;
import com.colonel.saas.gateway.douyin.contract.DouyinUpstreamModeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealDouyinTokenGatewayTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private DouyinConfig douyinConfig;
    @Mock
    private DoudianTokenGateway doudianTokenGateway;
    @Mock
    private InstitutionApi institutionApi;
    @Mock
    private DouyinUpstreamModeSupport upstreamModeSupport;
    @Mock
    private DouyinContractFixtureProvider contractFixtureProvider;

    private RealDouyinTokenGateway gateway;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        gateway = new RealDouyinTokenGateway(
                redisTemplate,
                douyinConfig,
                doudianTokenGateway,
                institutionApi,
                upstreamModeSupport,
                contractFixtureProvider
        );
    }

    @Test
    void ensureToken_shouldReuseCachedRedisToken() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(String.valueOf(expireAt));

        DouyinTokenGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNotNull();
        assertThat(payload.accessToken()).isEqualTo("cached-access");
        assertThat(payload.refreshToken()).isEqualTo("cached-refresh");
        assertThat(payload.expiresIn()).isBetween(1L, 600L);
    }

    @Test
    void ensureToken_shouldFallbackToConfiguredClientKey() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(douyinConfig.getClientKey()).thenReturn("client-key-1");
        when(valueOperations.get("douyin:token:client-key-1")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:client-key-1")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:client-key-1")).thenReturn(expireAt);

        DouyinTokenGateway.TokenPayload payload = gateway.ensureToken(null);

        assertThat(payload).isNotNull();
        assertThat(payload.accessToken()).isEqualTo("cached-access");
    }

    @Test
    void ensureToken_shouldReturnNullWhenTokenExpired() {
        long expireAt = Instant.now().getEpochSecond() - 5;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(expireAt);

        DouyinTokenGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNull();
    }

    @Test
    void ensureToken_shouldReturnNullWhenRefreshTokenMissing() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(valueOperations.get("douyin:token:app123")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:app123")).thenReturn(" ");
        when(valueOperations.get("douyin:token:expire_at:app123")).thenReturn(expireAt);

        DouyinTokenGateway.TokenPayload payload = gateway.ensureToken("app123");

        assertThat(payload).isNull();
    }

    @Test
    void ensureToken_shouldFallbackToConfiguredAppIdAndContractAppKey() {
        long expireAt = Instant.now().getEpochSecond() + 600;
        when(douyinConfig.getClientKey()).thenReturn(" ");
        when(douyinConfig.getAppId()).thenReturn(" configured-app ");
        when(valueOperations.get("douyin:token:configured-app")).thenReturn("cached-access");
        when(valueOperations.get("douyin:refresh:configured-app")).thenReturn("cached-refresh");
        when(valueOperations.get("douyin:token:expire_at:configured-app")).thenReturn(expireAt);

        DouyinTokenGateway.TokenPayload payload = gateway.ensureToken(null);

        assertThat(payload).isNotNull();
        assertThat(payload.accessToken()).isEqualTo("cached-access");

        when(douyinConfig.getAppId()).thenReturn(" ");
        when(contractFixtureProvider.appKey()).thenReturn("contract-app");
        when(valueOperations.get("douyin:token:contract-app")).thenReturn(null);
        assertThat(gateway.ensureToken(null)).isNull();
    }

    @Test
    void refreshCreateInstitutionAndProbeUseContractFixturesWhenContractModeEnabled() {
        when(upstreamModeSupport.isContract()).thenReturn(true);
        when(contractFixtureProvider.buildTokenPayload("app123", "refresh-1"))
                .thenReturn(new DouyinTokenGateway.TokenPayload(
                        "contract-access",
                        "refresh-1",
                        7200L,
                        "auth-1",
                        "institution",
                        1L
                ));
        when(contractFixtureProvider.buildTokenPayload(null, null))
                .thenReturn(new DouyinTokenGateway.TokenPayload(
                        "contract-created",
                        "contract-refresh",
                        7200L,
                        "auth-2",
                        "institution",
                        1L
                ));
        when(contractFixtureProvider.buildInstitutionInfoResponse("app123"))
                .thenReturn(Map.of("code", 10000, "data", Map.of("app_key", "app123")));
        when(contractFixtureProvider.authId()).thenReturn("auth-3");

        DouyinTokenGateway.TokenPayload refreshed = gateway.refreshToken("app123", "refresh-1");
        DouyinTokenGateway.TokenPayload created = gateway.createToken(
                new DouyinTokenGateway.TokenCreateCommand("code", "authorization_code", "shop", "S1", "AUTH", "Colonel")
        );
        Map<String, Object> info = gateway.institutionInfo("app123");
        DouyinTokenGateway.ProbeTokenCreateResult probe = gateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand(" ", "authorization_code", "shop", "S1", " ", "Colonel")
        );

        assertThat(refreshed.accessToken()).isEqualTo("contract-access");
        assertThat(created.accessToken()).isEqualTo("contract-created");
        assertThat(info).containsEntry("code", 10000);
        assertThat(probe.codeState()).isEqualTo("absent");
        assertThat(probe.authIdPresent()).isFalse();
        assertThat(probe.response().authorityId()).isEqualTo("auth-3");
    }

    @Test
    void refreshCreateInstitutionAndProbeDelegateToLiveGatewaysWhenLiveModeEnabled() {
        when(upstreamModeSupport.isContract()).thenReturn(false);
        when(doudianTokenGateway.refreshToken("refresh-1"))
                .thenReturn(new DoudianTokenGateway.TokenPayload(
                        "live-access",
                        "live-refresh",
                        3600L,
                        "auth-live",
                        "merchant",
                        2L
                ));
        when(doudianTokenGateway.createToken(new DoudianTokenGateway.TokenCreateCommand(
                "code",
                "authorization_code",
                "shop",
                "S1",
                "AUTH",
                "merchant"
        ))).thenReturn(new DoudianTokenGateway.TokenPayload(
                "created-access",
                "created-refresh",
                1800L,
                "auth-created",
                "merchant",
                1L
        ));
        when(institutionApi.info("app123"))
                .thenReturn(Map.of("code", 10000, "data", Map.of("app_key", "app123")));
        DoudianTokenGateway.TokenCreateResponseView responseView =
                new DoudianTokenGateway.TokenCreateResponseView(
                        "10000",
                        "success",
                        null,
                        null,
                        "****",
                        "****",
                        7200L,
                        "auth-probe",
                        "merchant",
                        1L
                );
        when(doudianTokenGateway.probeCreateToken(new DoudianTokenGateway.TokenCreateCommand(
                "code",
                "authorization_code",
                "shop",
                "S1",
                "AUTH",
                "merchant"
        ))).thenReturn(new DoudianTokenGateway.TokenCreateProbeResult(
                "authorization_code",
                "present",
                "shop",
                "S1",
                true,
                "merchant",
                null,
                responseView
        ));

        DouyinTokenGateway.TokenPayload refreshed = gateway.refreshToken("app123", "refresh-1");
        DouyinTokenGateway.TokenPayload created = gateway.createToken(
                new DouyinTokenGateway.TokenCreateCommand("code", "authorization_code", "shop", "S1", "AUTH", "merchant")
        );
        Map<String, Object> info = gateway.institutionInfo("app123");
        DouyinTokenGateway.ProbeTokenCreateResult probe = gateway.probeCreateToken(
                new DouyinTokenGateway.TokenCreateCommand("code", "authorization_code", "shop", "S1", "AUTH", "merchant")
        );

        assertThat(refreshed.accessToken()).isEqualTo("live-access");
        assertThat(refreshed.tokenType()).isEqualTo(2L);
        assertThat(created.accessToken()).isEqualTo("created-access");
        assertThat(created.authorityId()).isEqualTo("auth-created");
        assertThat(info).containsEntry("code", 10000);
        assertThat(probe.codeState()).isEqualTo("present");
        assertThat(probe.authIdPresent()).isTrue();
        assertThat(probe.response().authorityId()).isEqualTo("auth-probe");
    }

    @Test
    void constructor_shouldLazyInstitutionApiDependencyToAvoidRealProfileCycle() throws NoSuchMethodException {
        Constructor<RealDouyinTokenGateway> constructor = RealDouyinTokenGateway.class.getConstructor(
                RedisTemplate.class,
                DouyinConfig.class,
                DoudianTokenGateway.class,
                InstitutionApi.class,
                DouyinUpstreamModeSupport.class,
                DouyinContractFixtureProvider.class
        );

        assertThat(constructor.getParameters()[3].isAnnotationPresent(Lazy.class)).isTrue();
    }
}
