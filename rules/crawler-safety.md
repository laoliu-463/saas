# 规则：爬虫安全约束

**版本**：V1.0
**状态**：强制执行
**适用范围**：所有爬虫任务（Celery + Python 或 Java Scheduler）

---

## 一、请求安全约束

### 1.1 强制请求间隔

```python
# ✅ 正确：使用 CrawlerBase 基类
class DouyinTalentCrawler(CrawlerBase):
    def crawl(self, url: str) -> TalentInfo:
        # 基类自动处理 3-6 秒随机间隔
        html = self.http_get(url)
        return self.parse_html(html)

# ❌ 错误：直接发送请求无间隔
def wrong_crawl(url):
    response = requests.get(url)  # 禁止！无间隔
    return parse(response.text)
```

### 1.2 间隔配置

| 参数 | 值 | 说明 |
|------|------|------|
| `MIN_INTERVAL_MS` | 3000 | 最小间隔（毫秒） |
| `MAX_INTERVAL_MS` | 6000 | 最大间隔（毫秒） |

---

## 二、UA 轮换约束

### 2.1 必须使用随机 UA

```python
# ✅ 正确
class CrawlerBase:
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36...",
        # 至少 5 个不同 UA
    ]

    def random_ua(self) -> str:
        return random.choice(self.USER_AGENTS)
```

### 2.2 UA 列表要求

- [ ] 至少包含 5 个不同平台/版本的 UA
- [ ] 定期更新 UA 列表（建议每月）
- [ ] 禁止使用明显爬虫特征的 UA

---

## 三、Cookie 策略

### 3.1 Cookie 管理

```python
# ✅ 正确：使用 CookieJar 并定期刷新
class CrawlerBase:
    def __init__(self):
        self.cookie_jar = CookieJar()
        self.cookie_expire_time = None

    def refresh_cookie_if_needed(self):
        if self.is_cookie_expired():
            self.login_and_update_cookie()

    def is_cookie_expired(self) -> bool:
        if self.cookie_expire_time is None:
            return True
        return datetime.now() > self.cookie_expire_time
```

---

## 四、禁止做法

- [ ] **禁止**：并发请求同一来源（必须串行）
- [ ] **禁止**：无间隔连续请求
- [ ] **禁止**：使用单一固定 UA
- [ ] **禁止**：请求失败后立即重试（必须指数退避）
- [ ] **禁止**：在日志中打印 Cookie 或 Token

---

## 五、异常处理

### 5.1 指数退避重试

```python
def fetch_with_retry(self, url: str, max_retries: int = 3) -> Optional[str]:
    for attempt in range(max_retries):
        try:
            response = self.session.get(url, timeout=10)
            response.raise_for_status()
            return response.text
        except RequestException as e:
            if attempt == max_retries - 1:
                raise
            # 指数退避：2^attempt * base_interval
            sleep_time = (2 ** attempt) * self.MIN_INTERVAL_MS / 1000
            time.sleep(sleep_time)
    return None
```

---

## 六、监控告警

- [ ] 请求成功率 < 90% 时告警
- [ ] 连续 5 次失败时暂停爬虫并告警
- [ ] 记录每次请求的响应时间

---

## 七、相关文件索引

| 文件 | 说明 |
|------|------|
| `CrawlerBase.java` | Java 爬虫基类 |
| `crawler_base.py` | Python 爬虫基类 |
| `DouyinTalentCrawler.java` | 达人爬虫 |
| 需求入口 | `requirements/03-api-specs.md` |
