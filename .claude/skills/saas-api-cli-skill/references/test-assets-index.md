# 测试资产索引

## 主入口

- `docs/09-测试验收总览.md`
- `docs/验收/回归测试脚本索引.md`
- `docs/验收/E2E浏览器测试手册.md`
- `docs/验收/V1-P0验收清单.md`

## 测试类型

- 后端 JUnit / ArchUnit / 集成测试：`backend/src/test/**/*.java`。
- 前端 Vitest：`frontend/src/**/*.test.ts`。
- 浏览器 E2E：`tests/e2e/**/*.spec.ts`。
- QA / real-pre 脚本：`runtime/qa/*.cjs` 与 `runtime/qa/*.test.cjs`。
- JMeter：`tests/jmeter/*.jmx`。

## 执行入口

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run test -- --run
npm run e2e:v1-p0
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
```

## 证据规则

- 测试文件存在不等于测试通过。
- real-pre 缺 Token、权限包、真实订单、`pick_source` 样本时标记 `BLOCKED` 或 `PENDING`。
- Skill 不复制测试源文件内容，使用 `project-assets-manifest.md` 指向当前项目主源，避免重复内容漂移。
