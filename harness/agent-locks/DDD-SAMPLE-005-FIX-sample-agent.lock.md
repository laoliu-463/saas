# Agent Lock

task_id: DDD-SAMPLE-005-FIX
agent_name: Sample Agent
branch_name: feature/ddd/DDD-SAMPLE-005-FIX-sample-agent
status: in_progress
started_at: 2026-06-10T20:10:00

## Allowed Paths
- backend/src/main/java/com/colonel/saas/controller/SampleController.java
- backend/src/main/java/com/colonel/saas/service/sample/LegacySampleQueryService.java
- backend/src/main/java/com/colonel/saas/service/sample/SampleQueryService.java
- backend/src/main/java/com/colonel/saas/service/sample/SampleApplicationService.java
- backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
- harness/reports/ddd-sample-005-fix-*.md
- harness/handovers/ddd-sample-005-fix-*.md

## Forbidden Paths
- backend/src/main/java/**/product/**
- backend/src/main/java/**/order/**
- backend/src/main/java/**/performance/**
- backend/src/main/java/**/talent/**
- frontend/**
- docker-compose*.yml
- application*.yml

## Shared Files Requested
无

## Expected Outputs
- 打破 LegacySampleQueryService ↔ SampleController 循环依赖
- 使全量 Spring 测试 (ColonelSaasApplicationTests) 通过
- 测试报告

## Boundary Statement
本任务只修复 SampleQueryService 委派链的循环依赖，不修改寄样状态机、不修改导出逻辑、不修改 API 响应。
