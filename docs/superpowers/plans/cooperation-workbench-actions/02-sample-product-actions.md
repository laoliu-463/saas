# 合作单编辑与复制

## 操作能力

- 固定返回 `APPROVE, REJECT, EDIT, PROGRESS, COPY_LINK, COPY_ORDER, NOTE`。
- 审核沿用寄样状态机；编辑仅申请人或管理员可用。

## 修改订单

- `GET /samples/{id}/edit-context` 返回达人、商品、申请数量、规格、备注和申请人有效地址。
- `PUT /samples/{id}/cooperation-details` 仅修改备注和地址。
- 地址双写在同一事务内执行，并使用版本号检测并发冲突。

## 复制

- `POST /samples/{id}/promotion-copy` 由商品域生成固定十行推广文本和真实链接状态。
- `GET /samples/{id}/order-copy` 生成固定十三行订单文本。
- 粉丝数按 `W` 规则格式化；规格或橱窗销量缺失时输出 `---`。
- 两个接口先执行合作单可见性校验，不改变任何业务状态。

## 私有备注

- `GET/PUT /samples/{id}/private-note` 仅操作当前用户自己的内容。
- 内容最多 200 字，审计日志不记录正文。
