# Evidence Report

## Metadata

- Time: 2026-06-12 13:09:19 +08:00
- Environment: real-pre
- Scope: backend
- Branch: feature/ddd/DDD-SAMPLE-005-FIX-sample-agent
- Commit: 22e81dae
- Worktree: dirty
- Deploy remote: false

## Modified Files

~~~text
.gitignore
AGENTS.md
backend/pom.xml
backend/src/main/java/com/colonel/saas/controller/SampleController.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
backend/src/main/java/com/colonel/saas/domain/sample/facade/LegacySampleDomainFacade.java
backend/src/main/java/com/colonel/saas/domain/sample/facade/SampleDomainFacade.java
backend/src/main/java/com/colonel/saas/service/OperationLogService.java
backend/src/test/java/com/colonel/saas/architecture/DddConfig003ConfigRoutingTest.java
backend/src/test/java/com/colonel/saas/architecture/DddSample007SampleRoutingTest.java
backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
backend/src/test/java/com/colonel/saas/domain/sample/facade/
CLAUDE.md
docs/01-V1交付范围与边界.md
docs/01-V2交付范围与边界.md
docs/02-V1业务流程与领域设计.md
docs/06-技术架构与数据模型.md
docs/07-部署联调与三方对接.md
docs/09-02-MCP订单归因接口梳理.md
docs/V1对齐-订单域.md
docs/领域/订单域.md
docs/领域/用户域.md
frontend/src/views/ops/DouyinIntegration.vue
harness/archive/retired-content/.gitkeep
harness/archive/retired-content/20260602-153913/doc/计划.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-152855.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153340.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153744.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153913.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153922.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-154011.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-155301.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-190419.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-211152.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-212757.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-214047.md
harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-221609.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-144829.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-151552.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-151654.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-152941.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153044.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153335.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153416.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153508.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-154039.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-154048.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-155301.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-185344.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-185438.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-190419.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-192144.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-193052.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-193606.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-194421.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-211152.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-212757.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-214047.md
harness/archive/retired-content/20260603-reports-archive/evidence-20260602-221622.md
harness/archive/retired-content/20260603-reports-archive/retire-archive-manifest-20260602-1538.json
harness/archive/retired-content/20260603-reports-archive/retire-delete-manifest-20260602-1538.json
harness/archive/retired-content/20260603-reports-archive/retro-20260602-144829.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-151603.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-151702.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-153004.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-153431.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-154053.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-154100.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-185328.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-190432.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-192207.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-193108.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-193616.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-211201.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-212809.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-214055.md
harness/archive/retired-content/20260603-reports-archive/retro-20260602-221636.md
harness/CURRENT_STATE.md
harness/doc/00-HARNESS-README.md
harness/feedback/evidence-report-template.md
harness/feedback/retro-summary-template.md
harness/HARNESS_CHANGELOG.md
harness/INDEX.md
harness/manifests/gc/2026-06-11-harness-gc-plan.md
harness/manifests/harness-gc-20260612.json
harness/manifests/harness-gc-plan-20260612.md
harness/probes/order-attribution.evals.md
harness/probes/p0-regression.evals.md
harness/probes/product-library.evals.md
harness/probes/rbac-scope.evals.md
harness/probes/sample-auto-complete.evals.md
harness/probes/v1-business-closure.evals.md
harness/prompts/agents/00-coordinator.md
harness/prompts/agents/01-architecture-guard.md
harness/prompts/agents/02-user.md
harness/prompts/agents/03-config.md
harness/prompts/agents/04-product.md
harness/prompts/agents/05-talent.md
harness/prompts/agents/06-sample.md
harness/prompts/agents/07-order.md
harness/prompts/agents/08-performance.md
harness/prompts/agents/09-analytics.md
harness/prompts/agents/10-frontend.md
harness/prompts/agents/11-test.md
harness/prompts/agents/12-infra.md
harness/prompts/agents/13-integration.md
harness/prompts/agents/14-review.md
harness/README.md
harness/reports/.gitkeep
harness/reports/archive/20260603/b3-scope-001-batch3-scope-isolation-20260603-143131.md
harness/reports/archive/20260603/completion-gates-update-20260603-100557.md
harness/reports/archive/20260603/content-retire-20260603-093347.md
harness/reports/archive/20260603/content-retire-20260603-095000.md
harness/reports/archive/20260603/content-retire-20260603-103207.md
harness/reports/archive/20260603/content-retire-20260603-111343.md
harness/reports/archive/20260603/content-retire-20260603-113617.md
harness/reports/archive/20260603/content-retire-20260603-213739.md
harness/reports/archive/20260603/content-retire-20260603-214340.md
harness/reports/archive/20260603/ddd-analytics-001-event-consumer.md
harness/reports/archive/20260603/ddd-analytics-002-dashboard-shadow-compare.md
harness/reports/archive/20260603/ddd-base-001-evidence.md
harness/reports/archive/20260603/ddd-base-001-refactor-switches.md
harness/reports/archive/20260603/ddd-base-002-characterization.md
harness/reports/archive/20260603/ddd-base-004-package-structure.md
harness/reports/archive/20260603/ddd-config-001-config-domain-facade.md
harness/reports/archive/20260603/ddd-config-002-sample-talent-config.md
harness/reports/archive/20260603/ddd-config-003-performance-product-config.md
harness/reports/archive/20260603/ddd-config-004-config-updated-event.md
harness/reports/archive/20260603/ddd-dependency-map.md
harness/reports/archive/20260603/ddd-order-001-order-sync-application.md
harness/reports/archive/20260603/ddd-product-002-product-display-policy.md
harness/reports/archive/20260603/ddd-product-005-quick-sample-port.md
harness/reports/archive/20260603/ddd-refactor-plan.md
harness/reports/archive/20260603/ddd-sample-005-sample-query-service.md
harness/reports/archive/20260603/ddd-user-001-facade.md
harness/reports/archive/20260603/ddd-user-002-order-scope.md
harness/reports/archive/20260603/evidence-20260603-093411.md
harness/reports/archive/20260603/evidence-20260603-095000.md
harness/reports/archive/20260603/evidence-20260603-101503.md
harness/reports/archive/20260603/evidence-20260603-104232.md
harness/reports/archive/20260603/evidence-20260603-104601.md
harness/reports/archive/20260603/evidence-20260603-111733.md
harness/reports/archive/20260603/evidence-20260603-113632.md
harness/reports/archive/20260603/evidence-20260603-122021.md
harness/reports/archive/20260603/evidence-20260603-142506.md
harness/reports/archive/20260603/evidence-20260603-143000-sync-plan-001.md
harness/reports/archive/20260603/evidence-20260603-202253.md
harness/reports/archive/20260603/evidence-20260603-202320.md
harness/reports/archive/20260603/evidence-20260603-213739.md
harness/reports/archive/20260603/evidence-20260603-214340.md
harness/reports/archive/20260603/evidence-20260603-230334-harness-debt-governance.md
harness/reports/archive/20260603/func-001-product-card-hover-ui-20260603-111451.md
harness/reports/archive/20260603/git-batch-2-frontend-product-ui-20260603-140800.md
harness/reports/archive/20260603/git-batch-3-backend-user-domain-u2_5-test1-20260603-144936.md
harness/reports/archive/20260603/git-batch-4-reports-20260603-151500.md
harness/reports/archive/20260603/git-batch-c-talent-address-deploy-20260603-225500.md
harness/reports/archive/20260603/git-harness-001-worktree-governance-20260603-150000.md
harness/reports/archive/20260603/git-intake-001-dirty-classify-20260603-225000.md
harness/reports/archive/20260603/harness-debt-governance-inventory-20260603-230334.md
harness/reports/archive/20260603/harness-debt-governance-plan-20260603-230334.md
harness/reports/archive/20260603/order-api-6468-raw-probe-20260603-181634.md
harness/reports/archive/20260603/order-api-6468-verify-20260603-180500.md
harness/reports/archive/20260603/order-api-729-server-verify-20260603-175500.md
harness/reports/archive/20260603/order-api-729-verify-20260603-174500.md
harness/reports/archive/20260603/order-attribution-sample-20260603-222120.md
harness/reports/archive/20260603/order-p0-dual-source-remote-verify-20260603-205719.md
harness/reports/archive/20260603/order-settlement-dual-track-verify-20260603-183157.md
harness/reports/archive/20260603/p0-order-001-diagnosis-20260603-173500.md
harness/reports/archive/20260603/p0-order-001-intake-20260603-172923.md
harness/reports/archive/20260603/p0-order-001-real-order-visible-20260603-180450.md
harness/reports/archive/20260603/p0-sample-001-remote-verify-20260603-221004.md
harness/reports/archive/20260603/p-diag-002-product-library-count-sync-remote-20260603-114742.md
harness/reports/archive/20260603/p-fix-001c-product-library-pagination-20260603-112740.md
harness/reports/archive/20260603/p-fix-001c-product-library-pagination-20260603-113616.md
harness/reports/archive/20260603/p-fix-002a-product-sync-5min-config-20260603-120100.md
harness/reports/archive/20260603/p-fix-002-config-residual-20260603-152000.md
harness/reports/archive/20260603/p-fix-002d-real-pre-runtime-verify-20260603-123411.md
harness/reports/archive/20260603/p-fix-002d-remote-deploy-verify-20260603-132805.md
harness/reports/archive/20260603/p-fix-002-product-sync-display-5min-20260603-121257.md
harness/reports/archive/20260603/retro-20260603-093431.md
harness/reports/archive/20260603/retro-20260603-095028.md
harness/reports/archive/20260603/retro-20260603-104247.md
harness/reports/archive/20260603/retro-20260603-104601.md
harness/reports/archive/20260603/retro-20260603-111824.md
harness/reports/archive/20260603/retro-20260603-113645.md
harness/reports/archive/20260603/retro-20260603-122513.md
harness/reports/archive/20260603/retro-20260603-202309.md
harness/reports/archive/20260603/retro-20260603-202500.md
harness/reports/archive/20260603/retro-20260603-213756.md
harness/reports/archive/20260603/retro-20260603-214350.md
harness/reports/archive/20260603/retro-20260603-223153.md
harness/reports/archive/20260603/retro-20260603-230334-harness-debt-governance.md
harness/reports/archive/20260603/sample-apply-product-library-verify-20260603-214535.md
harness/reports/archive/20260603/session-exit-gate-update-20260603-101403.md
harness/reports/archive/20260603/sync-plan-001-batch-sync-deploy-plan-20260603-143000.md
harness/reports/archive/20260603/talent-address-sample-default-20260603-224000.md
harness/reports/archive/20260603/test-1-full-backend-failures-fix-20260603-104601.md
harness/reports/archive/20260603/user-domain-u1-inventory-20260603-090000.md
harness/reports/archive/20260603/user-domain-u1-inventory-20260603-120000.md
harness/reports/archive/20260603/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md
harness/reports/archive/20260603/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md
harness/reports/archive/20260603/user-domain-u2-model-schema-alignment-20260603-093000.md
harness/reports/archive/20260603/user-domain-u2-model-schema-alignment-20260603-150000.md
harness/reports/archive/20260604/content-retire-20260604-001401.md
harness/reports/archive/20260604/content-retire-20260604-160011.md
harness/reports/archive/20260604/content-retire-20260604-160333.md
harness/reports/archive/20260604/content-retire-20260604-191102.md
harness/reports/archive/20260604/content-retire-20260604-195212.md
harness/reports/archive/20260604/content-retire-20260604-195950.md
harness/reports/archive/20260604/content-retire-20260604-200338.md
harness/reports/archive/20260604/content-retire-20260604-200908.md
harness/reports/archive/20260604/content-retire-20260604-202523.md
harness/reports/archive/20260604/content-retire-20260604-204845.md
harness/reports/archive/20260604/content-retire-20260604-221838.md
harness/reports/archive/20260604/dashboard-money-audit-001-20260604-131908.md
harness/reports/archive/20260604/evidence-20260604-001401-harness-debt-gc-001.md
harness/reports/archive/20260604/evidence-20260604-131908-dashboard-money-audit-001.md
harness/reports/archive/20260604/evidence-20260604-141000-order-frontend-product-info-001.md
harness/reports/archive/20260604/evidence-20260604-141645.md
harness/reports/archive/20260604/evidence-20260604-143000-order-frontend-product-info-002.md
harness/reports/archive/20260604/evidence-20260604-152337-order-product-info-fix.md
harness/reports/archive/20260604/evidence-20260604-160012.md
harness/reports/archive/20260604/evidence-20260604-160333.md
harness/reports/archive/20260604/evidence-20260604-163000-order-list-field-mapping-001.md
harness/reports/archive/20260604/evidence-20260604-181500-order-detail-tab.md
harness/reports/archive/20260604/evidence-20260604-191102.md
harness/reports/archive/20260604/evidence-20260604-191900-order-detail-field-align.md
harness/reports/archive/20260604/evidence-20260604-193000.md
harness/reports/archive/20260604/evidence-20260604-195212.md
harness/reports/archive/20260604/evidence-20260604-195950.md
harness/reports/archive/20260604/evidence-20260604-200338.md
harness/reports/archive/20260604/evidence-20260604-200908.md
harness/reports/archive/20260604/evidence-20260604-202523.md
harness/reports/archive/20260604/evidence-20260604-204437.md
harness/reports/archive/20260604/evidence-20260604-204846.md
harness/reports/archive/20260604/evidence-20260604-204902.md
harness/reports/archive/20260604/evidence-20260604-205824.md
harness/reports/archive/20260604/evidence-20260604-221838.md
harness/reports/archive/20260604/harness-debt-gc-001-inventory-20260604-001052.md
harness/reports/archive/20260604/order-detail-field-align-002-inventory-20260604-163500.md
harness/reports/archive/20260604/order-domain-audit-001-20260604-215143.md
harness/reports/archive/20260604/order-frontend-product-info-001-20260604-141000.md
harness/reports/archive/20260604/order-frontend-product-info-002-20260604-143000.md
harness/reports/archive/20260604/order-list-field-mapping-001-inventory-20260604-161000.md
harness/reports/archive/20260604/order-product-info-fullstack-001-20260604-160510.md
harness/reports/archive/20260604/order-product-info-runtime-supplement-20260604-161450.md
harness/reports/archive/20260604/retro-20260604-001401-harness-debt-gc-001.md
harness/reports/archive/20260604/retro-20260604-131908-dashboard-money-audit-001.md
harness/reports/archive/20260604/retro-20260604-141000-order-frontend-product-info-001.md
harness/reports/archive/20260604/retro-20260604-141715.md
harness/reports/archive/20260604/retro-20260604-143000-order-frontend-product-info-002.md
harness/reports/archive/20260604/retro-20260604-152337-order-product-info-fix.md
harness/reports/archive/20260604/retro-20260604-160034.md
harness/reports/archive/20260604/retro-20260604-160358.md
harness/reports/archive/20260604/retro-20260604-163000-order-list-field-mapping-001.md
harness/reports/archive/20260604/retro-20260604-191127.md
harness/reports/archive/20260604/retro-20260604-195234.md
harness/reports/archive/20260604/retro-20260604-195959.md
harness/reports/archive/20260604/retro-20260604-200348.md
harness/reports/archive/20260604/retro-20260604-200922.md
harness/reports/archive/20260604/retro-20260604-202533.md
harness/reports/archive/20260604/retro-20260604-204454.md
harness/reports/archive/20260604/retro-20260604-204903.md
harness/reports/archive/20260604/retro-20260604-205838.md
harness/reports/archive/20260604/retro-20260604-221901.md
harness/reports/archive/20260605/content-retire-20260605-105947.md
harness/reports/archive/20260605/dashboard-full-money-recon-001-20260605-102309.md
harness/reports/archive/20260605/douyin-signature-invalid-audit-001-20260605-151256.md
harness/reports/archive/20260605/douyin-upstream-reconnect-001-20260605-155351.md
harness/reports/archive/20260605/evidence-20260605-102309-dashboard-full-money-recon-001.md
harness/reports/archive/20260605/evidence-20260605-102309-dashboard-full-money-recon-001-upstream.md
harness/reports/archive/20260605/evidence-20260605-102656-dashboard-full-money-recon-001.md
harness/reports/archive/20260605/evidence-20260605-105947.md
harness/reports/archive/20260605/evidence-20260605-142932-order-field-mapping-audit-001.md
harness/reports/archive/20260605/evidence-20260605-151256-douyin-signature-invalid-audit-001.md
harness/reports/archive/20260605/evidence-20260605-155351-douyin-upstream-reconnect-001.md
harness/reports/archive/20260605/evidence-20260605-161245-order-performance-missing-audit-001.md
harness/reports/archive/20260605/evidence-20260605-DASH-RECON-P0-007-summary-cache.md
harness/reports/archive/20260605/order-detail-tab-fix-001-20260605-100305.md
harness/reports/archive/20260605/order-field-mapping-audit-001-20260605-142932.md
harness/reports/archive/20260605/order-performance-missing-audit-001-20260605-161245.md
harness/reports/archive/20260605/retro-20260605-110019.md
harness/reports/archive/20260605/retro-20260605-151256-douyin-signature-invalid-audit-001.md
harness/reports/archive/20260605/retro-20260605-155351-douyin-upstream-reconnect-001.md
harness/reports/archive/20260605/retro-20260605-161245-order-performance-missing-audit-001.md
harness/reports/archive/20260606/content-retire-20260606-120813.md
harness/reports/archive/20260606/content-retire-20260606-135356.md
harness/reports/archive/20260606/content-retire-20260606-140450.md
harness/reports/archive/20260606/content-retire-20260606-142323.md
harness/reports/archive/20260606/content-retire-20260606-144015.md
harness/reports/archive/20260606/content-retire-20260606-145226.md
harness/reports/archive/20260606/content-retire-20260606-152446.md
harness/reports/archive/20260606/content-retire-20260606-153311.md
harness/reports/archive/20260606/content-retire-20260606-163231.md
harness/reports/archive/20260606/content-retire-20260606-180518.md
harness/reports/archive/20260606/content-retire-20260606-195517.md
harness/reports/archive/20260606/content-retire-20260606-212835.md
harness/reports/archive/20260606/dashboard-baseline-recon-3739-001-20260606-130937.md
harness/reports/archive/20260606/dashboard-money-formula-baseline-6557-001-20260606-200100.md
harness/reports/archive/20260606/dashboard-money-hidden-deduction-8291-001-20260606-220500.md
harness/reports/archive/20260606/dashboard-today-snapshot-recon-001-20260606-154416.md
harness/reports/archive/20260606/dashboard-today-snapshot-recon-001-20260606-154539.md
harness/reports/archive/20260606/douyin-env-secret-runtime-reload-20260606-201929.md
harness/reports/archive/20260606/evidence-20260606-120829.md
harness/reports/archive/20260606/evidence-20260606-121045.md
harness/reports/archive/20260606/evidence-20260606-121756-order-performance-backfill-001.md
harness/reports/archive/20260606/evidence-20260606-122912.md
harness/reports/archive/20260606/evidence-20260606-123500-channel-commission-label-rename-001.md
harness/reports/archive/20260606/evidence-20260606-125134-douyin-upstream-reconnect-local-001.md
harness/reports/archive/20260606/evidence-20260606-130937-dashboard-baseline-recon-3739-001.md
harness/reports/archive/20260606/evidence-20260606-132002-dashboard-baseline-recon-3739-001-addendum.md
harness/reports/archive/20260606/evidence-20260606-133127-order-history-backfill-001.md
harness/reports/archive/20260606/evidence-20260606-135356.md
harness/reports/archive/20260606/evidence-20260606-140450.md
harness/reports/archive/20260606/evidence-20260606-141942.md
harness/reports/archive/20260606/evidence-20260606-142323.md
harness/reports/archive/20260606/evidence-20260606-144015.md
harness/reports/archive/20260606/evidence-20260606-145227.md
harness/reports/archive/20260606/evidence-20260606-151400-order-6468-pagination-fix-001.md
harness/reports/archive/20260606/evidence-20260606-152447.md
harness/reports/archive/20260606/evidence-20260606-153131.md
harness/reports/archive/20260606/evidence-20260606-153311.md
harness/reports/archive/20260606/evidence-20260606-153950-order-6468-pagination-fix-001.md
harness/reports/archive/20260606/evidence-20260606-154416-dashboard-today-snapshot-recon-001.md
harness/reports/archive/20260606/evidence-20260606-154539-dashboard-today-snapshot-recon-001.md
harness/reports/archive/20260606/evidence-20260606-161412.md
harness/reports/archive/20260606/evidence-20260606-163046.md
harness/reports/archive/20260606/evidence-20260606-163231.md
harness/reports/archive/20260606/evidence-20260606-163233.md
harness/reports/archive/20260606/evidence-20260606-164000-dash-money-drift-fix-001.md
harness/reports/archive/20260606/evidence-20260606-164500-order-sync-freshness-optimize-001.md
harness/reports/archive/20260606/evidence-20260606-165000-order-sync-freshness-optimize-001.md
harness/reports/archive/20260606/evidence-20260606-171312-douyin-secret-runtime-verify-002.md
harness/reports/archive/20260606/evidence-20260606-173646-service-fee-income-rule-audit.md
harness/reports/archive/20260606/evidence-20260606-174315-service-fee-profit-rule-audit.md
harness/reports/archive/20260606/evidence-20260606-180600.md
harness/reports/archive/20260606/evidence-20260606-195517.md
harness/reports/archive/20260606/evidence-20260606-195538.md
harness/reports/archive/20260606/evidence-20260606-200100-dashboard-money-formula-baseline-6557-001.md
harness/reports/archive/20260606/evidence-20260606-200650-dashboard-money-formula-baseline-6557-001.md
harness/reports/archive/20260606/evidence-20260606-202013.md
harness/reports/archive/20260606/evidence-20260606-212835.md
harness/reports/archive/20260606/evidence-20260606-213110-service-fee-income-expense-fix.md
harness/reports/archive/20260606/evidence-20260606-220500-dashboard-money-hidden-deduction-8291-001.md
harness/reports/archive/20260606/order-6468-pagination-dryrun-001-20260606-140607.md
harness/reports/archive/20260606/order-6468-pagination-fix-001-20260606-151400.md
harness/reports/archive/20260606/order-6468-pagination-fix-001-20260606-153950.md
harness/reports/archive/20260606/order-history-backfill-001-20260606-133127.md
harness/reports/archive/20260606/order-performance-event-after-commit-fix-001-20260606-121000.md
harness/reports/archive/20260606/order-sync-freshness-optimize-001-20260606-164500.md
harness/reports/archive/20260606/order-sync-freshness-optimize-001-20260606-165000.md
harness/reports/archive/20260606/retro-20260606-120843.md
harness/reports/archive/20260606/retro-20260606-122926.md
harness/reports/archive/20260606/retro-20260606-125134-douyin-upstream-reconnect-local-001.md
harness/reports/archive/20260606/retro-20260606-135422.md
harness/reports/archive/20260606/retro-20260606-140512.md
harness/reports/archive/20260606/retro-20260606-142345.md
harness/reports/archive/20260606/retro-20260606-144037.md
harness/reports/archive/20260606/retro-20260606-145254.md
harness/reports/archive/20260606/retro-20260606-151400-order-6468-pagination-fix-001.md
harness/reports/archive/20260606/retro-20260606-152506.md
harness/reports/archive/20260606/retro-20260606-153330.md
harness/reports/archive/20260606/retro-20260606-153400-order-recent-days-popup.md
harness/reports/archive/20260606/retro-20260606-153950-order-6468-pagination-fix-001.md
harness/reports/archive/20260606/retro-20260606-161441.md
harness/reports/archive/20260606/retro-20260606-164500-order-sync-freshness-optimize-001.md
harness/reports/archive/20260606/retro-20260606-165000-order-sync-freshness-optimize-001.md
harness/reports/archive/20260606/retro-20260606-180615.md
harness/reports/archive/20260606/retro-20260606-200100-dashboard-money-formula-baseline-6557-001.md
harness/reports/archive/20260606/retro-20260606-200650-dashboard-money-formula-baseline-6557-001.md
harness/reports/archive/20260606/retro-20260606-202028.md
harness/reports/archive/20260606/retro-20260606-212859.md
harness/reports/archive/20260606/retro-20260606-220500-dashboard-money-hidden-deduction-8291-001.md
harness/reports/archive/20260607/content-retire-20260607-100701.md
harness/reports/archive/20260607/content-retire-20260607-102505.md
harness/reports/archive/20260607/dashboard-money-local-undercount-7670-001-20260607-095819.md
harness/reports/archive/20260607/evidence-20260607-095819.md
harness/reports/archive/20260607/evidence-20260607-100701.md
harness/reports/archive/20260607/evidence-20260607-100703.md
harness/reports/archive/20260607/evidence-20260607-102505.md
harness/reports/archive/20260607/evidence-20260607-151000.md
harness/reports/archive/20260607/remote-user-manual-001-20260607-200000.md
harness/reports/archive/20260607/retro-20260607-095819.md
harness/reports/archive/20260607/retro-20260607-102529.md
harness/reports/archive/20260607/SECURITY-INCIDENT-001-20260607-115744.md
harness/reports/archive/20260607/SECURITY-INCIDENT-001-FINAL-PAUSE-20260607-115800.md
harness/reports/archive/20260607/SECURITY-INCIDENT-001-FORENSIC-20260607-132211.md
harness/reports/archive/20260608/content-retire-20260608-144219.md
harness/reports/archive/20260608/content-retire-20260608-144245.md
harness/reports/archive/20260608/content-retire-20260608-145206.md
harness/reports/archive/20260608/content-retire-20260608-150249.md
harness/reports/archive/20260608/content-retire-20260608-151615.md
harness/reports/archive/20260608/ddd-audit-config-001-20260608-153000.md
harness/reports/archive/20260608/ddd-audit-cross-domain-001-20260608-144000.md
harness/reports/archive/20260608/ddd-audit-order-001-20260608-145000.md
harness/reports/archive/20260608/ddd-audit-performance-001-20260608-150000.md
harness/reports/archive/20260608/ddd-audit-product-001-20260608-170000.md
harness/reports/archive/20260608/ddd-audit-sample-001-20260608-151500.md
harness/reports/archive/20260608/ddd-audit-user-001-20260608-160500.md
harness/reports/archive/20260608/ddd-refactor-master-plan-001-20260608-135908.md
harness/reports/archive/20260608/evidence-20260608-135908-ddd-refactor-master-plan-001.md
harness/reports/archive/20260608/evidence-20260608-140000-harness-kb-001.md
harness/reports/archive/20260608/evidence-20260608-144000-ddd-audit-cross-domain-001.md
harness/reports/archive/20260608/evidence-20260608-144219.md
harness/reports/archive/20260608/evidence-20260608-144223.md
harness/reports/archive/20260608/evidence-20260608-144245.md
harness/reports/archive/20260608/evidence-20260608-145000-ddd-audit-order-001.md
harness/reports/archive/20260608/evidence-20260608-145206.md
harness/reports/archive/20260608/evidence-20260608-150000-ddd-audit-performance-001.md
harness/reports/archive/20260608/evidence-20260608-150000-harness-kb-count-fix-001.md
harness/reports/archive/20260608/evidence-20260608-150249.md
harness/reports/archive/20260608/evidence-20260608-150646.md
harness/reports/archive/20260608/evidence-20260608-151500-ddd-audit-sample-001.md
harness/reports/archive/20260608/evidence-20260608-151615.md
harness/reports/archive/20260608/evidence-20260608-153000-ddd-audit-config-001.md
harness/reports/archive/20260608/evidence-20260608-160500-ddd-audit-user-001.md
harness/reports/archive/20260608/evidence-20260608-170000-ddd-audit-product-001.md
harness/reports/archive/20260608/harness-kb-001-20260608-140000.md
harness/reports/archive/20260608/retro-20260608-135908-ddd-refactor-master-plan-001.md
harness/reports/archive/20260608/retro-20260608-140000-harness-kb-001.md
harness/reports/archive/20260608/retro-20260608-144000-ddd-audit-cross-domain-001.md
harness/reports/archive/20260608/retro-20260608-144324.md
harness/reports/archive/20260608/retro-20260608-145000-ddd-audit-order-001.md
harness/reports/archive/20260608/retro-20260608-145220.md
harness/reports/archive/20260608/retro-20260608-150000-ddd-audit-performance-001.md
harness/reports/archive/20260608/retro-20260608-151500-ddd-audit-sample-001.md
harness/reports/archive/20260608/retro-20260608-151631.md
harness/reports/archive/20260608/retro-20260608-153000-ddd-audit-config-001.md
harness/reports/archive/20260608/retro-20260608-160500-ddd-audit-user-001.md
harness/reports/archive/20260608/retro-20260608-170000-ddd-audit-product-001.md
harness/reports/archive/20260609/content-retire-20260609-162018.md
harness/reports/archive/20260609/content-retire-20260609-185256.md
harness/reports/archive/20260609/dashboard-metric-copy-fix-20260609-132430.md
harness/reports/archive/20260609/dashboard-today-metric-reconcile-20260609-125500.md
harness/reports/archive/20260609/ddd-audit-analysis-001-20260609-090500.md
harness/reports/archive/20260609/ddd-audit-talent-001-20260609-093000.md
harness/reports/archive/20260609/ddd-base-001-safety-toggles-20260609-120500.md
harness/reports/archive/20260609/ddd-phase0-arbitration-report-20260609-110000.md
harness/reports/archive/20260609/ddd-phase0-audit-status-sync-001-20260609-101500.md
harness/reports/archive/20260609/evidence-20260609-090500-ddd-audit-analysis-001.md
harness/reports/archive/20260609/evidence-20260609-093000-ddd-audit-talent-001.md
harness/reports/archive/20260609/evidence-20260609-101500-ddd-phase0-audit-status-sync-001.md
harness/reports/archive/20260609/evidence-20260609-110000-ddd-phase0-arbitration-report.md
harness/reports/archive/20260609/evidence-20260609-120500-ddd-base-001.md
harness/reports/archive/20260609/evidence-20260609-125500-dashboard-today-metric-reconcile.md
harness/reports/archive/20260609/evidence-20260609-132352.md
harness/reports/archive/20260609/evidence-20260609-161850.md
harness/reports/archive/20260609/evidence-20260609-161916.md
harness/reports/archive/20260609/evidence-20260609-162018.md
harness/reports/archive/20260609/evidence-20260609-185232.md
harness/reports/archive/20260609/evidence-20260609-185256.md
harness/reports/archive/20260609/evidence-20260609-185304.md
harness/reports/archive/20260609/retro-20260609-090500-ddd-audit-analysis-001.md
harness/reports/archive/20260609/retro-20260609-093000-ddd-audit-talent-001.md
harness/reports/archive/20260609/retro-20260609-101500-ddd-phase0-audit-status-sync-001.md
harness/reports/archive/20260609/retro-20260609-110000-ddd-phase0-arbitration-report.md
harness/reports/archive/20260609/retro-20260609-120500-ddd-base-001.md
harness/reports/archive/20260609/retro-20260609-132414.md
harness/reports/archive/20260609/retro-20260609-162122.md
harness/reports/archive/20260609/settle-dashboard-empty-diagnosis-20260609-134601.md
harness/reports/archive/20260609/settlement-api-audit-20260609-154907.md
harness/reports/archive/20260609/settlement-api-audit-20260609-160403.md
harness/reports/archive/20260609/settlement-time-type-fix-20260609-162400.md
harness/reports/archive/20260609/settlement-upstream-verification-material-20260609-160932.md
harness/reports/archive/20260610/content-retire-20260610-100225.md
harness/reports/archive/20260610/content-retire-20260610-100438.md
harness/reports/archive/20260610/content-retire-20260610-100647.md
harness/reports/archive/20260610/content-retire-20260610-100917.md
harness/reports/archive/20260610/content-retire-20260610-101145.md
harness/reports/archive/20260610/content-retire-20260610-101445.md
harness/reports/archive/20260610/content-retire-20260610-101840.md
harness/reports/archive/20260610/evidence-20260610-095712.md
harness/reports/archive/20260610/evidence-20260610-100000-ddd-base-002.md
harness/reports/archive/20260610/evidence-20260610-100225.md
harness/reports/archive/20260610/evidence-20260610-100234.md
harness/reports/archive/20260610/evidence-20260610-100439.md
harness/reports/archive/20260610/evidence-20260610-100452.md
harness/reports/archive/20260610/evidence-20260610-100648.md
harness/reports/archive/20260610/evidence-20260610-100708.md
harness/reports/archive/20260610/evidence-20260610-100917.md
harness/reports/archive/20260610/evidence-20260610-100932.md
harness/reports/archive/20260610/evidence-20260610-101145.md
harness/reports/archive/20260610/evidence-20260610-101230.md
harness/reports/archive/20260610/evidence-20260610-101445.md
harness/reports/archive/20260610/evidence-20260610-101607.md
harness/reports/archive/20260610/evidence-20260610-101840.md
harness/reports/archive/20260610/evidence-20260610-134000-ddd-base-003.md
harness/reports/archive/20260610/evidence-20260610-134959.md
harness/reports/archive/20260610/evidence-20260610-135134.md
harness/reports/archive/20260610/evidence-20260610-135150.md
harness/reports/archive/20260610/evidence-20260610-140205.md
harness/reports/archive/20260610/evidence-20260610-140259.md
harness/reports/archive/20260610/evidence-20260610-163007.md
harness/reports/archive/20260610/evidence-20260610-ddd-user-001.md
harness/reports/archive/20260610/evidence-20260610-phase0-ddd-base.md
harness/reports/archive/20260610/multi-agent-ddd-prompts-full.md
harness/reports/archive/20260610/retro-20260610-100000-ddd-base-002.md
harness/reports/archive/20260610/retro-20260610-102052.md
harness/reports/content-retire-20260610-202445.md
harness/reports/content-retire-20260612-130918.md
harness/reports/current/harness-doc-gc-optimize-001-final.md
harness/reports/ddd-order-002-amount-policy-20260612.md
harness/reports/ddd-product-001-facade-20260612.md
harness/reports/ddd-sample-006-state-machine-20260612.md
harness/reports/ddd-sample-007-facade-routing-20260612.md
harness/reports/ddd-talent-001-facade-20260612.md
harness/reports/ddd-talent-002-claim-policy-20260612.md
harness/reports/ddd-user-003-facade-consumption-20260612.md
harness/reports/evidence-20260610-202445.md
harness/reports/harness-doc-gc-optimize-001-status-before.md
harness/reports/latest-evidence-20260612.md
harness/reports/latest-harness-gc-report.md
harness/reports/latest-harness-inventory.md
harness/reports/latest-harness-limits-check.md
harness/reports/retro-20260612-batch2-policy-done.md
harness/rules/changelog.md
harness/rules/environment/CHEATSHEET.md
harness/rules/environment/envs/01-test环境说明.md
harness/rules/environment/envs/02-real-pre环境说明.md
harness/rules/environment/envs/03-remote-real-pre部署说明.md
harness/rules/environment/envs/04-Docker-Compose服务地图.md
harness/rules/environment/envs/docker-compose-map.md
harness/rules/environment/envs/local-dev-env.md
harness/rules/environment/envs/real-pre-env.md
harness/rules/environment/envs/remote-real-pre-env.md
harness/rules/environment/envs/test-env.md
harness/rules/environment/README.md
harness/rules/feedback/iteration.md
harness/rules/feedback/retire.md
harness/rules/file-retention-policy.md
harness/rules/governance/COMPLETION_GATES.md
harness/rules/governance/completion-gates-detail.md
harness/rules/governance/completion-gates-git.md
harness/rules/governance/domains-map.md
harness/rules/governance/forbidden-scope.md
harness/rules/governance/quality-ledger.md
harness/rules/governance/session-exit-gate.md
harness/rules/governance/task-routing.md
harness/rules/harness-structure-policy.md
harness/rules/instructions/domain/analytics-module.md
harness/rules/instructions/domain/config-domain.md
harness/rules/instructions/domain/order-domain.md
harness/rules/instructions/domain/performance-domain.md
harness/rules/instructions/domain/product-domain.md
harness/rules/instructions/domain/sample-domain.md
harness/rules/instructions/domain/talent-domain.md
harness/rules/instructions/domain/user-domain.md
harness/rules/instructions/governance/01-项目执行协议.md
harness/rules/instructions/governance/02-V1交付合同.md
harness/rules/instructions/governance/03-禁止范围与安全边界.md
harness/rules/instructions/governance/04-文档优先级与冲突处理.md
harness/rules/instructions/governance/05-领域边界总表.md
harness/rules/instructions/governance/definition-of-done.md
harness/rules/instructions/governance/document-priority.md
harness/rules/instructions/governance/multi-agent-ddd-prompts.md
harness/rules/instructions/governance/safety-rules.md
harness/rules/instructions/governance/v1-business-contract.md
harness/rules/locks/DDD-SAMPLE-005-FIX-sample-agent.lock.md
harness/rules/locks/INDEX.md
harness/rules/policies/agent-contract.md
harness/rules/policies/report-style-policy.md
harness/rules/policies/retention-policy.md
harness/rules/policies/structure-policy.md
harness/rules/report-style-policy.md
harness/rules/runbooks/backend-change.md
harness/rules/runbooks/database-change.md
harness/rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md
harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md
harness/rules/runbooks/ddd/HARNESS_ITERATION_ROADMAP.md
harness/rules/runbooks/frontend-change.md
harness/rules/runbooks/governance/closeout-and-gc.md
harness/rules/runbooks/governance/debt-governance.md
harness/rules/runbooks/governance/docker-compose-operations.md
harness/rules/runbooks/governance/scope-command-matrix.md
harness/rules/runbooks/governance/task-lifecycle.md
harness/rules/runbooks/governance/test-validation.md
harness/rules/runbooks/real-pre-change.md
harness/rules/runbooks/remote-deploy.md
harness/rules/runbooks/rollback.md
harness/rules/runbooks/third-party-integration.md
harness/rules/runbooks/workflow/agent-workflow.md
harness/rules/runbooks/workflow/order-attribution-check.md
harness/rules/runbooks/workflow/product-library-backfill-check.md
harness/rules/runbooks/workflow/sample-auto-complete-check.md
harness/rules/skills/ddd/ddd-boundary-check.skill.md
harness/rules/skills/ddd/ddd-domain-optimization.skill.md
harness/rules/skills/ddd/ddd-post-task-sync.skill.md
harness/rules/skills/ddd/domain-alignment.skill.md
harness/rules/skills/ddd/order-attribution.skill.md
harness/rules/skills/ddd/performance-dashboard.skill.md
harness/rules/skills/ddd/product-library.skill.md
harness/rules/skills/ddd/real-pre-debug.skill.md
harness/rules/skills/ddd/sample-lifecycle.skill.md
harness/rules/skills/git/git-batch-submit.md
harness/rules/skills/git/git-change-control.commit.md
harness/rules/skills/git/git-change-control.exit.md
harness/rules/skills/git/git-change-control.intake.md
harness/rules/skills/git/git-change-control.md
harness/rules/skills/workflow/code-review.skill.md
harness/rules/skills/workflow/evidence-report.skill.md
harness/rules/skills/workflow/frontend-ux.skill.md
harness/rules/skills/workflow/post-task-gc.md
harness/rules/state/current-business-state.md
harness/rules/state/debts/HARNESS_DEBT.md
harness/rules/state/debts/TASK_HISTORY.md
harness/rules/state/debts/VALIDATION_STATE.md
harness/rules/state/snapshots/01-当前项目状态.md
harness/rules/state/snapshots/02-业务闭环状态.md
harness/rules/state/snapshots/03-P0-P1问题台账.md
harness/rules/state/snapshots/04-real-pre证据索引.md
harness/rules/state/snapshots/05-文档债务与冲突台账.md
harness/rules/state/snapshots/DECISIONS.md
harness/rules/state/snapshots/DEPLOYMENT_STATE.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
harness/rules/state/snapshots/KNOWN_ISSUES.md
harness/rules/style/04-doc-style-guide.md
harness/scripts/check-harness-limits.ps1
harness/scripts/commands/_lib.ps1
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/collect-evidence.ps1
harness/scripts/commands/deploy-remote.ps1
harness/scripts/commands/git-push-safe.ps1
harness/scripts/commands/new-retro.ps1
harness/scripts/commands/restart-compose.ps1
harness/scripts/commands/retire-content.ps1
harness/scripts/commands/safety-check.ps1
harness/scripts/commands/verify-local.ps1
harness/scripts/deploy-plan.md
harness/scripts/README.md
harness/scripts/README-legacy.md
harness/state/current-business-state.md
harness/state/known-risks.md
harness/state/p0-p1-register.md
harness/state/real-pre-evidence-index.md
harness/tasks/ddd-multi-agent-board.md
harness/templates/business-closure-checklist.md
harness/templates/docs-only-template.md
harness/templates/evidence-report-template.md
harness/templates/feedback-loop.md
harness/templates/garbage-collection-policy.md
harness/templates/go-live-checklist.md
harness/templates/prompts/batches/0-prepare.md
harness/templates/prompts/batches/1-facade.md
harness/templates/prompts/batches/2-policy.md
harness/templates/prompts/batches/3-replace.md
harness/templates/prompts/domain-alignment.prompt.md
harness/templates/prompts/full-review.prompt.md
harness/templates/prompts/p0-fix.prompt.md
harness/templates/prompts/real-pre-debug.prompt.md
harness/templates/prompts/release-check.prompt.md
harness/templates/retro-summary-template.md
~~~

## Git Status

~~~text
M  .gitignore
M  AGENTS.md
M  CLAUDE.md
M  backend/pom.xml
 M backend/src/main/java/com/colonel/saas/controller/SampleController.java
M  backend/src/main/java/com/colonel/saas/service/OperationLogService.java
M  backend/src/test/java/com/colonel/saas/architecture/DddConfig003ConfigRoutingTest.java
 M backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java
D  docs/01-V1交付范围与边界.md
A  docs/01-V2交付范围与边界.md
M  docs/02-V1业务流程与领域设计.md
M  docs/06-技术架构与数据模型.md
M  docs/07-部署联调与三方对接.md
M  docs/09-02-MCP订单归因接口梳理.md
M  docs/V1对齐-订单域.md
M  docs/领域/用户域.md
M  docs/领域/订单域.md
M  frontend/src/views/ops/DouyinIntegration.vue
D  harness/CURRENT_STATE.md
D  harness/HARNESS_CHANGELOG.md
M  harness/INDEX.md
M  harness/README.md
D  harness/archive/retired-content/.gitkeep
D  harness/archive/retired-content/20260602-153913/doc/计划.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-152855.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153340.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153744.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153913.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-153922.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-154011.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-155301.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-190419.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-211152.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-212757.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-214047.md
D  harness/archive/retired-content/20260603-reports-archive/content-retire-20260602-221609.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-144829.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-151552.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-151654.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-152941.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153044.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153335.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153416.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-153508.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-154039.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-154048.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-155301.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-185344.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-185438.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-190419.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-192144.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-193052.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-193606.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-194421.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-211152.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-212757.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-214047.md
D  harness/archive/retired-content/20260603-reports-archive/evidence-20260602-221622.md
D  harness/archive/retired-content/20260603-reports-archive/retire-archive-manifest-20260602-1538.json
D  harness/archive/retired-content/20260603-reports-archive/retire-delete-manifest-20260602-1538.json
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-144829.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-151603.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-151702.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-153004.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-153431.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-154053.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-154100.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-185328.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-190432.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-192207.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-193108.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-193616.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-211201.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-212809.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-214055.md
D  harness/archive/retired-content/20260603-reports-archive/retro-20260602-221636.md
D  harness/doc/00-HARNESS-README.md
D  harness/feedback/evidence-report-template.md
D  harness/feedback/retro-summary-template.md
A  harness/manifests/gc/2026-06-11-harness-gc-plan.md
A  harness/manifests/harness-gc-20260612.json
A  harness/manifests/harness-gc-plan-20260612.md
R  harness/evals/order-attribution.evals.md -> harness/probes/order-attribution.evals.md
R  harness/evals/p0-regression.evals.md -> harness/probes/p0-regression.evals.md
R  harness/evals/product-library.evals.md -> harness/probes/product-library.evals.md
R  harness/evals/rbac-scope.evals.md -> harness/probes/rbac-scope.evals.md
R  harness/evals/sample-auto-complete.evals.md -> harness/probes/sample-auto-complete.evals.md
R  harness/evals/v1-business-closure.evals.md -> harness/probes/v1-business-closure.evals.md
D  harness/prompts/agents/00-coordinator.md
D  harness/prompts/agents/01-architecture-guard.md
D  harness/prompts/agents/02-user.md
D  harness/prompts/agents/03-config.md
D  harness/prompts/agents/04-product.md
D  harness/prompts/agents/05-talent.md
D  harness/prompts/agents/06-sample.md
D  harness/prompts/agents/07-order.md
D  harness/prompts/agents/08-performance.md
D  harness/prompts/agents/09-analytics.md
D  harness/prompts/agents/10-frontend.md
D  harness/prompts/agents/11-test.md
D  harness/prompts/agents/12-infra.md
D  harness/prompts/agents/13-integration.md
D  harness/prompts/agents/14-review.md
 D harness/reports/.gitkeep
 D harness/reports/archive/20260603/b3-scope-001-batch3-scope-isolation-20260603-143131.md
 D harness/reports/archive/20260603/completion-gates-update-20260603-100557.md
 D harness/reports/archive/20260603/content-retire-20260603-093347.md
 D harness/reports/archive/20260603/content-retire-20260603-095000.md
 D harness/reports/archive/20260603/content-retire-20260603-103207.md
 D harness/reports/archive/20260603/content-retire-20260603-111343.md
 D harness/reports/archive/20260603/content-retire-20260603-113617.md
 D harness/reports/archive/20260603/content-retire-20260603-213739.md
 D harness/reports/archive/20260603/content-retire-20260603-214340.md
 D harness/reports/archive/20260603/ddd-analytics-001-event-consumer.md
 D harness/reports/archive/20260603/ddd-analytics-002-dashboard-shadow-compare.md
 D harness/reports/archive/20260603/ddd-base-001-evidence.md
 D harness/reports/archive/20260603/ddd-base-001-refactor-switches.md
 D harness/reports/archive/20260603/ddd-base-002-characterization.md
 D harness/reports/archive/20260603/ddd-base-004-package-structure.md
 D harness/reports/archive/20260603/ddd-config-001-config-domain-facade.md
 D harness/reports/archive/20260603/ddd-config-002-sample-talent-config.md
 D harness/reports/archive/20260603/ddd-config-003-performance-product-config.md
 D harness/reports/archive/20260603/ddd-config-004-config-updated-event.md
 D harness/reports/archive/20260603/ddd-dependency-map.md
 D harness/reports/archive/20260603/ddd-order-001-order-sync-application.md
 D harness/reports/archive/20260603/ddd-product-002-product-display-policy.md
 D harness/reports/archive/20260603/ddd-product-005-quick-sample-port.md
 D harness/reports/archive/20260603/ddd-refactor-plan.md
 D harness/reports/archive/20260603/ddd-sample-005-sample-query-service.md
 D harness/reports/archive/20260603/ddd-user-001-facade.md
 D harness/reports/archive/20260603/ddd-user-002-order-scope.md
 D harness/reports/archive/20260603/evidence-20260603-093411.md
 D harness/reports/archive/20260603/evidence-20260603-095000.md
 D harness/reports/archive/20260603/evidence-20260603-101503.md
 D harness/reports/archive/20260603/evidence-20260603-104232.md
 D harness/reports/archive/20260603/evidence-20260603-104601.md
 D harness/reports/archive/20260603/evidence-20260603-111733.md
 D harness/reports/archive/20260603/evidence-20260603-113632.md
 D harness/reports/archive/20260603/evidence-20260603-122021.md
 D harness/reports/archive/20260603/evidence-20260603-142506.md
 D harness/reports/archive/20260603/evidence-20260603-143000-sync-plan-001.md
 D harness/reports/archive/20260603/evidence-20260603-202253.md
 D harness/reports/archive/20260603/evidence-20260603-202320.md
 D harness/reports/archive/20260603/evidence-20260603-213739.md
 D harness/reports/archive/20260603/evidence-20260603-214340.md
 D harness/reports/archive/20260603/evidence-20260603-230334-harness-debt-governance.md
 D harness/reports/archive/20260603/func-001-product-card-hover-ui-20260603-111451.md
 D harness/reports/archive/20260603/git-batch-2-frontend-product-ui-20260603-140800.md
 D harness/reports/archive/20260603/git-batch-3-backend-user-domain-u2_5-test1-20260603-144936.md
 D harness/reports/archive/20260603/git-batch-4-reports-20260603-151500.md
 D harness/reports/archive/20260603/git-batch-c-talent-address-deploy-20260603-225500.md
 D harness/reports/archive/20260603/git-harness-001-worktree-governance-20260603-150000.md
 D harness/reports/archive/20260603/git-intake-001-dirty-classify-20260603-225000.md
 D harness/reports/archive/20260603/harness-debt-governance-inventory-20260603-230334.md
 D harness/reports/archive/20260603/harness-debt-governance-plan-20260603-230334.md
 D harness/reports/archive/20260603/order-api-6468-raw-probe-20260603-181634.md
 D harness/reports/archive/20260603/order-api-6468-verify-20260603-180500.md
 D harness/reports/archive/20260603/order-api-729-server-verify-20260603-175500.md
 D harness/reports/archive/20260603/order-api-729-verify-20260603-174500.md
 D harness/reports/archive/20260603/order-attribution-sample-20260603-222120.md
 D harness/reports/archive/20260603/order-p0-dual-source-remote-verify-20260603-205719.md
 D harness/reports/archive/20260603/order-settlement-dual-track-verify-20260603-183157.md
 D harness/reports/archive/20260603/p-diag-002-product-library-count-sync-remote-20260603-114742.md
 D harness/reports/archive/20260603/p-fix-001c-product-library-pagination-20260603-112740.md
 D harness/reports/archive/20260603/p-fix-001c-product-library-pagination-20260603-113616.md
 D harness/reports/archive/20260603/p-fix-002-config-residual-20260603-152000.md
 D harness/reports/archive/20260603/p-fix-002-product-sync-display-5min-20260603-121257.md
 D harness/reports/archive/20260603/p-fix-002a-product-sync-5min-config-20260603-120100.md
 D harness/reports/archive/20260603/p-fix-002d-real-pre-runtime-verify-20260603-123411.md
 D harness/reports/archive/20260603/p-fix-002d-remote-deploy-verify-20260603-132805.md
 D harness/reports/archive/20260603/p0-order-001-diagnosis-20260603-173500.md
 D harness/reports/archive/20260603/p0-order-001-intake-20260603-172923.md
 D harness/reports/archive/20260603/p0-order-001-real-order-visible-20260603-180450.md
 D harness/reports/archive/20260603/p0-sample-001-remote-verify-20260603-221004.md
 D harness/reports/archive/20260603/retro-20260603-093431.md
 D harness/reports/archive/20260603/retro-20260603-095028.md
 D harness/reports/archive/20260603/retro-20260603-104247.md
 D harness/reports/archive/20260603/retro-20260603-104601.md
 D harness/reports/archive/20260603/retro-20260603-111824.md
 D harness/reports/archive/20260603/retro-20260603-113645.md
 D harness/reports/archive/20260603/retro-20260603-122513.md
 D harness/reports/archive/20260603/retro-20260603-202309.md
 D harness/reports/archive/20260603/retro-20260603-202500.md
 D harness/reports/archive/20260603/retro-20260603-213756.md
 D harness/reports/archive/20260603/retro-20260603-214350.md
 D harness/reports/archive/20260603/retro-20260603-223153.md
 D harness/reports/archive/20260603/retro-20260603-230334-harness-debt-governance.md
 D harness/reports/archive/20260603/sample-apply-product-library-verify-20260603-214535.md
 D harness/reports/archive/20260603/session-exit-gate-update-20260603-101403.md
 D harness/reports/archive/20260603/sync-plan-001-batch-sync-deploy-plan-20260603-143000.md
 D harness/reports/archive/20260603/talent-address-sample-default-20260603-224000.md
 D harness/reports/archive/20260603/test-1-full-backend-failures-fix-20260603-104601.md
 D harness/reports/archive/20260603/user-domain-u1-inventory-20260603-090000.md
 D harness/reports/archive/20260603/user-domain-u1-inventory-20260603-120000.md
 D harness/reports/archive/20260603/user-domain-u2-model-schema-alignment-20260603-093000.md
 D harness/reports/archive/20260603/user-domain-u2-model-schema-alignment-20260603-150000.md
 D harness/reports/archive/20260603/user-domain-u2_5-dept-type-unification-plan-20260603-094513.md
 D harness/reports/archive/20260603/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md
 D harness/reports/archive/20260604/content-retire-20260604-001401.md
 D harness/reports/archive/20260604/content-retire-20260604-160011.md
 D harness/reports/archive/20260604/content-retire-20260604-160333.md
 D harness/reports/archive/20260604/content-retire-20260604-191102.md
 D harness/reports/archive/20260604/content-retire-20260604-195212.md
 D harness/reports/archive/20260604/content-retire-20260604-195950.md
 D harness/reports/archive/20260604/content-retire-20260604-200338.md
 D harness/reports/archive/20260604/content-retire-20260604-200908.md
 D harness/reports/archive/20260604/content-retire-20260604-202523.md
 D harness/reports/archive/20260604/content-retire-20260604-204845.md
 D harness/reports/archive/20260604/content-retire-20260604-221838.md
 D harness/reports/archive/20260604/dashboard-money-audit-001-20260604-131908.md
 D harness/reports/archive/20260604/evidence-20260604-001401-harness-debt-gc-001.md
 D harness/reports/archive/20260604/evidence-20260604-131908-dashboard-money-audit-001.md
 D harness/reports/archive/20260604/evidence-20260604-141000-order-frontend-product-info-001.md
 D harness/reports/archive/20260604/evidence-20260604-141645.md
 D harness/reports/archive/20260604/evidence-20260604-143000-order-frontend-product-info-002.md
 D harness/reports/archive/20260604/evidence-20260604-152337-order-product-info-fix.md
 D harness/reports/archive/20260604/evidence-20260604-160012.md
 D harness/reports/archive/20260604/evidence-20260604-160333.md
 D harness/reports/archive/20260604/evidence-20260604-163000-order-list-field-mapping-001.md
 D harness/reports/archive/20260604/evidence-20260604-181500-order-detail-tab.md
 D harness/reports/archive/20260604/evidence-20260604-191102.md
 D harness/reports/archive/20260604/evidence-20260604-191900-order-detail-field-align.md
 D harness/reports/archive/20260604/evidence-20260604-193000.md
 D harness/reports/archive/20260604/evidence-20260604-195212.md
 D harness/reports/archive/20260604/evidence-20260604-195950.md
 D harness/reports/archive/20260604/evidence-20260604-200338.md
 D harness/reports/archive/20260604/evidence-20260604-200908.md
 D harness/reports/archive/20260604/evidence-20260604-202523.md
 D harness/reports/archive/20260604/evidence-20260604-204437.md
 D harness/reports/archive/20260604/evidence-20260604-204846.md
 D harness/reports/archive/20260604/evidence-20260604-204902.md
 D harness/reports/archive/20260604/evidence-20260604-205824.md
 D harness/reports/archive/20260604/evidence-20260604-221838.md
 D harness/reports/archive/20260604/harness-debt-gc-001-inventory-20260604-001052.md
 D harness/reports/archive/20260604/order-detail-field-align-002-inventory-20260604-163500.md
 D harness/reports/archive/20260604/order-domain-audit-001-20260604-215143.md
 D harness/reports/archive/20260604/order-frontend-product-info-001-20260604-141000.md
 D harness/reports/archive/20260604/order-frontend-product-info-002-20260604-143000.md
 D harness/reports/archive/20260604/order-list-field-mapping-001-inventory-20260604-161000.md
 D harness/reports/archive/20260604/order-product-info-fullstack-001-20260604-160510.md
 D harness/reports/archive/20260604/order-product-info-runtime-supplement-20260604-161450.md
 D harness/reports/archive/20260604/retro-20260604-001401-harness-debt-gc-001.md
 D harness/reports/archive/20260604/retro-20260604-131908-dashboard-money-audit-001.md
 D harness/reports/archive/20260604/retro-20260604-141000-order-frontend-product-info-001.md
 D harness/reports/archive/20260604/retro-20260604-141715.md
 D harness/reports/archive/20260604/retro-20260604-143000-order-frontend-product-info-002.md
 D harness/reports/archive/20260604/retro-20260604-152337-order-product-info-fix.md
 D harness/reports/archive/20260604/retro-20260604-160034.md
 D harness/reports/archive/20260604/retro-20260604-160358.md
 D harness/reports/archive/20260604/retro-20260604-163000-order-list-field-mapping-001.md
 D harness/reports/archive/20260604/retro-20260604-191127.md
 D harness/reports/archive/20260604/retro-20260604-195234.md
 D harness/reports/archive/20260604/retro-20260604-195959.md
 D harness/reports/archive/20260604/retro-20260604-200348.md
 D harness/reports/archive/20260604/retro-20260604-200922.md
 D harness/reports/archive/20260604/retro-20260604-202533.md
 D harness/reports/archive/20260604/retro-20260604-204454.md
 D harness/reports/archive/20260604/retro-20260604-204903.md
 D harness/reports/archive/20260604/retro-20260604-205838.md
 D harness/reports/archive/20260604/retro-20260604-221901.md
 D harness/reports/archive/20260605/content-retire-20260605-105947.md
 D harness/reports/archive/20260605/dashboard-full-money-recon-001-20260605-102309.md
 D harness/reports/archive/20260605/douyin-signature-invalid-audit-001-20260605-151256.md
 D harness/reports/archive/20260605/douyin-upstream-reconnect-001-20260605-155351.md
 D harness/reports/archive/20260605/evidence-20260605-102309-dashboard-full-money-recon-001-upstream.md
 D harness/reports/archive/20260605/evidence-20260605-102309-dashboard-full-money-recon-001.md
 D harness/reports/archive/20260605/evidence-20260605-102656-dashboard-full-money-recon-001.md
 D harness/reports/archive/20260605/evidence-20260605-105947.md
 D harness/reports/archive/20260605/evidence-20260605-142932-order-field-mapping-audit-001.md
 D harness/reports/archive/20260605/evidence-20260605-151256-douyin-signature-invalid-audit-001.md
 D harness/reports/archive/20260605/evidence-20260605-155351-douyin-upstream-reconnect-001.md
 D harness/reports/archive/20260605/evidence-20260605-161245-order-performance-missing-audit-001.md
 D harness/reports/archive/20260605/evidence-20260605-DASH-RECON-P0-007-summary-cache.md
 D harness/reports/archive/20260605/order-detail-tab-fix-001-20260605-100305.md
 D harness/reports/archive/20260605/order-field-mapping-audit-001-20260605-142932.md
 D harness/reports/archive/20260605/order-performance-missing-audit-001-20260605-161245.md
 D harness/reports/archive/20260605/retro-20260605-110019.md
 D harness/reports/archive/20260605/retro-20260605-151256-douyin-signature-invalid-audit-001.md
 D harness/reports/archive/20260605/retro-20260605-155351-douyin-upstream-reconnect-001.md
 D harness/reports/archive/20260605/retro-20260605-161245-order-performance-missing-audit-001.md
 D harness/reports/archive/20260606/content-retire-20260606-120813.md
 D harness/reports/archive/20260606/content-retire-20260606-135356.md
 D harness/reports/archive/20260606/content-retire-20260606-140450.md
 D harness/reports/archive/20260606/content-retire-20260606-142323.md
 D harness/reports/archive/20260606/content-retire-20260606-144015.md
 D harness/reports/archive/20260606/content-retire-20260606-145226.md
 D harness/reports/archive/20260606/content-retire-20260606-152446.md
 D harness/reports/archive/20260606/content-retire-20260606-153311.md
 D harness/reports/archive/20260606/content-retire-20260606-163231.md
 D harness/reports/archive/20260606/content-retire-20260606-180518.md
 D harness/reports/archive/20260606/content-retire-20260606-195517.md
 D harness/reports/archive/20260606/content-retire-20260606-212835.md
 D harness/reports/archive/20260606/dashboard-baseline-recon-3739-001-20260606-130937.md
 D harness/reports/archive/20260606/dashboard-money-formula-baseline-6557-001-20260606-200100.md
 D harness/reports/archive/20260606/dashboard-money-hidden-deduction-8291-001-20260606-220500.md
 D harness/reports/archive/20260606/dashboard-today-snapshot-recon-001-20260606-154416.md
 D harness/reports/archive/20260606/dashboard-today-snapshot-recon-001-20260606-154539.md
 D harness/reports/archive/20260606/douyin-env-secret-runtime-reload-20260606-201929.md
 D harness/reports/archive/20260606/evidence-20260606-120829.md
 D harness/reports/archive/20260606/evidence-20260606-121045.md
 D harness/reports/archive/20260606/evidence-20260606-121756-order-performance-backfill-001.md
 D harness/reports/archive/20260606/evidence-20260606-122912.md
 D harness/reports/archive/20260606/evidence-20260606-123500-channel-commission-label-rename-001.md
 D harness/reports/archive/20260606/evidence-20260606-125134-douyin-upstream-reconnect-local-001.md
 D harness/reports/archive/20260606/evidence-20260606-130937-dashboard-baseline-recon-3739-001.md
 D harness/reports/archive/20260606/evidence-20260606-132002-dashboard-baseline-recon-3739-001-addendum.md
 D harness/reports/archive/20260606/evidence-20260606-133127-order-history-backfill-001.md
 D harness/reports/archive/20260606/evidence-20260606-135356.md
 D harness/reports/archive/20260606/evidence-20260606-140450.md
 D harness/reports/archive/20260606/evidence-20260606-141942.md
 D harness/reports/archive/20260606/evidence-20260606-142323.md
 D harness/reports/archive/20260606/evidence-20260606-144015.md
 D harness/reports/archive/20260606/evidence-20260606-145227.md
 D harness/reports/archive/20260606/evidence-20260606-151400-order-6468-pagination-fix-001.md
 D harness/reports/archive/20260606/evidence-20260606-152447.md
 D harness/reports/archive/20260606/evidence-20260606-153131.md
 D harness/reports/archive/20260606/evidence-20260606-153311.md
 D harness/reports/archive/20260606/evidence-20260606-153950-order-6468-pagination-fix-001.md
 D harness/reports/archive/20260606/evidence-20260606-154416-dashboard-today-snapshot-recon-001.md
 D harness/reports/archive/20260606/evidence-20260606-154539-dashboard-today-snapshot-recon-001.md
 D harness/reports/archive/20260606/evidence-20260606-161412.md
 D harness/reports/archive/20260606/evidence-20260606-163046.md
 D harness/reports/archive/20260606/evidence-20260606-163231.md
 D harness/reports/archive/20260606/evidence-20260606-163233.md
 D harness/reports/archive/20260606/evidence-20260606-164000-dash-money-drift-fix-001.md
 D harness/reports/archive/20260606/evidence-20260606-164500-order-sync-freshness-optimize-001.md
 D harness/reports/archive/20260606/evidence-20260606-165000-order-sync-freshness-optimize-001.md
 D harness/reports/archive/20260606/evidence-20260606-171312-douyin-secret-runtime-verify-002.md
 D harness/reports/archive/20260606/evidence-20260606-173646-service-fee-income-rule-audit.md
 D harness/reports/archive/20260606/evidence-20260606-174315-service-fee-profit-rule-audit.md
 D harness/reports/archive/20260606/evidence-20260606-180600.md
 D harness/reports/archive/20260606/evidence-20260606-195517.md
 D harness/reports/archive/20260606/evidence-20260606-195538.md
 D harness/reports/archive/20260606/evidence-20260606-200100-dashboard-money-formula-baseline-6557-001.md
 D harness/reports/archive/20260606/evidence-20260606-200650-dashboard-money-formula-baseline-6557-001.md
 D harness/reports/archive/20260606/evidence-20260606-202013.md
 D harness/reports/archive/20260606/evidence-20260606-212835.md
 D harness/reports/archive/20260606/evidence-20260606-213110-service-fee-income-expense-fix.md
 D harness/reports/archive/20260606/evidence-20260606-220500-dashboard-money-hidden-deduction-8291-001.md
 D harness/reports/archive/20260606/order-6468-pagination-dryrun-001-20260606-140607.md
 D harness/reports/archive/20260606/order-6468-pagination-fix-001-20260606-151400.md
 D harness/reports/archive/20260606/order-6468-pagination-fix-001-20260606-153950.md
 D harness/reports/archive/20260606/order-history-backfill-001-20260606-133127.md
 D harness/reports/archive/20260606/order-performance-event-after-commit-fix-001-20260606-121000.md
 D harness/reports/archive/20260606/order-sync-freshness-optimize-001-20260606-164500.md
 D harness/reports/archive/20260606/order-sync-freshness-optimize-001-20260606-165000.md
 D harness/reports/archive/20260606/retro-20260606-120843.md
 D harness/reports/archive/20260606/retro-20260606-122926.md
 D harness/reports/archive/20260606/retro-20260606-125134-douyin-upstream-reconnect-local-001.md
 D harness/reports/archive/20260606/retro-20260606-135422.md
 D harness/reports/archive/20260606/retro-20260606-140512.md
 D harness/reports/archive/20260606/retro-20260606-142345.md
 D harness/reports/archive/20260606/retro-20260606-144037.md
 D harness/reports/archive/20260606/retro-20260606-145254.md
 D harness/reports/archive/20260606/retro-20260606-151400-order-6468-pagination-fix-001.md
 D harness/reports/archive/20260606/retro-20260606-152506.md
 D harness/reports/archive/20260606/retro-20260606-153330.md
 D harness/reports/archive/20260606/retro-20260606-153400-order-recent-days-popup.md
 D harness/reports/archive/20260606/retro-20260606-153950-order-6468-pagination-fix-001.md
 D harness/reports/archive/20260606/retro-20260606-161441.md
 D harness/reports/archive/20260606/retro-20260606-164500-order-sync-freshness-optimize-001.md
 D harness/reports/archive/20260606/retro-20260606-165000-order-sync-freshness-optimize-001.md
 D harness/reports/archive/20260606/retro-20260606-180615.md
 D harness/reports/archive/20260606/retro-20260606-200100-dashboard-money-formula-baseline-6557-001.md
 D harness/reports/archive/20260606/retro-20260606-200650-dashboard-money-formula-baseline-6557-001.md
 D harness/reports/archive/20260606/retro-20260606-202028.md
 D harness/reports/archive/20260606/retro-20260606-212859.md
 D harness/reports/archive/20260606/retro-20260606-220500-dashboard-money-hidden-deduction-8291-001.md
 D harness/reports/archive/20260607/SECURITY-INCIDENT-001-20260607-115744.md
 D harness/reports/archive/20260607/SECURITY-INCIDENT-001-FINAL-PAUSE-20260607-115800.md
 D harness/reports/archive/20260607/SECURITY-INCIDENT-001-FORENSIC-20260607-132211.md
 D harness/reports/archive/20260607/content-retire-20260607-100701.md
 D harness/reports/archive/20260607/content-retire-20260607-102505.md
 D harness/reports/archive/20260607/dashboard-money-local-undercount-7670-001-20260607-095819.md
 D harness/reports/archive/20260607/evidence-20260607-095819.md
 D harness/reports/archive/20260607/evidence-20260607-100701.md
 D harness/reports/archive/20260607/evidence-20260607-100703.md
 D harness/reports/archive/20260607/evidence-20260607-102505.md
 D harness/reports/archive/20260607/evidence-20260607-151000.md
 D harness/reports/archive/20260607/remote-user-manual-001-20260607-200000.md
 D harness/reports/archive/20260607/retro-20260607-095819.md
 D harness/reports/archive/20260607/retro-20260607-102529.md
 D harness/reports/archive/20260608/content-retire-20260608-144219.md
 D harness/reports/archive/20260608/content-retire-20260608-144245.md
 D harness/reports/archive/20260608/content-retire-20260608-145206.md
 D harness/reports/archive/20260608/content-retire-20260608-150249.md
 D harness/reports/archive/20260608/content-retire-20260608-151615.md
 D harness/reports/archive/20260608/ddd-audit-config-001-20260608-153000.md
 D harness/reports/archive/20260608/ddd-audit-cross-domain-001-20260608-144000.md
 D harness/reports/archive/20260608/ddd-audit-order-001-20260608-145000.md
 D harness/reports/archive/20260608/ddd-audit-performance-001-20260608-150000.md
 D harness/reports/archive/20260608/ddd-audit-product-001-20260608-170000.md
 D harness/reports/archive/20260608/ddd-audit-sample-001-20260608-151500.md
 D harness/reports/archive/20260608/ddd-audit-user-001-20260608-160500.md
 D harness/reports/archive/20260608/ddd-refactor-master-plan-001-20260608-135908.md
 D harness/reports/archive/20260608/evidence-20260608-135908-ddd-refactor-master-plan-001.md
 D harness/reports/archive/20260608/evidence-20260608-140000-harness-kb-001.md
 D harness/reports/archive/20260608/evidence-20260608-144000-ddd-audit-cross-domain-001.md
 D harness/reports/archive/20260608/evidence-20260608-144219.md
 D harness/reports/archive/20260608/evidence-20260608-144223.md
 D harness/reports/archive/20260608/evidence-20260608-144245.md
 D harness/reports/archive/20260608/evidence-20260608-145000-ddd-audit-order-001.md
 D harness/reports/archive/20260608/evidence-20260608-145206.md
 D harness/reports/archive/20260608/evidence-20260608-150000-ddd-audit-performance-001.md
 D harness/reports/archive/20260608/evidence-20260608-150000-harness-kb-count-fix-001.md
 D harness/reports/archive/20260608/evidence-20260608-150249.md
 D harness/reports/archive/20260608/evidence-20260608-150646.md
 D harness/reports/archive/20260608/evidence-20260608-151500-ddd-audit-sample-001.md
 D harness/reports/archive/20260608/evidence-20260608-151615.md
 D harness/reports/archive/20260608/evidence-20260608-153000-ddd-audit-config-001.md
 D harness/reports/archive/20260608/evidence-20260608-160500-ddd-audit-user-001.md
 D harness/reports/archive/20260608/evidence-20260608-170000-ddd-audit-product-001.md
 D harness/reports/archive/20260608/harness-kb-001-20260608-140000.md
 D harness/reports/archive/20260608/retro-20260608-135908-ddd-refactor-master-plan-001.md
 D harness/reports/archive/20260608/retro-20260608-140000-harness-kb-001.md
 D harness/reports/archive/20260608/retro-20260608-144000-ddd-audit-cross-domain-001.md
 D harness/reports/archive/20260608/retro-20260608-144324.md
 D harness/reports/archive/20260608/retro-20260608-145000-ddd-audit-order-001.md
 D harness/reports/archive/20260608/retro-20260608-145220.md
 D harness/reports/archive/20260608/retro-20260608-150000-ddd-audit-performance-001.md
 D harness/reports/archive/20260608/retro-20260608-151500-ddd-audit-sample-001.md
 D harness/reports/archive/20260608/retro-20260608-151631.md
 D harness/reports/archive/20260608/retro-20260608-153000-ddd-audit-config-001.md
 D harness/reports/archive/20260608/retro-20260608-160500-ddd-audit-user-001.md
 D harness/reports/archive/20260608/retro-20260608-170000-ddd-audit-product-001.md
 D harness/reports/archive/20260609/content-retire-20260609-162018.md
 D harness/reports/archive/20260609/content-retire-20260609-185256.md
 D harness/reports/archive/20260609/dashboard-metric-copy-fix-20260609-132430.md
 D harness/reports/archive/20260609/dashboard-today-metric-reconcile-20260609-125500.md
 D harness/reports/archive/20260609/ddd-audit-analysis-001-20260609-090500.md
 D harness/reports/archive/20260609/ddd-audit-talent-001-20260609-093000.md
 D harness/reports/archive/20260609/ddd-base-001-safety-toggles-20260609-120500.md
 D harness/reports/archive/20260609/ddd-phase0-arbitration-report-20260609-110000.md
 D harness/reports/archive/20260609/ddd-phase0-audit-status-sync-001-20260609-101500.md
 D harness/reports/archive/20260609/evidence-20260609-090500-ddd-audit-analysis-001.md
 D harness/reports/archive/20260609/evidence-20260609-093000-ddd-audit-talent-001.md
 D harness/reports/archive/20260609/evidence-20260609-101500-ddd-phase0-audit-status-sync-001.md
 D harness/reports/archive/20260609/evidence-20260609-110000-ddd-phase0-arbitration-report.md
 D harness/reports/archive/20260609/evidence-20260609-120500-ddd-base-001.md
 D harness/reports/archive/20260609/evidence-20260609-125500-dashboard-today-metric-reconcile.md
 D harness/reports/archive/20260609/evidence-20260609-132352.md
 D harness/reports/archive/20260609/evidence-20260609-161850.md
 D harness/reports/archive/20260609/evidence-20260609-161916.md
 D harness/reports/archive/20260609/evidence-20260609-162018.md
 D harness/reports/archive/20260609/evidence-20260609-185232.md
 D harness/reports/archive/20260609/evidence-20260609-185256.md
 D harness/reports/archive/20260609/evidence-20260609-185304.md
 D harness/reports/archive/20260609/retro-20260609-090500-ddd-audit-analysis-001.md
 D harness/reports/archive/20260609/retro-20260609-093000-ddd-audit-talent-001.md
 D harness/reports/archive/20260609/retro-20260609-101500-ddd-phase0-audit-status-sync-001.md
 D harness/reports/archive/20260609/retro-20260609-110000-ddd-phase0-arbitration-report.md
 D harness/reports/archive/20260609/retro-20260609-120500-ddd-base-001.md
 D harness/reports/archive/20260609/retro-20260609-132414.md
 D harness/reports/archive/20260609/retro-20260609-162122.md
 D harness/reports/archive/20260609/settle-dashboard-empty-diagnosis-20260609-134601.md
 D harness/reports/archive/20260609/settlement-api-audit-20260609-154907.md
 D harness/reports/archive/20260609/settlement-api-audit-20260609-160403.md
 D harness/reports/archive/20260609/settlement-time-type-fix-20260609-162400.md
 D harness/reports/archive/20260609/settlement-upstream-verification-material-20260609-160932.md
 D harness/reports/archive/20260610/content-retire-20260610-100225.md
 D harness/reports/archive/20260610/content-retire-20260610-100438.md
 D harness/reports/archive/20260610/content-retire-20260610-100647.md
 D harness/reports/archive/20260610/content-retire-20260610-100917.md
 D harness/reports/archive/20260610/content-retire-20260610-101145.md
 D harness/reports/archive/20260610/content-retire-20260610-101445.md
 D harness/reports/archive/20260610/content-retire-20260610-101840.md
 D harness/reports/archive/20260610/evidence-20260610-095712.md
 D harness/reports/archive/20260610/evidence-20260610-100000-ddd-base-002.md
 D harness/reports/archive/20260610/evidence-20260610-100225.md
 D harness/reports/archive/20260610/evidence-20260610-100234.md
 D harness/reports/archive/20260610/evidence-20260610-100439.md
 D harness/reports/archive/20260610/evidence-20260610-100452.md
 D harness/reports/archive/20260610/evidence-20260610-100648.md
 D harness/reports/archive/20260610/evidence-20260610-100708.md
 D harness/reports/archive/20260610/evidence-20260610-100917.md
 D harness/reports/archive/20260610/evidence-20260610-100932.md
 D harness/reports/archive/20260610/evidence-20260610-101145.md
 D harness/reports/archive/20260610/evidence-20260610-101230.md
 D harness/reports/archive/20260610/evidence-20260610-101445.md
 D harness/reports/archive/20260610/evidence-20260610-101607.md
 D harness/reports/archive/20260610/evidence-20260610-101840.md
 D harness/reports/archive/20260610/evidence-20260610-134000-ddd-base-003.md
 D harness/reports/archive/20260610/evidence-20260610-134959.md
 D harness/reports/archive/20260610/evidence-20260610-135134.md
 D harness/reports/archive/20260610/evidence-20260610-135150.md
 D harness/reports/archive/20260610/evidence-20260610-140205.md
 D harness/reports/archive/20260610/evidence-20260610-140259.md
 D harness/reports/archive/20260610/evidence-20260610-163007.md
 D harness/reports/archive/20260610/evidence-20260610-ddd-user-001.md
 D harness/reports/archive/20260610/evidence-20260610-phase0-ddd-base.md
 D harness/reports/archive/20260610/multi-agent-ddd-prompts-full.md
 D harness/reports/archive/20260610/retro-20260610-100000-ddd-base-002.md
 D harness/reports/archive/20260610/retro-20260610-102052.md
 D harness/reports/content-retire-20260610-202445.md
 D harness/reports/current/harness-doc-gc-optimize-001-final.md
 D harness/reports/ddd-order-002-amount-policy-20260612.md
 D harness/reports/ddd-product-001-facade-20260612.md
 D harness/reports/ddd-sample-006-state-machine-20260612.md
 D harness/reports/ddd-talent-001-facade-20260612.md
 D harness/reports/ddd-talent-002-claim-policy-20260612.md
 D harness/reports/ddd-user-003-facade-consumption-20260612.md
 D harness/reports/evidence-20260610-202445.md
 D harness/reports/harness-doc-gc-optimize-001-status-before.md
 D harness/reports/retro-20260612-batch2-policy-done.md
A  harness/rules/changelog.md
R  harness/environment/CHEATSHEET.md -> harness/rules/environment/CHEATSHEET.md
R  harness/environment/README.md -> harness/rules/environment/README.md
R  harness/doc/03-environment/01-test环境说明.md -> harness/rules/environment/envs/01-test环境说明.md
R  harness/doc/03-environment/02-real-pre环境说明.md -> harness/rules/environment/envs/02-real-pre环境说明.md
R  harness/doc/03-environment/03-remote-real-pre部署说明.md -> harness/rules/environment/envs/03-remote-real-pre部署说明.md
R  harness/doc/03-environment/04-Docker-Compose服务地图.md -> harness/rules/environment/envs/04-Docker-Compose服务地图.md
R  harness/environment/docker-compose-map.md -> harness/rules/environment/envs/docker-compose-map.md
R  harness/environment/local-dev-env.md -> harness/rules/environment/envs/local-dev-env.md
R  harness/environment/real-pre-env.md -> harness/rules/environment/envs/real-pre-env.md
R  harness/environment/remote-real-pre-env.md -> harness/rules/environment/envs/remote-real-pre-env.md
R  harness/environment/test-env.md -> harness/rules/environment/envs/test-env.md
R  harness/doc/05-feedback/05-Harness持续迭代规则.md -> harness/rules/feedback/iteration.md
R  harness/doc/05-feedback/06-旧内容生命周期规则.md -> harness/rules/feedback/retire.md
A  harness/rules/file-retention-policy.md
R  harness/COMPLETION_GATES.md -> harness/rules/governance/COMPLETION_GATES.md
R  harness/completion-gates-detail.md -> harness/rules/governance/completion-gates-detail.md
R  harness/completion-gates-git.md -> harness/rules/governance/completion-gates-git.md
R  harness/DOMAIN_MAP.md -> harness/rules/governance/domains-map.md
R  harness/FORBIDDEN_SCOPE.md -> harness/rules/governance/forbidden-scope.md
R  harness/QUALITY_LEDGER.md -> harness/rules/governance/quality-ledger.md
R  harness/SESSION_EXIT_GATE.md -> harness/rules/governance/session-exit-gate.md
R  harness/TASK_ROUTING.md -> harness/rules/governance/task-routing.md
A  harness/rules/harness-structure-policy.md
R  harness/instructions/analytics-module.md -> harness/rules/instructions/domain/analytics-module.md
R  harness/instructions/config-domain.md -> harness/rules/instructions/domain/config-domain.md
R  harness/instructions/order-domain.md -> harness/rules/instructions/domain/order-domain.md
R  harness/instructions/performance-domain.md -> harness/rules/instructions/domain/performance-domain.md
R  harness/instructions/product-domain.md -> harness/rules/instructions/domain/product-domain.md
R  harness/instructions/sample-domain.md -> harness/rules/instructions/domain/sample-domain.md
R  harness/instructions/talent-domain.md -> harness/rules/instructions/domain/talent-domain.md
R  harness/instructions/user-domain.md -> harness/rules/instructions/domain/user-domain.md
R  harness/doc/01-instructions/01-项目执行协议.md -> harness/rules/instructions/governance/01-项目执行协议.md
R  harness/doc/01-instructions/02-V1交付合同.md -> harness/rules/instructions/governance/02-V1交付合同.md
R  harness/doc/01-instructions/03-禁止范围与安全边界.md -> harness/rules/instructions/governance/03-禁止范围与安全边界.md
R  harness/doc/01-instructions/04-文档优先级与冲突处理.md -> harness/rules/instructions/governance/04-文档优先级与冲突处理.md
R  harness/doc/01-instructions/05-领域边界总表.md -> harness/rules/instructions/governance/05-领域边界总表.md
R  harness/instructions/definition-of-done.md -> harness/rules/instructions/governance/definition-of-done.md
R  harness/instructions/document-priority.md -> harness/rules/instructions/governance/document-priority.md
R  harness/instructions/multi-agent-ddd-prompts.md -> harness/rules/instructions/governance/multi-agent-ddd-prompts.md
R  harness/instructions/safety-rules.md -> harness/rules/instructions/governance/safety-rules.md
R  harness/instructions/v1-business-contract.md -> harness/rules/instructions/governance/v1-business-contract.md
R  harness/agent-locks/DDD-SAMPLE-005-FIX-sample-agent.lock.md -> harness/rules/locks/DDD-SAMPLE-005-FIX-sample-agent.lock.md
R  harness/agent-locks/LOCK_INDEX.md -> harness/rules/locks/INDEX.md
R  harness/AGENT_CONTRACT.md -> harness/rules/policies/agent-contract.md
A  harness/rules/policies/report-style-policy.md
A  harness/rules/policies/retention-policy.md
A  harness/rules/policies/structure-policy.md
A  harness/rules/report-style-policy.md
R  harness/runbooks/backend-change.md -> harness/rules/runbooks/backend-change.md
R  harness/runbooks/database-change.md -> harness/rules/runbooks/database-change.md
R  harness/plans/DDD_DOMAIN_TASK_MATRIX.md -> harness/rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX.md
R  harness/plans/DDD_OPTIMIZATION_ROADMAP.md -> harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md
R  harness/plans/HARNESS_ITERATION_ROADMAP.md -> harness/rules/runbooks/ddd/HARNESS_ITERATION_ROADMAP.md
R  harness/runbooks/frontend-change.md -> harness/rules/runbooks/frontend-change.md
R  harness/runbooks/closeout-and-gc.md -> harness/rules/runbooks/governance/closeout-and-gc.md
R  harness/runbooks/debt-governance.md -> harness/rules/runbooks/governance/debt-governance.md
R  harness/runbooks/docker-compose-operations.md -> harness/rules/runbooks/governance/docker-compose-operations.md
R  harness/runbooks/scope-command-matrix.md -> harness/rules/runbooks/governance/scope-command-matrix.md
R  harness/runbooks/task-lifecycle.md -> harness/rules/runbooks/governance/task-lifecycle.md
R  harness/runbooks/test-validation.md -> harness/rules/runbooks/governance/test-validation.md
R  harness/runbooks/real-pre-change.md -> harness/rules/runbooks/real-pre-change.md
R  harness/runbooks/remote-deploy.md -> harness/rules/runbooks/remote-deploy.md
R  harness/runbooks/rollback.md -> harness/rules/runbooks/rollback.md
R  harness/runbooks/third-party-integration.md -> harness/rules/runbooks/third-party-integration.md
R  harness/doc/02-tools/02-后续Agent默认执行流程.md -> harness/rules/runbooks/workflow/agent-workflow.md
R  harness/runbooks/order-attribution-check.md -> harness/rules/runbooks/workflow/order-attribution-check.md
R  harness/runbooks/product-library-backfill-check.md -> harness/rules/runbooks/workflow/product-library-backfill-check.md
R  harness/runbooks/sample-auto-complete-check.md -> harness/rules/runbooks/workflow/sample-auto-complete-check.md
R  harness/skills/ddd-boundary-check.skill.md -> harness/rules/skills/ddd/ddd-boundary-check.skill.md
R  harness/skills/ddd-domain-optimization.skill.md -> harness/rules/skills/ddd/ddd-domain-optimization.skill.md
R  harness/skills/ddd-post-task-sync.skill.md -> harness/rules/skills/ddd/ddd-post-task-sync.skill.md
R  harness/skills/domain-alignment.skill.md -> harness/rules/skills/ddd/domain-alignment.skill.md
R  harness/skills/order-attribution.skill.md -> harness/rules/skills/ddd/order-attribution.skill.md
R  harness/skills/performance-dashboard.skill.md -> harness/rules/skills/ddd/performance-dashboard.skill.md
R  harness/skills/product-library.skill.md -> harness/rules/skills/ddd/product-library.skill.md
R  harness/skills/real-pre-debug.skill.md -> harness/rules/skills/ddd/real-pre-debug.skill.md
R  harness/skills/sample-lifecycle.skill.md -> harness/rules/skills/ddd/sample-lifecycle.skill.md
R  harness/skills/git-batch-submit.md -> harness/rules/skills/git/git-batch-submit.md
R  harness/skills/git-change-control.commit.md -> harness/rules/skills/git/git-change-control.commit.md
R  harness/skills/git-change-control.exit.md -> harness/rules/skills/git/git-change-control.exit.md
R  harness/skills/git-change-control.intake.md -> harness/rules/skills/git/git-change-control.intake.md
R  harness/skills/git-change-control.md -> harness/rules/skills/git/git-change-control.md
R  harness/skills/code-review.skill.md -> harness/rules/skills/workflow/code-review.skill.md
R  harness/skills/evidence-report.skill.md -> harness/rules/skills/workflow/evidence-report.skill.md
R  harness/skills/frontend-ux.skill.md -> harness/rules/skills/workflow/frontend-ux.skill.md
R  harness/skills/post-task-gc.md -> harness/rules/skills/workflow/post-task-gc.md
A  harness/rules/state/current-business-state.md
R  harness/state/HARNESS_DEBT.md -> harness/rules/state/debts/HARNESS_DEBT.md
R  harness/state/TASK_HISTORY.md -> harness/rules/state/debts/TASK_HISTORY.md
R  harness/state/VALIDATION_STATE.md -> harness/rules/state/debts/VALIDATION_STATE.md
R  harness/doc/04-state/01-当前项目状态.md -> harness/rules/state/snapshots/01-当前项目状态.md
R  harness/doc/04-state/02-业务闭环状态.md -> harness/rules/state/snapshots/02-业务闭环状态.md
R  harness/doc/04-state/03-P0-P1问题台账.md -> harness/rules/state/snapshots/03-P0-P1问题台账.md
R  harness/doc/04-state/04-real-pre证据索引.md -> harness/rules/state/snapshots/04-real-pre证据索引.md
R  harness/doc/04-state/05-文档债务与冲突台账.md -> harness/rules/state/snapshots/05-文档债务与冲突台账.md
R  harness/state/DECISIONS.md -> harness/rules/state/snapshots/DECISIONS.md
R  harness/state/DEPLOYMENT_STATE.md -> harness/rules/state/snapshots/DEPLOYMENT_STATE.md
R  harness/state/DOMAIN_STATUS.md -> harness/rules/state/snapshots/DOMAIN_STATUS.md
R  harness/state/KNOWN_ISSUES.md -> harness/rules/state/snapshots/KNOWN_ISSUES.md
R  harness/core/04-doc-style-guide.md -> harness/rules/style/04-doc-style-guide.md
R  harness/tools/README.md -> harness/scripts/README-legacy.md
R  harness/doc/02-tools/01-现有脚本与命令索引.md -> harness/scripts/README.md
AM harness/scripts/check-harness-limits.ps1
R  harness/commands/_lib.ps1 -> harness/scripts/commands/_lib.ps1
R  harness/commands/agent-do.ps1 -> harness/scripts/commands/agent-do.ps1
R  harness/commands/collect-evidence.ps1 -> harness/scripts/commands/collect-evidence.ps1
R  harness/commands/deploy-remote.ps1 -> harness/scripts/commands/deploy-remote.ps1
R  harness/commands/git-push-safe.ps1 -> harness/scripts/commands/git-push-safe.ps1
R  harness/commands/new-retro.ps1 -> harness/scripts/commands/new-retro.ps1
R  harness/commands/restart-compose.ps1 -> harness/scripts/commands/restart-compose.ps1
R  harness/commands/retire-content.ps1 -> harness/scripts/commands/retire-content.ps1
R  harness/commands/safety-check.ps1 -> harness/scripts/commands/safety-check.ps1
R  harness/commands/verify-local.ps1 -> harness/scripts/commands/verify-local.ps1
R  harness/doc/02-tools/03-构建重启部署命令规划.md -> harness/scripts/deploy-plan.md
D  harness/state/current-business-state.md
D  harness/state/known-risks.md
D  harness/state/p0-p1-register.md
D  harness/state/real-pre-evidence-index.md
M  harness/tasks/ddd-multi-agent-board.md
R  harness/doc/05-feedback/02-业务闭环验证清单.md -> harness/templates/business-closure-checklist.md
R  harness/feedback/docs-only-template.md -> harness/templates/docs-only-template.md
R  harness/doc/05-feedback/03-任务完成证据报告模板.md -> harness/templates/evidence-report-template.md
R  harness/feedback/feedback-loop.md -> harness/templates/feedback-loop.md
R  harness/feedback/garbage-collection-policy.md -> harness/templates/garbage-collection-policy.md
R  harness/doc/05-feedback/01-上线验收清单.md -> harness/templates/go-live-checklist.md
R  harness/prompts/batches/0-prepare.md -> harness/templates/prompts/batches/0-prepare.md
R  harness/prompts/batches/1-facade.md -> harness/templates/prompts/batches/1-facade.md
R  harness/prompts/batches/2-policy.md -> harness/templates/prompts/batches/2-policy.md
R  harness/prompts/batches/3-replace.md -> harness/templates/prompts/batches/3-replace.md
R  harness/prompts/domain-alignment.prompt.md -> harness/templates/prompts/domain-alignment.prompt.md
R  harness/prompts/full-review.prompt.md -> harness/templates/prompts/full-review.prompt.md
R  harness/prompts/p0-fix.prompt.md -> harness/templates/prompts/p0-fix.prompt.md
R  harness/prompts/real-pre-debug.prompt.md -> harness/templates/prompts/real-pre-debug.prompt.md
R  harness/prompts/release-check.prompt.md -> harness/templates/prompts/release-check.prompt.md
R  harness/doc/05-feedback/04-任务后复盘模板.md -> harness/templates/retro-summary-template.md
?? backend/src/main/java/com/colonel/saas/domain/sample/application/SampleCommandApplicationService.java
?? backend/src/main/java/com/colonel/saas/domain/sample/application/SampleQueryApplicationService.java
?? backend/src/main/java/com/colonel/saas/domain/sample/facade/LegacySampleDomainFacade.java
?? backend/src/main/java/com/colonel/saas/domain/sample/facade/SampleDomainFacade.java
?? backend/src/test/java/com/colonel/saas/architecture/DddSample007SampleRoutingTest.java
?? backend/src/test/java/com/colonel/saas/domain/sample/facade/
?? harness/reports/content-retire-20260612-130918.md
?? harness/reports/ddd-sample-007-facade-routing-20260612.md
?? harness/reports/latest-evidence-20260612.md
?? harness/reports/latest-harness-gc-report.md
?? harness/reports/latest-harness-inventory.md
?? harness/reports/latest-harness-limits-check.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

### docker compose ps

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    27 seconds ago   Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   45 minutes ago   Up 45 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   41 hours ago     Up 46 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      6 days ago       Up 46 minutes (healthy)   6379/tcp
~~~

### docker ps

~~~text
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 45 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 46 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 46 minutes (healthy)   6379/tcp
saas-test-backend-1               Up 46 minutes (healthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 46 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 46 minutes (healthy)   6379/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Conclusion

PASS

## Residual Risk

- Items marked as not collected are not proof of success.
- If real-pre lacks real orders or pick_source samples, record the result as PENDING or PARTIAL.
