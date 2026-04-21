# 瑙勫垯锛氱埇铏畨鍏ㄧ害鏉?
**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈夌埇铏换鍔★紙Celery + Python 鎴?Java Scheduler锛?
---

## 涓€銆佽姹傚畨鍏ㄧ害鏉?
### 1.1 寮哄埗璇锋眰闂撮殧

```python
# 鉁?姝ｇ‘锛氫娇鐢?CrawlerBase 鍩虹被
class DouyinTalentCrawler(CrawlerBase):
    def crawl(self, url: str) -> TalentInfo:
        # 鍩虹被鑷姩澶勭悊 3-6 绉掗殢鏈洪棿闅?        html = self.http_get(url)
        return self.parse_html(html)

# 鉂?閿欒锛氱洿鎺ュ彂閫佽姹傛棤闂撮殧
def wrong_crawl(url):
    response = requests.get(url)  # 绂佹锛佹棤闂撮殧
    return parse(response.text)
```

### 1.2 闂撮殧閰嶇疆

| 鍙傛暟 | 鍊?| 璇存槑 |
|------|------|------|
| `MIN_INTERVAL_MS` | 3000 | 鏈€灏忛棿闅旓紙姣锛?|
| `MAX_INTERVAL_MS` | 6000 | 鏈€澶ч棿闅旓紙姣锛?|

---

## 浜屻€乁A 杞崲绾︽潫

### 2.1 蹇呴』浣跨敤闅忔満 UA

```python
# 鉁?姝ｇ‘
class CrawlerBase:
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36...",
        # 鑷冲皯 5 涓笉鍚?UA
    ]

    def random_ua(self) -> str:
        return random.choice(self.USER_AGENTS)
```

### 2.2 UA 鍒楄〃瑕佹眰

- [ ] 鑷冲皯鍖呭惈 5 涓笉鍚屽钩鍙?鐗堟湰鐨?UA
- [ ] 瀹氭湡鏇存柊 UA 鍒楄〃锛堝缓璁瘡鏈堬級
- [ ] 绂佹浣跨敤鏄庢樉鐖櫕鐗瑰緛鐨?UA

---

## 涓夈€丆ookie 绛栫暐

### 3.1 Cookie 绠＄悊

```python
# 鉁?姝ｇ‘锛氫娇鐢?CookieJar 骞跺畾鏈熷埛鏂?class CrawlerBase:
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

## 鍥涖€佺姝㈠仛娉?
- [ ] **绂佹**锛氬苟鍙戣姹傚悓涓€鏉ユ簮锛堝繀椤讳覆琛岋級
- [ ] **绂佹**锛氭棤闂撮殧杩炵画璇锋眰
- [ ] **绂佹**锛氫娇鐢ㄥ崟涓€鍥哄畾 UA
- [ ] **绂佹**锛氳姹傚け璐ュ悗绔嬪嵆閲嶈瘯锛堝繀椤绘寚鏁伴€€閬匡級
- [ ] **绂佹**锛氬湪鏃ュ織涓墦鍗?Cookie 鎴?Token

---

## 浜斻€佸紓甯稿鐞?
### 5.1 鎸囨暟閫€閬块噸璇?
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
            # 鎸囨暟閫€閬匡細2^attempt * base_interval
            sleep_time = (2 ** attempt) * self.MIN_INTERVAL_MS / 1000
            time.sleep(sleep_time)
    return None
```

---

## 鍏€佺洃鎺у憡璀?
- [ ] 璇锋眰鎴愬姛鐜?< 90% 鏃跺憡璀?- [ ] 杩炵画 5 娆″け璐ユ椂鏆傚仠鐖櫕骞跺憡璀?- [ ] 璁板綍姣忔璇锋眰鐨勫搷搴旀椂闂?
---

## 涓冦€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `CrawlerBase.java` | Java 鐖櫕鍩虹被 |
| `crawler_base.py` | Python 鐖櫕鍩虹被 |
| `DouyinTalentCrawler.java` | 杈句汉鐖櫕 |
| 闇€姹傚叆鍙?| `doc/requirements/03-api-specs.md` |
