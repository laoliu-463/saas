# Playwright 验收入口

本文件保留为旧书签兼容入口，不再维护第二套 E2E 规则。

- 浏览器测试总览：[docs/验收/E2E浏览器测试手册.md](docs/验收/E2E浏览器测试手册.md)
- real-pre 联调口径：[docs/验收/real-pre联调手册.md](docs/验收/real-pre联调手册.md)
- 测试与证据总览：[docs/09-测试验收总览.md](docs/09-测试验收总览.md)
- 部署后发布门禁：[docs/deploy/README.md](docs/deploy/README.md)

常用命令：

```bash
npm run e2e:v1-p0
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
```

`test/mock` 和 `real-pre` 必须分开运行。缺少真实 Token、权限或业务样本时，记录为 `BLOCKED` / `PENDING`，不得写成 `PASS`。
