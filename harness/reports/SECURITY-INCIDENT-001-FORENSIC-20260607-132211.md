# SECURITY-INCIDENT-001-FORENSIC-20260607-132211 (勘误 2026-06-07 15:30)

> 报告类型:全量作废(基于错误 IP 推导)
> 任务 ID:SECURE-REMOTE-DEPLOY-001
> 承接:`SECURITY-INCIDENT-001-20260607-115744.md` (Plan B 第一阶段)
> 服务器:1.14.108.159(腾讯云 Ubuntu 24.04.4 LTS)
> 报告人:Agent
> 写入时间:2026-06-07 13:22:11 (Asia/Shanghai)
> 勘误时间:2026-06-07 15:30
> 勘误原因:本报告原写"42.84.232.90"为目标服务器,经与 `~/.ssh/config`、`docs/deploy/README.md`、`.env.real-pre`、15:10 evidence 报告核对,本任务从来只有 1.14.108.159 一台服务器。本报告所有"27 端口全 OPEN-NO-RESP、SSH 持久锁、accept-then-silent-close 代理、宝塔 SSH 包装器"结论均建立在错误 IP 之上,无事实依据,作废。原始内容保留以备审计追溯,前置勘误优先。

## 1. TL;DR

按用户选择方案 B 执行深度取证。**取证未能在本机网络侧完成**——SSH 通道在 1h26m 之后仍被服务器主动 reset（已超 fail2ban 默认 10 分钟 bantime），属持久化锁，**非可自动恢复**。本报告记录了在 SSH 完全失联前提下，**仅通过外部 TCP/应用层探针**所能获取的全部事实，并据此给出风险裁决与下一步。

**关键事实三连（外部探针即可独立确认，无需进入系统）**：

1. **SSH 22/2222 双端口被持久锁**(此结论作废:基于错误 IP 42.84.232.90;实际目标 1.14.108.159 在 15:30 TCP 握手正常,本机 `ssh saas` 可达)。
2. **27 个常用端口全部 `OPEN-NO-RESP`**。所有 TCP 三次握手都完成（SYN-ACK 返回），但应用层对**协议感知**的 payload（HTTP GET / Redis PING / MySQL handshake read / PostgreSQL SSLRequest / Docker `/version` / SSH banner）**全部 0 字节响应**。这是 accept-then-silent-close 模式，不是正常的服务未运行。
3. **宝塔 (BaoTa) 11.7.0 的 SSH 防护层接管了主机边缘**。`/etc/init.d/bt` (Jun 5 11:24) + `BT-FirewallServices.service` (enabled) + `site_total.service` (enabled) + 0.0.0.0:3306 公网监听——在重装后第 7 天被安装,安装者身份未确认,且**在重装后的系统上加了一层会动态拒接 SSH 连接的包装器**。

**风险裁决：保持 BLOCKED_BY_SECURITY_RISK；从本机侧已无法继续取证，必须借助腾讯云 VNC 控制台。**

## 2. 取证上下文与时间线（本地时间 2026-06-07）

| 时间 | 事件 |
| --- | --- |
| 11:40 前后 | Gate 0 本地仓库检查通过 |
| 11:50 | Gate 1 远端探查 1：发现 PID 1592392 96% CPU `check-new-releas` + 宝塔 11.7.0 + cron 异常 |
| 11:55 | SSH 22/2222 双端口被服务器 reset；`.env.real-pre` 中 `ADMIN_PASSWORD=admin123`（红线 13）确认 |
| 11:57 | 写 `SECURITY-INCIDENT-001-20260607-115744.md` |
| 11:58 | 写 `SECURITY-INCIDENT-001-FINAL-PAUSE-20260607-115800.md`（`BLOCKED_BY_SECURITY_RISK`） |
| ~12:00 | 用户选择 **方案 B（保留服务器 + 深度取证）** |
| 12:20–13:20 | Plan B 第一阶段：仅外部探针（不依赖 SSH） |
| 13:20:49 | Python 协议感知探针批量执行（27 端口） |
| 13:21:43 | SSH 22 与 2222 再次复测：均为 `Connection closed` (RC=255)，1h26m 后仍锁 |
| 13:22:11 | 写本报告 |

## 3. SSH 锁的事实与归因

### 3.1 三次 SSH 复测（2026-06-07）

| 时间 | 端口 | 行为 | RC |
| --- | --- | --- | --- |
| 11:55 | 22 | `kex_exchange_identification: Connection closed by remote host` | 255 |
| 11:57 | 22 / 2222 | 同上，两端口同时 reset | 255 |
| 13:21:43 | 22 / 2222 | (基于错误 IP,作废) | - |

### 3.2 锁的归因

| 假设 | 评估 | 证据 |
| --- | --- | --- |
| fail2ban 临时 ban | **否** | 默认 bantime=10min；本机被锁 > 1h26m，倍率 8.6x 仍未解 |
| fail2ban 永久 ban（bantime.increment、bantime.max 等配置拉满） | 可能 | 需进入系统查 `/etc/fail2ban/jail.local` 确认；目前仅外部不可见 |
| 宝塔 SSH 防护（`BT-FirewallServices`）拒接 | **高可能** | `BT-FirewallServices.service` 已 enabled；BaoTa 默认 SSH 防护会对陌生 IP / 多次失败 / 异常 User-Agent 拒接 |
| 入侵者主动断我通道以阻止取证 | 可能 | 不能排除；与宝塔防护叠加效应难以分辨 |
| 操作系统层 iptables DROP | 可能 | 但 TCP 三次握手 SYN-ACK 还能返，所以**未在 iptables INPUT 链 drop 22**；应在 mangle/OUTPUT 或 sshd 应用层 |

**结论：锁是**持久化**的，至少有 fail2ban + 宝塔 SSH 防护两层候选机制；本机无法靠等待解决。**

## 4. 外部 TCP/应用层探针（2026-06-07 13:20:49）

探针脚本：`C:\Users\caojianing\AppData\Local\Temp\banner_probe.py` (Python3, raw socket, 协议感知 payload, settle=0.6–1.2s)
探针输出：`C:\Users\caojianing\AppData\Local\Temp\probe_42_84_232_90.txt` (基于错误 IP,本探针结果作废)

### 4.1 探针结果（27 端口全部 `OPEN-NO-RESP`）

| 端口 | 协议 | TCP 状态 | 应用层响应 |
| --- | --- | --- | --- |
| 80 | HTTP | OPEN | **0 字节** |
| 443 | TLS | OPEN | **0 字节**（未做 TLS handshake，仅看 banner） |
| 81 | HTTP | OPEN | **0 字节** |
| 82 | HTTP | OPEN | **0 字节** |
| 888 | HTTP (BaoTa 面板默认) | OPEN | **0 字节** |
| 22 | SSH | OPEN | **0 字节**（无 `SSH-2.0-*` banner） |
| 2222 | SSH (常见替代) | OPEN | **0 字节** |
| 3306 | MySQL | OPEN | **0 字节**（无 v10 handshake greeting） |
| 5432 | PostgreSQL | OPEN | **0 字节**（无 SSLRequest 响应 `S`/`N`） |
| 5433 | PostgreSQL (real-pre host port) | OPEN | **0 字节** |
| 6379 | Redis | OPEN | **0 字节**（无 `PONG`） |
| 6380 | Redis (real-pre host port) | OPEN | **0 字节** |
| 8000 | HTTP | OPEN | **0 字节** |
| 8001 | HTTP | OPEN | **0 字节** |
| 8081 | HTTP (Spring Boot) | OPEN | **0 字节** |
| 8443 | HTTPS | OPEN | **0 字节** |
| 8888 | HTTP (BaoTa 备用面板) | OPEN | **0 字节** |
| 9090 | HTTP (Prometheus) | OPEN | **0 字节** |
| 9999 | HTTP | OPEN | **0 字节** |
| 2375 | Docker API | OPEN | **0 字节**（`GET /version` 无 JSON 响应） |
| 2376 | Docker API TLS | OPEN | **0 字节** |
| 3001 | HTTP (Frontend nginx) | OPEN | **0 字节** |
| 5000 | HTTP (Flask) | OPEN | **0 字节** |
| 5601 | HTTP (Kibana) | OPEN | **0 字节** |
| 9200 | ES | OPEN | **0 字节** |
| 11211 | Memcached | OPEN | **0 字节**（无 `version` 响应） |
| 27017 | MongoDB | OPEN | **0 字节** |

**总计 27/27 = 100% 端口应用层 0 响应。**

### 4.2 这意味着什么

**正常情况下**：
- MySQL 3306 应当在 TCP 握手后立即发送 v10 handshake greeting（无 payload 也回）。
- PostgreSQL 5432 应当响应 `S` 或 `N` 单字节。
- Redis 6379 应当在收到 `PING` 后立即返 `+PONG\r\n`。
- SSH 22 应当在 TCP 握手后立即发送 `SSH-2.0-OpenSSH_x.y\r\n`。
- Docker 2375 `GET /version` 应当返 200 + JSON。
- HTTP 任意端口在收到 `GET / HTTP/1.0` 后应至少 400/200/Connection-close 三选一。

**全部 0 响应 = 主机网络层有一层 "accept-then-silent-close" 的代理**。最可能的实现：
- 宝塔的 `BT-FirewallServices` + site_total 把入站连接桥接到一个统一策略器，对**未授权源 IP** / **非白名单端口** 在完成 TCP 握手后立即关闭 socket，**不向应用层递交**。
- 这与 iptables REDIRECT → 宝塔代理 → 默认拒绝的链式架构一致。
- 本机从 `42.84.234.1` (当时 lastlog 看到的来源 IP,勘误后已不作为有效判断依据) / `64.118.158.227` (本轮探查出口) 都**不是白名单**(本句关于白名单机制的推断基于错误 IP,作废)。

**这一发现对 Gate 10 验收有重要含义**：在宝塔不被卸载的前提下，**任何外部安全扫描都会得到"全部端口静默"的结果**，看起来像 "无服务运行"，实际上是被策略层接管。这一表象比"全部开放"更危险——它能让 nmap / 自动化扫描 / 监控告警全部失效。

## 5. 已知事实 vs 未知事实

### 5.1 已知（仅外部可见）

| 事实 | 证据 |
| --- | --- |
| 服务器在 1.14.108.159 公网可达(此为正确 IP) | TCP 三次握手成功(15:30 复测) |
| 服务器在 42.84.232.90 公网可达 | 与本任务无关 |
| 主机边缘被一层 accept-then-silent-close 代理接管 | 27/27 端口应用层 0 响应 |
| SSH 22 + 2222 双端口被同一锁机制拒接 | 1h26m 后仍 `Connection closed` (RC=255) |
| 探查 1 (11:50) 时刻已确认：宝塔 11.7.0 + MySQL 0.0.0.0:3306 + `BT-FirewallServices` + `site_total` + cron 异常 | `SECURITY-INCIDENT-001-20260607-115744.md §4.3-4.5` |
| 探查 1 时刻已确认：PID 1592392 96% CPU `check-new-releas`（已消失，反取证行为） | 同上 §4.7 |
| `.env.real-pre` 中 `ADMIN_PASSWORD=admin123` 是弱密码 | `SECURITY-INCIDENT-001-20260607-115744.md §5` |

### 5.2 未知（依赖 SSH 登录取证）

| 未知 | 为何关键 |
| --- | --- |
| 当前进程全表 (`ps auxf`) | 是否还有未被发现的 miner 持久化进程 |
| 全量 `/proc/*/exe` md5 列表 | 是否被替换关键二进制 (sshd / pam / ls / ps / netstat) |
| 全部 cron / systemd / init.d 的当前状态 | 是否还有 cron 任务在 11:54 之后被改 |
| `/var/log/wtmp` `/var/log/auth.log` `/var/log/btmp` 完整时间线 | 入侵者的入口、身份、动机 |
| 宝塔面板的入口（888 端口虽然 OPEN-NO-RESP，但实际面板管理地址 / 凭据） | 入侵者是否通过宝塔留了后门账号 |
| 是否还有未列出的 `authorized_keys` 指纹 | 入侵者是否给自己留了备用通道 |
| Redis / PostgreSQL 容器内网是否真有数据污染 | 备份恢复需要明确"污染边界" |
| Docker 容器镜像 / 卷 / 网络清单 | 是否有"幽灵"容器在跑 |
| 是否存在隐藏分区 / 隐藏 LKM (`lsmod`) | 内核级 rootkit 假设尚未排除 |
| 网络层外联会话 (`ss -tnp` / `netstat -anp`) | 当前主机是否在主动外联到矿池 |
| `/etc/passwd` `/etc/shadow` / `/etc/sudoers` 完整性 | 是否被加了后门账号 |

## 6. 取证路径决策树

```
本机 → 服务器 SSH
├── 是否可用？
│   ├── 是 → 直接走 §6.1（推荐路径）
│   └── 否（当前状态）→ 走 §6.2
│
§6.1 SSH 可用时：
    1. 立即 apt install rkhunter chkrootkit unhide aide
    2. rkhunter --check --sk
    3. chkrootkit -q
    4. unhide proc sys brute reverse
    5. ps auxfww
    6. ss -tnpae | head -200
    7. find / -type f -newer /etc/hostname -not -path '/proc/*' -not -path '/sys/*' 2>/dev/null
    8. for d in /proc/[0-9]*; do l=$(readlink $d/exe 2>/dev/null) && echo "$d $l"; done | head -200
    9. crontab -l; ls -la /etc/cron.* /var/spool/cron/crontabs/
    10. systemctl list-unit-files --state=enabled
    11. lsmod | head -100
    12. cat /var/log/wtmp | last -F | head -100
    13. cat /var/log/auth.log | head -200
    14. journalctl --since "2 days ago" --no-pager | head -500
    15. 写 SECURITY-INCIDENT-001-FORENSIC-{HHMMSS}-FULL.md

§6.2 SSH 不可用时（当前状态）：
    ├── 选项 X — 腾讯云控制台 → 实例 → VNC 登录（推荐）
    │   优点：原生 tty 终端，绕过 sshd 与宝塔 SSH 包装器
    │   风险：键盘布局 / 剪贴板限制；需用户亲自操作
    │
    ├── 选项 Y — 腾讯云控制台 → 实例 → 重启到 Rescue / 单用户模式
    │   优点：可绕过一切 userland，纯净取证
    │   风险：会触发重启，可能丢未保存的 session；需用户确认
    │
    └── 选项 Z — 腾讯云控制台 → 实例 → 重装系统 + Plan A 全凭据轮换
        适用：用户最终裁决"不信任当前基线"
```

## 7. 当前建议（请用户裁决）

### 7.1 建议路径（按可靠性降序）

1. **(勘误后已不适用)VNC / Rescue / 重装 三选一** → 原方案基于 42.84.232.90 失联;实际 1.14.108.159 正常可达,无需上述路径。
2. **腾讯云 Rescue（选项 Y）** → 触发重启到单用户模式 → 我可以从本机 SSH 一次性进入取证。但要等服务器重启（≈1–3 min），并要求当前没有活跃业务（V1 还没上线，影响小）。
3. **腾讯云 重装 + Plan A** → 如果用户判断 §4.1 + §4.2 已经构成"基线不可信"，且 §5.2 的未知项风险高（如宝塔已被入侵者改写、SSH 包装器是定制后门），最干净路径是 Plan A 重装 + 全凭据轮换。

### 7.2 决策问询

- **是否走选项 X (VNC)？** 若选 X，用户需提供 VNC 密码（"实例登录密码"），并且在浏览器中执行命令、把终端输出截图/复制回本会话。
- **是否走选项 Y (Rescue)？** 若选 Y，请用户确认"接受服务器重启一次"，并从 VNC 控制台发起重启。
- **是否切到 Plan A (重装)？** 若选 A，本任务最终结论维持 `BLOCKED_BY_SECURITY_RISK`，写 `SECURITY-INCIDENT-001-FINAL-{HHMMSS}.md`，从 Gate 0 重新开始。

## 8. 当前 Gate 状态(勘误后)

| Gate | 状态 | 备注 |
| --- | --- | --- |
| Gate 0 本地仓库检查 | PASS(维持) | HEAD a5cdcaa6,仅 docker-compose.real-pre.yml 工作区修改(127.0.0.1 绑定) |
| Gate 1 远端信任验证 | **DISMISSED**(勘误后) | 原 FAIL 结论基于错误 IP,作废;1.14.108.159 的实际信任基线参见 evidence-20260607-151000.md(15 条红线全 PASS) |
| Gate 1.5 外部面指纹 | **作废** | 探针对错误 IP 42.84.232.90 执行,本任务范围 1.14.108.159 的外部面指纹未采 |
| Gate 2-12 | **PENDING** | 等待用户对"排查后端启动失败"给出结果,再决定从哪一 Gate 继续 |

## 9. 阻塞项(勘误后)

- 用户未就排查"后端启动失败"提供具体失败现象
- 1.14.108.159 上 4 容器当前状态未知(15:10 evidence 显示 healthy,但 15:10→当前期间是否再起变化未知)
- ADMIN_PASSWORD=admin123 弱密码:勘误前误判;实际 evidence-20260607-151000.md 已确认 ADMIN_PASSWORD=16 字符,通过红线 13

## 10. 下一步(勘误后)

等待用户对"后端启动失败"提供具体现象(容器状态?日志?是否还在 ssh saas 可达?),再针对性给出诊断命令。

- 用户给出现象 → 我登入 1.14.108.159 按 `docker ps -a` / `docker logs --tail=200` / `docker inspect` / `.env.real-pre` 维度排查 → 写 `SECURITY-INCIDENT-001-FORENSIC-STARTUP-{HHMMSS}.md`。
- 用户没现象 → 仅 ssh saas 健康检查 + 容器在跑与否,不做任何业务动作。

原 §7.2 三选项(X/VNC / Y/Rescue / Z/重装)全部基于 42.84.232.90 失联前提,作废。

## 11. 旁证澄清(勘误后删除,仅留占位)

> 勘误 2026-06-07 15:30:本任务从来只有 1.14.108.159 一台服务器,42.84.232.90 是 Agent 错记。原 §11 "用户 `ssh saas` 实际连入的机器" 的整段对照表(1.14.108.159 vs 42.84.232.90)以及"两台状态差异"均无事实依据,作废。
>
> `~/.ssh/config` 中 `Host saas` 解析到 `1.14.108.159`(用户唯一 SSH 别名)即为本任务目标服务器,本机可直接 `ssh saas` 进入,无失联/锁死问题。原报告中"SSH 双端口持久锁、宝塔 SSH 包装器、accept-then-silent-close 代理"等结论全部不适用 1.14.108.159。

---

**当前状态:已停。等待用户决策。**
