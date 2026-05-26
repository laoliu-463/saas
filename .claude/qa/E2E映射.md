# E2E 映射

[V1 必做] `20~24-v1-*` 只允许跑 test/mock 基线。

[V1 必做] `31~36-real-pre-*` 只允许跑 real-pre。

| 脚本 | 范围 | 用途 |
| --- | --- | --- |
| `npm run e2e:smoke` | [V1 简化] | 日常页面保底 |
| `npm run e2e:v1-p0` | [V1 必做] | V1 test/mock P0 |
| `npm run e2e:real-pre:p0` | [V1 必做] | real-pre 统一验收 |
| `npm run e2e:real-pre:all` | [V1 简化] | 细分联调辅助 |

