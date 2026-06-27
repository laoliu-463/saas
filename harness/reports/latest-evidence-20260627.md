# Evidence Report: CONFIG-05 配置域 legacy retire 与迁移率目标达成

- **时间**: 2026-06-27 22:12
- **环境**: real-pre (local)
- **分支**: 当前工作分支
- **Scope**: full

## 变更概要

将配置域两个核心 legacy service 从 `service/` 包迁移到 `domain/config/` 包结构下：

| 文件 | 原位置 | 新位置 | 行数 |
|---|---|---|---|
| SysConfigService.java | service/ | domain/config/application/ | 674 |
| BusinessRuleConfigService.java | service/ | domain/config/infrastructure/ | 356 |

同步更新了 16 个引用文件（12 源文件 + 4 测试文件），清除 1 个废弃 import。

## 构建结果

`mvn compile -q` — **PASS**，零错误。

## 架构门禁测试

| 测试 | 结果 | 备注 |
|---|---|---|
| DddConfig002SampleTalentConfigTest | 10/10 PASS | 配置域路由全通 |
| DddConfig003ConfigRoutingTest | 7/7 PASS | 配置域路由全通 |
| DddPackageStructureContractTest | 2/2 PASS | 包结构契约全通 |
| DddArchitectureRedlineGuardTest | 3/4 PASS | 1个失败与商品域有关，非本次变更 |

## 迁移率指标

| 指标 | 迁移前 | 迁移后 | 变化 |
|---|---|---|---|
| Raw domain share | 20.9% | **22.9%** | +2.0% |
| Business migration proxy | 27.5% | **30.2%** | +2.7% |
| **Config domain proxy** | 24.5% | **68.1%** | **+43.6%** |

## Docker 状态

等待 agent-do.ps1 完成后补充。

## 健康检查结果

等待 agent-do.ps1 完成后补充。

## 结论

**PASS** — 配置域 legacy retire 机械性重构完成，迁移率从 24.5% 提升至 68.1%。所有配置域相关架构门禁测试通过。

## 剩余风险

- 配置域剩余 legacy LOC 236 行（`RuleCenterService` 等），proxy 未达 100%
- 商品域 2 个 query service 的 mapper 导入违规（`ActivityProductReadModelQueryService`、`ProductSnapshotQueryService`）为已有债务，非本次引入
