# 第三方接口

## 什么时候用

修改抖音 / 抖店鉴权、参数转换、签名、错误码、重试或真实上游联调时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv real-pre
```

## 成功标准

请求边界、脱敏日志、错误映射、幂等和真实上游结果可验证；前端不直接持有第三方凭据或调用第三方接口。

## 失败回滚

停止真实请求扩大，保留 request id 和脱敏响应；恢复适配层代码并重新走 CI，不通过关闭真实开关伪造成功。
