# DDD-PHASE0-ARBITRATION 仲裁与收口报告

## 1. 事实纠正说明

此前关于“分析域/配置域任务卡缺失”的描述已被确认为**事实错误**：
* 实际检索结果表明，[D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\tasks](file:///D:/Docs/Books/my%20second%20brain/团长SaaS知识库/plans/ddd-refactor/tasks) 目录下已完整存在全部 9 个只读审查的 `.md` 任务卡。
* 所有 Phase 0 审查状态已全部闭环为 `DONE_AUDIT`，并在任务索引中完成对账。

---

## 2. 关键禁止结论强力确认

为确保下一步重构的安全与规范，本仲裁特别在收口阶段显式确认以下两项核心红线规则，后续 Agent 必须强制遵守：

### 规则确认 1：【禁止跳过防护测试直接进入 Facade】 (第 8 条)
* **内容：** 任何领域在开展 Facade 重构前，必须确保其相对应的防护测试已在本地环境（及 real-pre）通过验证。严禁跳过 Phase 1（防护测试阶段）直接针对代码进行 Facade 抽象设计或物理包重构。

### 规则确认 2：【禁止把结算轨无样本写成已完成】 (第 5 条)
* **内容：** 涉及真实抖音结算流的业绩计算对账，若由于缺少真实加密或解密结算报文样本而无法在真实环境中跑通对账，必须客观标注其状态为 `BLOCKED_BY_SAMPLE` 或者是 `PENDING`，严禁通过 Mock 数据宣称结算闭环已“通过（PASS）”。

---

## 3. 验证与结论
* **验证状态：** 执行 `git diff --check`（成功通过）。
* **业务变动：** 本次为纯文档性（docs-only）仲裁任务，无任何 Java / Vue 代码及公式变动。
* **最终结论：** **PASS**。

---

## 4. 阶段任务状态同步（2026-06-09 12:05 更新）

为防止后续 Agent 在读到本仲裁报告时把"DDD 重构就绪"误读为"DDD 重构已动"，本节显式记录 Phase 0.5（前置基线）的落位情况。

### 4.1 已落位：DDD-BASE-001（重构安全开关基线）

* **Commit**：`7f72e51c`（`DDD-BASE-001 add refactor safety toggles`）
* **状态**：`PASS`（**Phase 0.5 阶段性**）
* **范围**：仅 5 个文件（3 个 YAML 末尾追加 + `DddRefactorProperties.java` + 1 个测试）。未触及任何 Controller / Service / Repository / 前端 / DB / Docker / CI / Nginx。
* **结果**：6 个开关全部默认 `false`，`mvn test` 全量 1781/1781 通过，code-review-graph risk score = 0.00。
* **红线**：
  * 0 行为变更（线上与改动前 100% 一致）。
  * 5 个子开关全部 `V1=否`，未开任何一项。
  * 不构成任何领域"已通过 DDD 改造"的依据。
* **三件套审计产物**：
  * 主报告：[ddd-base-001-safety-toggles-20260609-120500.md](ddd-base-001-safety-toggles-20260609-120500.md)
  * 证据：[evidence-20260609-120500-ddd-base-001.md](evidence-20260609-120500-ddd-base-001.md)
  * 复盘：[retro-20260609-120500-ddd-base-001.md](retro-20260609-120500-ddd-base-001.md)
* **配套 KB 文档**：`D:\Docs\Books\my second brain\团长SaaS知识库\plans\ddd-refactor\DDD-BASE-001-safety-toggles.md`（首行已声明用户指引路径与实际落位差异）。

### 4.2 与本仲裁报告 §2 红线的关系

* **规则确认 1**（禁止跳过防护测试直接 Facade）：DDD-BASE-001 仅落位"开关基线"，**没有**也**不能**代替 Phase 1 防护测试。任何领域要打开子开关前，仍须按本仲裁 §2 规则 1 的红线先完成防护测试 + ADR 评审。
* **规则确认 2**（禁止把结算轨无样本写成已完成）：与本任务无关（BASE-001 不涉及结算轨）。已在 evidence §6 复述该红线，避免后续 Agent 在 DDD-BASE-001 报告中误读。

### 4.3 推荐下一步（沿用 retro §4 结论）

* **DDD-USER-SCOPE-PROTECTION-001**（用户域防护测试）：在 DDD-BASE-001 开关就位的前提下，先以用户域作为 Phase 1 防护测试模板，跑通 `self / group / all` 三种范围的边界用例。
* **不要**先打开任何 `ddd.refactor.*.enabled` 子开关；**不要**先做订单域 / 业绩域 / 商品域 / 寄样域（应等用户域模板跑通后再复用）。
