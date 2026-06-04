# Evidence Report — ORDER-FRONTEND-PRODUCT-INFO-002

**时间**: 2026-06-04 14:30  
**环境**: real-pre (本地)  

---

## 构建结果

| 步骤 | 结果 |
|------|------|
| vue-tsc -b (typecheck) | PASS |
| vite build | PASS |
| Docker image build | PASS |

## Docker 状态

| 服务 | 状态 |
|------|------|
| frontend-real-pre | Up (healthy) |
| backend-real-pre | Up (healthy) |

## 健康检查结果

- Docker health check: PASS (healthy)

## 业务验证结果

- 商品信息列布局: PASS (17/17 测试)
- 渠道文案: PASS (全局搜索无"媒介")
- 页面人工验证: PENDING

## 是否部署远端: 否

## 结论: **PARTIAL** — 前端修改、构建、测试、容器重启全部完成，页面待人工确认。

## 剩余风险

1. 商品图片后端未返回
2. 部分字段后端未返回，显示 `-`
