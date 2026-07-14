# Evidence Report

## Metadata

- Time: 2026-07-14 17:31:19 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Commit: 3e1c02e611a08138d448da924bea6509fd7cf2e5
- Owned worktree: dirty (保留用户既有未提交改动)
- Deploy remote: true

## Owned Files

~~~text
frontend/src/plugins/naive-product-components.ts
frontend/src/plugins/naive-product-components.test.ts
~~~

## Root Cause Evidence

~~~text
远端部署前，真实商品编辑抽屉中的“开始时间/结束时间”标签存在，但 DOM 中对应节点是未注册的 <n-date-picker> 原生标签；实际 Naive UI 日期控件未渲染。
原因：product 路由组件注册清单遗漏 NDatePicker。ProductEditModal 单测使用了 NDatePicker stub，因此原有单测未覆盖全局注册缺口。
~~~

## Change

~~~text
在 frontend/src/plugins/naive-product-components.ts 注册 NDatePicker；新增注册回归测试，防止商品路由再次遗漏日期控件。
~~~

## Build and Test Result

~~~text
PASS: frontend targeted Vitest, 2 files / 4 tests passed.
PASS: frontend typecheck.
PASS: frontend production build.
历史基线：商品时间字段与后端保存逻辑的既有 targeted/full tests 已通过；本次修复未修改后端逻辑。
~~~

## Remote Deploy Result

~~~text
PASS: fixed real-pre deployment entry completed.
Remote HEAD: 3e1c02e6.
Remote backend Maven package: BUILD SUCCESS.
Remote frontend pnpm build: success.
Remote backend/frontend containers rebuilt and restarted.
Remote backend health: HTTP 200, {"status":"UP"}.
Remote frontend asset contains the product-route NDatePicker registration bundle.
Remote real-pre preflight: PASS; canRunBusinessFlows=true.
~~~

## Business Validation Result

~~~text
PASS: server-local Chromium targeted real-pre product edit flow.
商品编辑按钮：3 个可见。
日期控件：datePickerCount=2；nativeDateTagCount=0。
开始时间输入：2026-05-07 00:00:00。
结束时间输入：2027-05-31 23:59:59。
点击开始时间后日历弹层出现，popupHasCalendar=true。
控制台错误：0；失败请求：0。
未点击保存，未修改真实商品数据。
说明：服务器本机浏览器通过无 Origin 的 API 代理完成 real-pre 页面验收，避免 localhost Origin 被后端安全策略拒绝；页面和构建产物仍来自已部署容器。
~~~

## P0 Context

~~~text
完整 real-pre P0 不作为本功能 PASS 依据：最新 P0 的商品链步骤通过，但总体因抖店接入回归 FAIL、清理计划 FAIL，以及订单/寄样真实前置数据不足而 FAIL/PENDING；这些不是本次日期控件修复的失败证据。
~~~

## Retro Summary

本次根因是路由级 Naive UI 注册清单遗漏，且组件单测 stub 掩盖了运行时注册缺口。已新增注册回归测试，并在部署后执行服务器本机真实页面验收，后续涉及全局组件注册的改动必须同时做实际路由页面检查。

## Conclusion

PASS

## Residual Risk

- 用户当前浏览器若仍缓存旧 index/JS，需要强制刷新后再进入商品编辑；新部署已生成新的前端资源 hash。
- 完整 real-pre P0 仍有抖店/真实订单相关独立阻塞，不能据此宣称整个 real-pre P0 通过。
- 工作区存在用户既有未提交改动，本任务未修改、未暂存、未提交这些文件。
