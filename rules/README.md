# Rules 目录说明

本目录包含抖音团长 SaaS V2.2 系统的所有业务约束规则。

## 文件清单

| 文件 | 用途 | BLOCK 级别 |
|------|------|------------|
| `golden-rules.md` | 黄金规则索引，全局强制 | **CRITICAL** |
| `attribution-logic.md` | 订单归因逻辑约束 | **CRITICAL** |
| `exclusive-triggers.md` | 独家达人/商家判定约束 | **HIGH** |
| `partition-table.md` | PostgreSQL 分区表约束 | **CRITICAL** |
| `entity-constraints.md` | 实体类规范约束 | **HIGH** |
| `data-scope-lint.md` | 多角色数据范围过滤约束 | **CRITICAL** |
| `crawler-safety.md` | 爬虫请求安全约束 | **CRITICAL** |
| `api-security.md` | API 调用与敏感数据约束 | **CRITICAL** |

## 约束级别说明

| 级别 | 含义 | CI 处理 |
|------|------|----------|
| **CRITICAL** | 必须遵守，违反 BLOCK 合并 | SpotBugs / SonarQube |
| **HIGH** | 应该遵守，违反警告 | 代码审查 |
| **MEDIUM** | 推荐遵守 | 代码审查（可选） |

## 使用方式

1. **开发前**：阅读相关约束文件
2. **开发中**：参考约束中的示例代码
3. **代码审查**：检查是否违反约束
4. **CI/CD**：SpotBugs/SonarQube 自动检测

## 新增约束

1. 在 `/rules` 目录创建新的约束文件
2. 在 `golden-rules.md` 中添加索引
3. 在 `AGENTS.md` 中更新约束索引
4. 配置 SpotBugs 或 SonarQube 规则（如适用）
