# test 环境

## 什么时候用

需要 mock 回归基线、隔离测试或 CI 可重复验证时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv test
```

## 成功标准

测试数据、mock 边界、测试结果和证据可重复；不得把 test 结果写成 real-pre 真实闭环。

## 失败回滚

停止失败测试并清理测试产生的临时数据；不得以修改真实环境配置来绕过 test 失败。
