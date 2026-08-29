# Phase 2 Task 1：三态配置与默认 LEGACY

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 1: 固化三态配置和默认 LEGACY

**Files:**

- Create: `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationRuntimeMode.java`
- Create: `backend/src/main/java/com/colonel/saas/config/AuthorizationRuntimeProperties.java`
- Create: `backend/src/test/java/com/colonel/saas/config/AuthorizationRuntimePropertiesTest.java`
- Create: `backend/src/test/java/com/colonel/saas/config/DddAuthorizationRuntimeDefaultsContractTest.java`
- Modify: `backend/src/main/resources/application.yml:70-74`
- Modify: `backend/src/main/resources/application-real-pre.yml:31-34`
- Modify: `backend/src/main/resources/application-test.yml:1-4`

- [ ] **Step 1: Write the failing configuration tests**

```java
class AuthorizationRuntimePropertiesTest {

    @Test
    void defaultModeIsLegacyAndDomainOverrideIsCaseInsensitive() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();
        properties.setDomainModes(Map.of("sample", AuthorizationRuntimeMode.SHADOW));

        assertThat(properties.modeFor("SAMPLE")).isEqualTo(AuthorizationRuntimeMode.SHADOW);
        assertThat(properties.modeFor("order")).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(properties.requiresVersionValidation()).isTrue();
    }

    @Test
    void untouchedPropertiesDoNotEnableVersionValidation() {
        AuthorizationRuntimeProperties properties = new AuthorizationRuntimeProperties();

        assertThat(properties.getDefaultMode()).isEqualTo(AuthorizationRuntimeMode.LEGACY);
        assertThat(properties.requiresVersionValidation()).isFalse();
        assertThat(properties.getSnapshotCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }
}
```

`DddAuthorizationRuntimeDefaultsContractTest` 必须逐个读取三个 profile，并断言都包含：

```java
assertThat(source)
        .contains("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:LEGACY}")
        .doesNotContain("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:SHADOW}")
        .doesNotContain("${AUTHORIZATION_RUNTIME_DEFAULT_MODE:ENFORCE}");
```

- [ ] **Step 2: Run RED**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationRuntimePropertiesTest,DddAuthorizationRuntimeDefaultsContractTest"
Pop-Location
```

Expected: FAIL because the two production types and YAML keys do not exist.

- [ ] **Step 3: Add the mode enum and properties**

```java
package com.colonel.saas.domain.user.api;

public enum AuthorizationRuntimeMode {
    LEGACY,
    SHADOW,
    ENFORCE
}
```

```java
package com.colonel.saas.config;

import com.colonel.saas.domain.user.api.AuthorizationRuntimeMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "authorization.runtime")
public class AuthorizationRuntimeProperties {

    private AuthorizationRuntimeMode defaultMode = AuthorizationRuntimeMode.LEGACY;
    private Map<String, AuthorizationRuntimeMode> domainModes = new LinkedHashMap<>();
    private Duration snapshotCacheTtl = Duration.ofMinutes(5);

    public AuthorizationRuntimeMode modeFor(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return defaultMode;
        }
        String normalized = domainCode.trim().toLowerCase(Locale.ROOT);
        return domainModes.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                        && normalized.equals(entry.getKey().trim().toLowerCase(Locale.ROOT)))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(defaultMode);
    }

    public boolean requiresVersionValidation() {
        return defaultMode != AuthorizationRuntimeMode.LEGACY
                || domainModes.values().stream()
                .anyMatch(mode -> mode != null && mode != AuthorizationRuntimeMode.LEGACY);
    }
}
```

Add to all three YAML files, with identical safe defaults:

```yaml
authorization:
  runtime:
    default-mode: ${AUTHORIZATION_RUNTIME_DEFAULT_MODE:LEGACY}
    snapshot-cache-ttl: ${AUTHORIZATION_SNAPSHOT_CACHE_TTL:5m}
    domain-modes: {}
```

- [ ] **Step 4: Run GREEN and commit**

```powershell
Push-Location backend
mvn -q -DforkCount=0 test "-Dtest=AuthorizationRuntimePropertiesTest,DddAuthorizationRuntimeDefaultsContractTest"
Pop-Location
git add backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationRuntimeMode.java backend/src/main/java/com/colonel/saas/config/AuthorizationRuntimeProperties.java backend/src/main/resources/application.yml backend/src/main/resources/application-real-pre.yml backend/src/main/resources/application-test.yml backend/src/test/java/com/colonel/saas/config/AuthorizationRuntimePropertiesTest.java backend/src/test/java/com/colonel/saas/config/DddAuthorizationRuntimeDefaultsContractTest.java
git commit -m "feat(auth): define safe authorization runtime modes"
```

Expected: tests PASS; committed profiles still default to `LEGACY`.

