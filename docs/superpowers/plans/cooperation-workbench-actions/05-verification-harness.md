# 迁移、测试与 Harness 验收

## 后端

```powershell
mvn -f backend/pom.xml "-Dtest=CooperationWorkbenchActionsSchemaContractTest,SamplePrivateNoteMapperPostgresTest,LegacyTalentDomainFacadeTest,SampleCooperationActionPolicyTest,SampleCooperationApplicationServiceTest,SamplePromotionCopyPolicyTest,SampleOrderCopyPolicyTest,SampleControllerTest" test
mvn -f backend/pom.xml clean package -DskipTests
```

验证 `sample_private_note` 表、有效行唯一索引、地址双写和七项操作能力。

## 前端

运行合作单 API、操作列、编辑弹窗、私有备注和工作台相关 Vitest，再执行前端构建。

## 本地 real-pre

- 使用项目固定 `agent-do.ps1` 入口执行 full scope。
- 构建并重启对应容器，检查后端/前端健康。
- 执行安全的合作单页面与 API smoke，不写入无关真实业务数据。
- 生成 `harness/reports/current/latest-cooperation-workbench-actions.md`。
- 不执行远端部署，除非用户明确授权。
