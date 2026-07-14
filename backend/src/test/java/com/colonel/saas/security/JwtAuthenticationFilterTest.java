package com.colonel.saas.security;

import com.colonel.saas.auth.service.AuthService;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.AuthorizationRuntimeProperties;
import com.colonel.saas.domain.user.api.AuthorizationPrincipal;
import com.colonel.saas.domain.user.api.AuthorizationTokenRejectedException;
import com.colonel.saas.domain.user.api.AuthorizationUnavailableException;
import com.colonel.saas.domain.user.facade.AuthorizationPrincipalFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthService authService;

    @Mock
    private AuthorizationPrincipalFacade principalFacade;

    @Mock
    private AuthorizationRuntimeProperties runtimeProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = newFilter(new MockEnvironment());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productionConstructor_shouldBeTheOnlyConstructorAndExplicitlyAutowired() {
        Constructor<?>[] constructors = JwtAuthenticationFilter.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getAnnotation(Autowired.class)).isNotNull();
        assertThat(constructors[0].getParameterTypes()).containsExactly(
                JwtTokenProvider.class,
                AuthService.class,
                AuthorizationPrincipalFacade.class,
                AuthorizationRuntimeProperties.class,
                ObjectMapper.class,
                Environment.class);
    }

    @Test
    void publicLoginPath_shouldBypassAuthorizationCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void publicLogoutPath_shouldBypassAuthorizationCheckEvenWhenAccessTokenExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/logout");
        request.addHeader("Authorization", "Bearer expired.access.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void publicApiLogoutPath_shouldBypassAuthorizationCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void systemHealthPath_shouldBypassAuthorizationCheck() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void systemEnvPath_shouldRequireAuthorizationWhenProtectedProfileActive() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("real-pre");
        JwtAuthenticationFilter prodFilter = newFilter(environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        prodFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void systemEnvPath_shouldStayPublicOutsideProdProfile() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void testToolPath_shouldRequireAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void actuatorPath_shouldRequireAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void blacklistedToken_shouldRejectBeforeResolvingAuthorizationPrincipal() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(jwtTokenProvider.parseClaims("blacklisted.token")).thenReturn(claims);
        when(jwtTokenProvider.getTokenHash("blacklisted.token")).thenReturn("blacklisted-hash");
        when(authService.isTokenBlacklisted("blacklisted-hash")).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("blacklisted.token", "/products", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertShortCircuited(chain);
        verify(claims, never()).getSubject();
        verifyNoInteractions(principalFacade);
    }

    @Test
    void nonLegacyMode_shouldRejectTokenWithoutAuthorizationVersionBeforeFacade() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, null);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("missing.version", claims);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("missing.version", "/products", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertShortCircuited(chain);
        verifyNoInteractions(principalFacade);
    }

    @Test
    void nonLegacyMode_shouldRejectNonPositiveAuthorizationVersionBeforeFacade() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 0L);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("invalid.version", claims);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("invalid.version", "/products", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertShortCircuited(chain);
        verifyNoInteractions(principalFacade);
    }

    @Test
    void authorizationVersionClaimTypeError_shouldReturnGeneric401BeforeFacade() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L);
        when(claims.get("authzVersion", Long.class))
                .thenThrow(new ClassCastException("authzVersion is not a Long"));
        stubAcceptedToken("wrong.type", claims);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("wrong.type", "/products", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(jsonBody(response).path("msg").asText()).isEqualTo("Token 无效或已过期");
        assertShortCircuited(chain);
        verifyNoInteractions(principalFacade);
    }

    @Test
    void nonLegacyMode_shouldReturn401WhenFacadeRejectsToken() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("stale.version", claims);
        when(principalFacade.requireCurrent(userId, 4L))
                .thenThrow(new AuthorizationTokenRejectedException());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("stale.version", "/products", chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(jsonBody(response).path("msg").asText())
                .isEqualTo("授权令牌已失效，请重新登录");
        assertShortCircuited(chain);
        verify(principalFacade).requireCurrent(userId, 4L);
    }

    @Test
    void nonLegacyMode_shouldReturnStructured503WhenPrincipalStoreIsUnavailable() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("store.down", claims);
        when(principalFacade.requireCurrent(userId, 4L))
                .thenThrow(new AuthorizationUnavailableException());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = filter("store.down", "/products", chain);

        JsonNode body = jsonBody(response);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.path("code").asInt()).isEqualTo(503);
        assertThat(body.path("msg").asText()).isEqualTo("授权事实暂时不可用");
        assertThat(body.path("errorCode").asText()).isEqualTo("AUTHORIZATION_UNAVAILABLE");
        assertShortCircuited(chain);
    }

    @Test
    void nonLegacyMode_shouldUseDatabasePrincipalAndPreserveLegacyTokenAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenDept = UUID.randomUUID();
        UUID databaseDept = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L, tokenDept, "token-alice", false);
        AuthorizationPrincipal principal =
                new AuthorizationPrincipal(userId, databaseDept, "database-alice", 4L, false);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("valid.version", claims);
        when(principalFacade.requireCurrent(userId, 4L)).thenReturn(principal);

        MockHttpServletRequest request = authenticatedRequest("valid.version", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            Authentication authenticationAtChain =
                    SecurityContextHolder.getContext().getAuthentication();
            assertThat((Object) invocation.getArgument(0)).isSameAs(request);
            assertThat(authenticationAtChain.getPrincipal()).isSameAs(principal);
            assertThat(authenticationAtChain.getAuthorities()).isEmpty();
            assertThat(request.getAttribute("authorizationPrincipal")).isSameAs(principal);
            assertThat(request.getAttribute("userId"))
                    .isInstanceOf(UUID.class)
                    .isEqualTo(principal.userId());
            assertThat(request.getAttribute("deptId")).isEqualTo(databaseDept);
            assertThat(request.getAttribute("username")).isEqualTo("database-alice");
            assertThat(request.getAttribute("roleCodes")).isEqualTo(List.of("admin"));
            assertThat(request.getAttribute("dataScope")).isEqualTo(DataScope.ALL);
            return null;
        }).when(chain).doFilter(any(), any());
        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(request.getAttribute("authorizationPrincipal")).isSameAs(principal);
        assertThat(request.getAttribute("userId"))
                .isInstanceOf(UUID.class)
                .isEqualTo(principal.userId());
        assertThat(request.getAttribute("deptId")).isEqualTo(databaseDept);
        assertThat(request.getAttribute("username")).isEqualTo("database-alice");
        assertThat(request.getAttribute("roleCodes")).isEqualTo(List.of("admin"));
        assertThat(request.getAttribute("dataScope")).isEqualTo(DataScope.ALL);
        InOrder order = inOrder(authService, principalFacade, chain);
        order.verify(authService).isTokenBlacklisted("valid.version-hash");
        order.verify(principalFacade).requireCurrent(userId, 4L);
        order.verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(chain);
    }

    @ParameterizedTest(name = "{0} {1} should invoke filter chain: {2}")
    @MethodSource("pendingActivationPaths")
    void nonLegacyMode_shouldUseDatabasePendingPrincipalForActivationPaths(
            String method,
            String path,
            boolean shouldInvokeChain) throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L, UUID.randomUUID(), "token-user", false);
        AuthorizationPrincipal principal =
                new AuthorizationPrincipal(userId, UUID.randomUUID(), "database-user", 4L, true);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("database.pending", claims);
        when(principalFacade.requireCurrent(userId, 4L)).thenReturn(principal);
        MockHttpServletRequest request =
                authenticatedRequest("database.pending", method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(principalFacade).requireCurrent(userId, 4L);
        if (shouldInvokeChain) {
            verify(chain).doFilter(request, response);
            assertThat(response.getStatus()).isNotEqualTo(403);
            assertThat(authenticatedPrincipal()).isSameAs(principal);
        } else {
            verifyNoInteractions(chain);
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Test
    void pendingActivationPolicy_shouldIgnoreTrueTokenValueWhenDatabasePrincipalIsActive()
            throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 4L, UUID.randomUUID(), "token-user", true);
        AuthorizationPrincipal principal =
                new AuthorizationPrincipal(userId, UUID.randomUUID(), "database-user", 4L, false);
        when(runtimeProperties.requiresVersionValidation()).thenReturn(true);
        stubAcceptedToken("database.active", claims);
        when(principalFacade.requireCurrent(userId, 4L)).thenReturn(principal);

        MockHttpServletRequest request = authenticatedRequest("database.active", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute("authorizationPrincipal")).isSameAs(principal);
    }

    @Test
    void legacyMode_shouldUseVersionOneWhenAuthorizationVersionIsMissingWithoutCallingFacade()
            throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, null);
        stubAcceptedToken("legacy.missing", claims);

        MockHttpServletRequest request = authenticatedRequest("legacy.missing", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        AuthorizationPrincipal principal = authenticatedPrincipal();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(principal.authzVersion()).isEqualTo(1L);
        assertThat(request.getAttribute("authorizationPrincipal")).isSameAs(principal);
        verifyNoInteractions(principalFacade);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -7L})
    void legacyMode_shouldUseVersionOneForNonPositiveAuthorizationVersionWithoutCallingFacade(
            long tokenVersion) throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, tokenVersion);
        stubAcceptedToken("legacy.nonpositive", claims);

        MockHttpServletResponse response = filter("legacy.nonpositive", "/products");

        assertThat(response.getStatus()).isNotEqualTo(401);
        assertThat(authenticatedPrincipal().authzVersion()).isEqualTo(1L);
        verifyNoInteractions(principalFacade);
    }

    @Test
    void legacyMode_shouldPreservePositiveAuthorizationVersionWithoutCallingFacade() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = accessClaims(userId, 9L);
        stubAcceptedToken("legacy.versioned", claims);

        MockHttpServletRequest request = authenticatedRequest("legacy.versioned", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);

        AuthorizationPrincipal principal = authenticatedPrincipal();
        assertThat(principal.authzVersion()).isEqualTo(9L);
        assertThat(request.getAttribute("userId"))
                .isInstanceOf(UUID.class)
                .isEqualTo(userId);
        InOrder order = inOrder(authService, chain);
        order.verify(authService).isTokenBlacklisted("legacy.versioned-hash");
        order.verify(chain).doFilter(request, response);
        verifyNoMoreInteractions(chain);
        verifyNoInteractions(principalFacade);
    }

    private static Stream<Arguments> pendingActivationPaths() {
        return Stream.of(
                Arguments.of("GET", "/users/current", true),
                Arguments.of("PUT", "/users/current/password", true),
                Arguments.of("GET", "/products", false),
                Arguments.of("GET", "/users/current/data-scope", false));
    }

    private JwtAuthenticationFilter newFilter(Environment environment) {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                authService,
                principalFacade,
                runtimeProperties,
                objectMapper,
                environment);
    }

    private Claims accessClaims(UUID userId, Long authzVersion) {
        return accessClaims(userId, authzVersion, UUID.randomUUID(), "token-user", false);
    }

    private Claims accessClaims(
            UUID userId,
            Long authzVersion,
            UUID deptId,
            String username,
            boolean pendingActivation) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.getSubject()).thenReturn(userId.toString());
        lenient().when(claims.get("deptId")).thenReturn(deptId.toString());
        lenient().when(claims.get("dataScope", Integer.class)).thenReturn(3);
        lenient().when(claims.get("roleCodes", List.class)).thenReturn(List.of("admin"));
        lenient().when(claims.get("username", String.class)).thenReturn(username);
        lenient().when(claims.get("type", String.class)).thenReturn("access");
        lenient().when(claims.get("pendingActivation", Boolean.class)).thenReturn(pendingActivation);
        lenient().when(claims.get("authzVersion", Long.class)).thenReturn(authzVersion);
        return claims;
    }

    private void stubAcceptedToken(String token, Claims claims) {
        when(jwtTokenProvider.parseClaims(token)).thenReturn(claims);
        when(jwtTokenProvider.getTokenHash(token)).thenReturn(token + "-hash");
        when(authService.isTokenBlacklisted(token + "-hash")).thenReturn(false);
    }

    private MockHttpServletRequest authenticatedRequest(String token, String path) {
        return authenticatedRequest(token, "GET", path);
    }

    private MockHttpServletRequest authenticatedRequest(String token, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletResponse filter(String token, String path) throws Exception {
        MockHttpServletRequest request = authenticatedRequest(token, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse filter(String token, String path, FilterChain chain)
            throws Exception {
        MockHttpServletRequest request = authenticatedRequest(token, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertShortCircuited(FilterChain chain) {
        verifyNoInteractions(chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private AuthorizationPrincipal authenticatedPrincipal() {
        return (AuthorizationPrincipal) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private JsonNode jsonBody(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsByteArray());
    }
}
