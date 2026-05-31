> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# Environment Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate local environment ambiguity and credential risk by separating local-mock, test, and real integration paths, tightening test-tool exposure, and unifying frontend API routing semantics.

**Architecture:** Treat environment governance as four independent slices with disjoint responsibilities: credential handling, backend profile defaults, frontend API path semantics, and `/api/test/**` exposure. Keep behavior changes minimal and reversible: prefer config/documentation updates first, then narrow code changes at the exact interception points already identified (`application.yml`, `.env*`, `WebConfig`, `TestController`, frontend request bootstrap).

**Tech Stack:** Spring Boot, Vue 3, Vite, Docker Compose, PowerShell scripts, Git ignore rules, Markdown docs

---

## File Structure

### Environment and credential files

- Modify: `.gitignore`
- Create: `.env.example`
- Create: `.env.local-mock.example`
- Create: `.env.test.example`
- Create: `.env.prod.example`
- Modify or remove from working-tree usage: `.env`, `.env.test`, `.env.prod`, `.env.real`

### Backend environment loading

- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-local-mock.yml`
- Modify: `backend/src/main/resources/application-test.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `docker-compose.yml`
- Modify: `docker-compose.test.yml`
- Review-only for real path alignment: `docker-compose.real-pre.yml`, `scripts/start-real-pre.ps1`, `scripts/start-test-all.ps1`

### Backend test-tool exposure

- Modify: `backend/src/main/java/com/colonel/saas/controller/TestController.java`
- Modify: `backend/src/main/java/com/colonel/saas/config/WebConfig.java`
- Modify: `backend/src/main/java/com/colonel/saas/security/JwtAuthInterceptor.java`
- Optional create if logging needs isolation: `backend/src/main/java/com/colonel/saas/config/LocalMockTestEndpointConfig.java`

### Frontend API routing

- Modify: `frontend/.env.development`
- Modify: `frontend/.env.production`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/utils/request.ts`
- Delete or deprecate: `frontend/src/api/index.ts`

### Documentation

- Modify: `README.md`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/16-local-mock业务联调记录.md`
- Modify: `docs/17-项目剩余事项看板.md`

---

### Task 1: Credential Governance First

**Files:**
- Modify: `.gitignore`
- Create: `.env.example`
- Create: `.env.local-mock.example`
- Create: `.env.test.example`
- Create: `.env.prod.example`
- Create: `.env.real.example` (sanitize existing file contents if retained)
- Modify: `README.md`
- Modify: `docs/17-项目剩余事项看板.md`

- [ ] **Step 1: Write the failing policy check as a repository audit note**

Create a short audit section in the plan execution branch notes before changing files:

```text
Current failure conditions:
1. Real-looking credential files exist in repo root working tree.
2. No dedicated .env.local-mock.example exists.
3. .gitignore only whitelists .env.example, not the full example matrix.
4. Docs do not explicitly say "real credentials must never live in repo root shared env files".
```

Expected result before implementation: the repository still allows ambiguous env handling.

- [ ] **Step 2: Update `.gitignore` to explicitly protect all live env files while keeping examples**

Apply this minimal pattern set:

```gitignore
.env
.env.*
!.env.example
!.env.local-mock.example
!.env.test.example
!.env.real.example
!.env.prod.example
```

This keeps the ignore behavior explicit and future-proof instead of relying on only one negation.

- [ ] **Step 3: Create sanitized example files with placeholder-only values**

Create the example set with no live secrets:

```dotenv
# .env.local-mock.example
SPRING_PROFILES_ACTIVE=local-mock
DB_HOST=localhost
DB_PORT=5432
DB_NAME=colonel_saas
DB_USER=saas
DB_PASSWORD=change-me
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
JWT_SECRET=replace-with-random-64-char-secret
DOUYIN_TEST_ENABLED=true
DOUYIN_MOCK_ENABLED=true
DOUYIN_APP_ID=replace-with-test-app-id
DOUYIN_CLIENT_KEY=replace-with-test-client-key
DOUYIN_CLIENT_SECRET=replace-with-test-client-secret
DOUYIN_TOKEN_AUTO_REFRESH_ENABLED=false
TALENT_ENRICH_MODE=test
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
```

```dotenv
# .env.test.example
SPRING_PROFILES_ACTIVE=test
DB_HOST=localhost
DB_PORT=5432
DB_NAME=colonel_saas_test
DB_USER=saas
DB_PASSWORD=change-me
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=1
JWT_SECRET=replace-with-random-64-char-secret
DOUYIN_TEST_ENABLED=true
DOUYIN_MOCK_ENABLED=true
DOUYIN_TOKEN_AUTO_REFRESH_ENABLED=false
TALENT_ENRICH_MODE=test
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
```

```dotenv
# .env.real.example / .env.prod.example
SPRING_PROFILES_ACTIVE=prod
DB_HOST=localhost
DB_PORT=5432
DB_NAME=colonel_saas_real
DB_USER=saas
DB_PASSWORD=change-me
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=2
JWT_SECRET=replace-with-random-64-char-secret
DOUYIN_TEST_ENABLED=false
DOUYIN_BASE_URL=https://openapi-fxg.jinritemai.com
DOUYIN_APP_ID=replace-with-real-app-id
DOUYIN_CLIENT_KEY=replace-with-real-client-key
DOUYIN_CLIENT_SECRET=replace-with-real-client-secret
DOUYIN_TOKEN_AUTO_REFRESH_ENABLED=false
DOUYIN_WEBHOOK_VERIFY_SIGN=true
TALENT_ENRICH_MODE=real
TALENT_PUBLIC_PAGE_CRAWL_ENABLED=false
```

- [ ] **Step 4: Document the credential policy and rotation recommendation**

Add a short explicit policy block to `README.md` and `docs/17-项目剩余事项看板.md`:

```md
## Environment Secret Policy

- Real credentials must not be stored in tracked repository files.
- Repository examples may contain field names only, never live values.
- Local real-integration credentials must live in untracked env files, CI/CD secrets, or server environment variables.
- If a real Douyin secret was ever shared in a working tree, screenshots, or prior commits, rotate `DOUYIN_CLIENT_SECRET` and related credentials.
```

- [ ] **Step 5: Verify the repository no longer tracks live env files**

Run:

```bash
git ls-files .env .env.* 
```

Expected:

```text
No live .env / .env.* files are tracked.
Only example files remain trackable by policy.
```

- [ ] **Step 6: Commit**

```bash
git add .gitignore .env.example .env.local-mock.example .env.test.example .env.real.example .env.prod.example README.md docs/17-项目剩余事项看板.md
git commit -m "chore: harden environment credential handling"
```

---

### Task 2: Unify Profile Responsibilities

**Files:**
- Modify: `.env`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `backend/src/main/resources/application-local-mock.yml`
- Modify: `docker-compose.yml`
- Modify: `docker-compose.test.yml`
- Modify: `README.md`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/16-local-mock业务联调记录.md`
- Modify: `docs/17-项目剩余事项看板.md`

- [ ] **Step 1: Write the failing environment expectation as a reproducible checklist**

Before changing config, confirm the mismatch with this checklist:

```text
1. .env says dev.
2. application.yml defaults to dev.
3. docker-compose.yml says local-mock.
4. docker-compose.test.yml says test.
5. docs still describe earlier dev/test mixed facts.
```

Expected current result:

```text
At least two startup paths resolve to different profiles for "local development".
```

- [ ] **Step 2: Make `local-mock` the only default for local human startup**

Update `.env` and `application.yml` to align:

```dotenv
# .env
SPRING_PROFILES_ACTIVE=local-mock
```

```yaml
# backend/src/main/resources/application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local-mock}
```

This removes the hidden fallback to `dev`.

- [ ] **Step 3: Freeze explicit responsibilities per profile in docs and config comments**

Add comments and doc wording with this exact matrix:

```text
local-mock = local human business walkthroughs
test = automated tests and isolated test compose startup
prod = real integration / production-style runtime
dev = legacy compatibility only, not a recommended entrypoint
```

For `application-dev.yml`, add a prominent header comment:

```yaml
# Legacy compatibility profile.
# Do not use as the default local business walkthrough entrypoint.
# Preferred local profile: local-mock
```

- [ ] **Step 4: Keep compose entrypoints explicit and consistent**

Verify or adjust only if needed:

```yaml
# docker-compose.yml
SPRING_PROFILES_ACTIVE: local-mock

# docker-compose.test.yml
SPRING_PROFILES_ACTIVE: test
```

Document in `README.md`:

```md
- Local mock walkthrough: `docker compose up -d`
- Isolated test stack: `docker compose -f docker-compose.yml -f docker-compose.test.yml up -d`
- Real/pre integration: use dedicated real-pre script or compose file only
```

- [ ] **Step 5: Verify all startup paths resolve to the intended roles**

Run:

```bash
docker compose config
docker compose -f docker-compose.yml -f docker-compose.test.yml config
```

Expected:

```text
Default compose => local-mock
Test compose overlay => test
No default path resolves to dev anymore
```

- [ ] **Step 6: Commit**

```bash
git add .env backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml backend/src/main/resources/application-local-mock.yml docker-compose.yml docker-compose.test.yml README.md docs/04-开发进度.md docs/16-local-mock业务联调记录.md docs/17-项目剩余事项看板.md
git commit -m "chore: unify local mock profile ownership"
```

---

### Task 3: Unify Frontend API Base Semantics

**Files:**
- Modify: `frontend/.env.development`
- Modify: `frontend/.env.production`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/src/utils/request.ts`
- Delete: `frontend/src/api/index.ts`
- Modify: `README.md`
- Modify: `docs/17-项目剩余事项看板.md`

- [ ] **Step 1: Write the failing routing assumption**

Record the current ambiguity:

```text
1. frontend/src/utils/request.ts uses baseURL '/api'
2. frontend/src/api/index.ts uses VITE_API_BASE_URL or http://localhost:8080/api
3. frontend/.env.development sets VITE_API_BASE_URL=http://localhost:8080/api
4. vite proxy also rewrites /api to backend target
```

Expected current result:

```text
Two API bootstrap styles coexist, and one future import could reintroduce /api/api duplication risk.
```

- [ ] **Step 2: Choose one routing model and encode it everywhere**

Keep the working model:

```ts
// frontend/src/utils/request.ts
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
});
```

Then simplify env usage so development env does not declare a full API base:

```dotenv
# frontend/.env.development
VITE_APP_TITLE=抖音团长SaaS系统
VITE_ENV_LABEL=本地 Mock
```

Use proxy target only when needed:

```yaml
# docker-compose.yml frontend service
VITE_PROXY_TARGET: http://backend:8080
```

- [ ] **Step 3: Remove the unused alternate axios bootstrap**

Delete `frontend/src/api/index.ts` if unused, or replace its contents with a deprecation shim:

```ts
export { default } from '../utils/request';
```

The preferred minimal option is deletion if no imports exist.

- [ ] **Step 4: Keep Vite proxy semantics explicit**

Retain or simplify this structure in `frontend/vite.config.ts`:

```ts
server: {
  proxy: {
    '/api': createApiProxy(),
    '/douyin': createApiProxy({
      rewrite: (path) => `/api${path}`
    })
  }
}
```

And document:

```md
Frontend code always requests `/api/...`.
Vite proxy decides where `/api` points during development.
```

- [ ] **Step 5: Verify there is no duplicate base assembly risk**

Run:

```bash
Get-ChildItem -Recurse -File frontend\src | Select-String -Pattern "VITE_API_BASE_URL|baseURL|/api/"
npm --prefix frontend run build
```

Expected:

```text
Only one active axios bootstrap remains.
Frontend build passes.
No request path requires concatenating an extra /api prefix.
```

- [ ] **Step 6: Commit**

```bash
git add frontend/.env.development frontend/.env.production frontend/vite.config.ts frontend/src/utils/request.ts README.md docs/17-项目剩余事项看板.md
git rm frontend/src/api/index.ts
git commit -m "chore: unify frontend api routing semantics"
```

---

### Task 4: Restrict and Clarify `/api/test/**` Exposure

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/controller/TestController.java`
- Modify: `backend/src/main/java/com/colonel/saas/config/WebConfig.java`
- Modify: `backend/src/main/java/com/colonel/saas/security/JwtAuthInterceptor.java`
- Modify: `backend/src/main/resources/application-local-mock.yml`
- Modify: `backend/src/main/resources/application-test.yml`
- Modify: `README.md`
- Modify: `docs/16-local-mock业务联调记录.md`
- Modify: `docs/17-项目剩余事项看板.md`

- [ ] **Step 1: Write the failing security regression test**

Create a focused MVC test file:

```java
// backend/src/test/java/com/colonel/saas/controller/TestControllerSecurityTest.java
@SpringBootTest
@AutoConfigureMockMvc
class TestControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void optionsOnTestSeed_shouldNotReturn401_inLocalMock() throws Exception {
        mockMvc.perform(options("/test/seed"))
                .andExpect(status().isOk());
    }
}
```

Expected failure before implementation:

```text
FAIL because JwtAuthInterceptor currently blocks OPTIONS without Authorization.
```

- [ ] **Step 2: Add a second failing test proving destructive endpoints are profile-bound**

Add one context guard test:

```java
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "app.test.enabled=false"
})
@AutoConfigureMockMvc
class TestControllerDisabledInProdTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedEndpoint_shouldNotExistWhenTestToolsDisabled() throws Exception {
        mockMvc.perform(post("/test/seed"))
                .andExpect(status().is4xxClientError());
    }
}
```

Expected failure before implementation if controller is too broadly available.

- [ ] **Step 3: Bind the controller to local/test-only enablement**

Apply the narrowest safe change in `TestController.java`:

```java
@RestController
@RequestMapping("/test")
@Profile({"local-mock", "test"})
@ConditionalOnProperty(prefix = "app.test", name = "enabled", havingValue = "true")
public class TestController extends BaseController {
```

This ensures `prod` and real integration startup cannot accidentally expose the controller even if a property is mis-set.

- [ ] **Step 4: Stop blocking CORS preflight and explicitly whitelist local test paths**

Update `WebConfig.java` exclusions:

```java
.excludePathPatterns(
        "/auth/login",
        "/test/**",
        "/douyin/webhooks/**",
        "/error",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-resources/**",
        "/doc.html",
        "/actuator/**"
);
```

If destructive endpoints must remain protected, use a narrower interceptor change instead:

```java
if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
    return true;
}
```

Recommended sequence:
1. Always allow `OPTIONS`
2. Then decide whether `/test/**` stays authenticated or profile-local anonymous

- [ ] **Step 5: Add explicit startup logging for test tools**

Add one startup log in a local/test-only config or controller constructor:

```java
log.warn("TestController enabled under profile(s): local-mock/test only");
```

This gives the operator visible proof that test tools are intentionally loaded.

- [ ] **Step 6: Run tests and probe behavior**

Run:

```bash
cd backend
mvn "-Dtest=TestControllerSecurityTest,TestControllerDisabledInProdTest" test
```

Then verify manually:

```bash
curl -i -X OPTIONS http://localhost:8080/api/test/seed
curl -i http://localhost:8080/api/actuator/health
```

Expected:

```text
OPTIONS on /api/test/seed no longer returns 401 in local-mock
prod/real startup path does not expose TestController
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/controller/TestController.java backend/src/main/java/com/colonel/saas/config/WebConfig.java backend/src/main/java/com/colonel/saas/security/JwtAuthInterceptor.java backend/src/main/resources/application-local-mock.yml backend/src/main/resources/application-test.yml backend/src/test/java/com/colonel/saas/controller/TestControllerSecurityTest.java README.md docs/16-local-mock业务联调记录.md docs/17-项目剩余事项看板.md
git commit -m "chore: confine local test endpoints to mock profiles"
```

---

### Task 5: Final Governance Verification and Documentation Sweep

**Files:**
- Modify: `docs/04-开发进度.md`
- Modify: `docs/16-local-mock业务联调记录.md`
- Modify: `docs/17-项目剩余事项看板.md`
- Modify: `README.md`

- [ ] **Step 1: Correct stale environment statements**

Replace outdated statements such as:

```md
- docker-compose.yml 固定 dev
- application-local-mock.yml 不存在
- 本地容器仍是 dev + mock enabled
```

with current post-governance facts:

```md
- docker-compose.yml is the standard local-mock entrypoint
- application-local-mock.yml is the standard local walkthrough profile
- test is reserved for automated and isolated verification
```

- [ ] **Step 2: Write a final operator checklist**

Add a short final checklist to `README.md`:

```md
## Local Startup Checklist

1. Use `docker compose up -d` for local-mock.
2. Use `docker compose -f docker-compose.yml -f docker-compose.test.yml up -d` for isolated test.
3. Do not use `dev` as the default local walkthrough profile.
4. Do not store real Douyin credentials in tracked files.
5. Use `/api/test/**` only in local-mock or test.
```

- [ ] **Step 3: Run the final verification set**

Run:

```bash
docker compose config
docker compose -f docker-compose.yml -f docker-compose.test.yml config
curl http://localhost:8080/api/actuator/health
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
npm --prefix frontend run build
cd backend && mvn "-Dtest=TestControllerSecurityTest" test
```

Expected:

```text
Config output shows local-mock and test exactly where intended.
Health endpoint returns UP.
Login succeeds.
Frontend build passes.
Test endpoint security tests pass.
```

- [ ] **Step 4: Commit**

```bash
git add README.md docs/04-开发进度.md docs/16-local-mock业务联调记录.md docs/17-项目剩余事项看板.md
git commit -m "docs: finalize environment governance baseline"
```

---

## Self-Review

### Spec coverage

- Profile dual-truth issue: covered by Task 2 and Task 5.
- Real credential exposure: covered by Task 1.
- `/api/test/**` auth and OPTIONS 401: covered by Task 4.
- Frontend base URL ambiguity: covered by Task 3.
- Execution order by P0/P1/P2: reflected below.

### Placeholder scan

- No `TODO` / `TBD` placeholders remain.
- Every task includes exact files, commands, and expected outcomes.

### Type consistency

- `local-mock`, `test`, `prod`, and `dev` naming is used consistently.
- `TestController`, `WebConfig`, `JwtAuthInterceptor`, and `frontend/src/utils/request.ts` are referenced consistently across tasks.

---

## Recommended Execution Order

1. `P0` Task 1: Credential Governance First
2. `P0` Task 2: Unify Profile Responsibilities
3. `P1` Task 3: Unify Frontend API Base Semantics
4. `P1/P2` Task 4: Restrict and Clarify `/api/test/**` Exposure
5. Task 5: Final Governance Verification and Documentation Sweep

