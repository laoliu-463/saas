# 闇€姹傦細鎶栭煶 API 瀵规帴瑙勮寖

**鏂囨。鐗堟湰**锛歏1.0
**鐘舵€?*锛氬凡瀹氱
**鏅鸿兘浣撳叆鍙?*锛氱洿鎺ヨ鍙栨鏂囦欢

---

## 涓€銆丄PI 瀵规帴姒傝

| API 绫诲瀷 | 鎺ュ彛绀轰緥 | 鐢ㄩ€?|
|----------|----------|------|
| **鎺堟潈鑾峰彇 Token** | `buyin.oauth.token` | 鑾峰彇 access_token |
| **娲诲姩绠＄悊** | `buyin.colonel.activity.list` | 鍥㈤暱娲诲姩鍒楄〃 |
| **娲诲姩鍟嗗搧** | `buyin.colonel.product.list` | 娲诲姩鍟嗗搧鍒楄〃 |
| **璁㈠崟鍚屾** | `buyin.settlement.order.list` | 缁撶畻璁㈠崟鍒楄〃 |
| **璁㈠崟瑙ｅ瘑** | `order.batchSensitiveDataRequest` | 璁㈠崟鏁忔劅鏁版嵁瑙ｅ瘑 |
| **杈句汉杞摼** | `buyin.instPickSourceConvert` | 鐢熸垚褰掑洜鐭摼 |
| **鍟嗗搧杞摼** | `buyin.productLink` | 鍟嗗搧鎺ㄥ箍閾炬帴 |

---

## 浜屻€丼DK 灏佽瑕佹眰

### 2.1 鐩綍缁撴瀯

```
com.colonel.saas
鈹斺攢鈹€ douyin
    鈹斺攢鈹€ sdk
        鈹溾攢鈹€ DouyinApiClient.java      # API 瀹㈡埛绔紙鍗曚緥锛?        鈹溾攢鈹€ DouyinTokenService.java  # Token 绠＄悊
        鈹斺攢鈹€ api
            鈹溾攢鈹€ ActivityApi.java      # 娲诲姩鐩稿叧 API
            鈹溾攢鈹€ OrderApi.java         # 璁㈠崟鐩稿叧 API
            鈹溾攢鈹€ ProductApi.java       # 鍟嗗搧鐩稿叧 API
            鈹斺攢鈹€ TalentApi.java        # 杈句汉鐩稿叧 API
```

### 2.2 Token 绠＄悊瑙勮寖

```java
// DouyinTokenService 鑱岃矗
public interface DouyinTokenService {
    /**
     * 鑾峰彇鏈夋晥 Token锛堣嚜鍔ㄥ埛鏂拌繃鏈?Token锛?     * @param appId 鎶栭煶搴旂敤 ID
     * @return 鏈夋晥鐨?access_token
     */
    String getValidToken(String appId);

    /**
     * 鍒锋柊 Token
     * @param appId 鎶栭煶搴旂敤 ID
     */
    void refreshToken(String appId);

    /**
     * 妫€鏌?Token 鏄惁鍗冲皢杩囨湡锛? 5 鍒嗛挓锛?     */
    boolean isTokenExpiringSoon(String appId);
}
```

### 2.3 Token 缂撳瓨绛栫暐

| 缂撳瓨閿?| 杩囨湡鏃堕棿 | 璇存槑 |
|--------|----------|------|
| `douyin:token:{appId}` | 瀹為檯杩囨湡鏃堕棿 | access_token |
| `douyin:refresh:{appId}` | 30澶?| refresh_token |

---

## 涓夈€丄PI 璋冪敤瑙勮寖

### 3.1 鏍囧噯璇锋眰娴佺▼

```java
public ApiResponse<OrderListResponse> getOrderList(String appId, OrderListRequest request) {
    // 1. 鑾峰彇鏈夋晥 Token锛堣嚜鍔ㄥ埛鏂帮級
    String token = tokenService.getValidToken(appId);

    // 2. 鏋勫缓璇锋眰鍙傛暟
    Map<String, Object> params = new HashMap<>();
    params.put("access_token", token);
    params.put("app_id", appId);
    params.put("start_time", request.getStartTime());
    params.put("end_time", request.getEndTime());
    params.put("page_size", request.getPageSize());
    params.put("cursor", request.getCursor());

    // 3. 鍙戦€佽姹傦紙甯﹂噸璇曪級
    return httpClient.post(API_ORDER_LIST)
        .header("Content-Type", "application/json")
        .body(params)
        .timeout(30_000)  // 30 绉掕秴鏃?        .retry(3)         // 鏈€澶氶噸璇?3 娆?        .execute()
        .as(OrderListResponse.class);
}
```

### 3.2 璇锋眰瓒呮椂閰嶇疆

| 鎺ュ彛绫诲瀷 | 瓒呮椂鏃堕棿 | 閲嶈瘯娆℃暟 |
|----------|----------|----------|
| 璁㈠崟鍚屾 | 30s | 3 |
| 娲诲姩鍒楄〃 | 15s | 2 |
| 鍟嗗搧鍒楄〃 | 15s | 2 |
| 杈句汉杞摼 | 10s | 1 |

### 3.3 閿欒澶勭悊

```java
// 閿欒鐮佸鐞?public class DouyinApiException extends RuntimeException {
    private int errorCode;
    private String errorMsg;
    private String requestId;  // 鎶栭煶杩斿洖鐨勮姹?ID锛岀敤浜庢帓鏌?
    // 甯歌閿欒鐮佸鐞?    public static boolean isTokenExpired(int errorCode) {
        return errorCode == 10009 || errorCode == 10008; // token 杩囨湡/鏃犳晥
    }

    public static boolean isRateLimited(int errorCode) {
        return errorCode == 40017; // 璋冪敤棰戠巼瓒呴檺
    }
}
```

---

## 鍥涖€佸綊鍥犲弬鏁拌鑼冿紙pick_source锛?
### 4.1 pick_source 鐢熸垚瑙勫垯

```java
/**
 * pick_source 鏍煎紡锛歿userId}_{shortId}_{timestamp}
 * 绀轰緥锛歶sr_abc123_1712000000
 *
 * 绾︽潫锛? * - 鎬婚暱搴?鈮?64 瀛楃
 * - short_id 蹇呴』鍞竴锛堥€氳繃 PickSourceMapping 琛ㄧ鐞嗭級
 */
public String generatePickSource(UUID userId, String productId, String activityId) {
    String shortId = generateShortId(userId);
    return String.format("%s_%s_%d",
        userId.toString().substring(0, 8),
        shortId,
        System.currentTimeMillis() / 1000
    );
}
```

### 4.2 pick_extra 鍙傛暟锛?0瀛楃闄愬埗锛?
```java
/**
 * pick_extra 闀垮害闄愬埗锛氣墹 20 瀛楃
 * 鐢ㄤ簬浼犻€掗澶栧綊鍥犱俊鎭紙濡傚晢鍝両D鍚?浣嶏級
 */
public String generatePickExtra(String productId) {
    if (productId != null && productId.length() > 20) {
        return productId.substring(productId.length() - 20);
    }
    return productId;
}
```

---

## 浜斻€佽鍗曡В瀵嗚鑼?
### 5.1 瑙ｅ瘑鎺ュ彛璋冪敤

```java
/**
 * 璁㈠崟瑙ｅ瘑蹇呴』璋冪敤瀹樻柟鎺ュ彛锛屼弗绂佹湰鍦板瓨鍌ㄦ槑鏂? * 鎺ュ彛锛歰rder.batchSensitiveDataRequest
 */
public DecryptResponse decryptOrder(String appId, List<String> orderIds) {
    // 1. 妫€鏌ヨ鍗曟槸鍚﹀湪瑙ｅ瘑鏈夋晥鏈熷唴锛坋xpire_time锛?    // 2. 璋冪敤瑙ｅ瘑鎺ュ彛
    // 3. 杩斿洖鏄庢枃浣嗕笉鎸佷箙鍖栧瓨鍌?    // 4. 浠呰繑鍥炵粰鍓嶇灞曠ず锛岀紦瀛?5 鍒嗛挓
}
```

### 5.2 绂佹鍋氭硶

- [ ] **绂佹**灏嗚В瀵嗗悗鐨勬墜鏈哄彿銆佽韩浠借瘉瀛樺偍鍒版暟鎹簱
- [ ] **绂佹**鍦ㄦ棩蹇椾腑鎵撳嵃瑙ｅ瘑鍚庣殑鏁忔劅淇℃伅
- [ ] **绂佹**灏嗚В瀵嗘帴鍙ｈ繑鍥炵殑鏄庢枃鍐欏叆鏂囦欢

---

## 鍏€佺埇铏€忔槑鍖栧熀绫?
璺緞锛歚com.colonel.saas.crawler.base`

```java
/**
 * 鐖櫕鍩虹被锛岄缃畨鍏ㄦ帶鍒? * 鏅鸿兘浣撳彧闇€瀹炵幇 HTML 瑙ｆ瀽锛屾棤闇€鍏冲績璇锋眰闂撮殧
 */
public abstract class CrawlerBase {

    // 璇锋眰闂撮殧锛?-6 绉掞紙闅忔満锛?    private static final int MIN_INTERVAL_MS = 3000;
    private static final int MAX_INTERVAL_MS = 6000;

    // User-Agent 杞崲
    private String[] userAgents = {...};

    /**
     * 甯﹀畨鍏ㄦ帶鍒剁殑 HTTP GET
     */
    protected String httpGet(String url) {
        // 1. 闅忔満寤舵椂
        Thread.sleep(randomInterval());

        // 2. 闅忔満 UA
        HttpRequest request = HttpUtil.createGet(url)
            .header("User-Agent", randomUA());

        // 3. 寮傚父澶勭悊
        return executeWithRetry(request);
    }

    /**
     * 瀛愮被瀹炵幇锛氳В鏋?HTML
     */
    protected abstract TalentInfo parseHtml(String html);
}
```

---

## 涓冦€佸紑鍙戠害鏉?
### 7.1 Token 鐩稿叧

- [ ] **蹇呴』**锛氫娇鐢?`DouyinTokenService` 鑾峰彇 Token锛岀姝㈢洿鎺ユ煡璇㈡暟鎹簱
- [ ] **蹇呴』**锛歍oken 鍗冲皢杩囨湡鏃惰嚜鍔ㄥ埛鏂?- [ ] **蹇呴』**锛歍oken 寮傚父鏃舵姏鍑?`DouyinApiException`

### 7.2 API 璋冪敤

- [ ] **蹇呴』**锛氭墍鏈?API 璋冪敤璁剧疆瓒呮椂鏃堕棿
- [ ] **蹇呴』**锛氬疄鐜伴噸璇曟満鍒讹紙鎸囨暟閫€閬匡級
- [ ] **蹇呴』**锛氳褰曡姹傛棩蹇楋紙鍚?request_id锛?
### 7.3 鏁忔劅鏁版嵁

- [ ] **绂佹**锛氭湰鍦拌В瀵嗗瓨鍌ㄨ鍗曟晱鎰熶俊鎭?- [ ] **绂佹**锛氭棩蹇楁墦鍗版晱鎰熷瓧娈碉紙鎵嬫満鍙枫€佽韩浠借瘉锛?- [ ] **蹇呴』**锛氳В瀵嗙粨鏋滀粎灞曠ず涓嶅瓨鍌?
### 7.4 鐖櫕

- [ ] **蹇呴』**锛氳姹傞棿闅?3-6 绉?- [ ] **蹇呴』**锛歎A 杞崲
- [ ] **绂佹**锛氬苟鍙戣姹傚悓涓€鏉ユ簮

---

## 鍏€侀獙鏀舵爣鍑?
1. **Token 绠＄悊**锛氳繃鏈?Token 鑷姩鍒锋柊锛屾帴鍙ｄ笉涓柇
2. **褰掑洜鍙傛暟**锛歱ick_source 鐢熸垚姝ｇ‘锛宲ick_extra 鈮?20 瀛楃
3. **璁㈠崟瑙ｅ瘑**锛氳В瀵嗘帴鍙ｈ皟鐢ㄦ垚鍔燂紝鏄庢枃涓嶈惤搴?4. **鐖櫕瀹夊叏**锛氳繛缁姹傞棿闅旂鍚?3-6 绉掕姹?5. **閿欒澶勭悊**锛欰PI 寮傚父鏃惰繑鍥炴槑纭敊璇俊鎭?
---

## 涔濄€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璺緞 | 璇存槑 |
|------|------|------|
| Token 鏈嶅姟 | `backend/src/main/java/com/colonel/saas/douyin/DouyinTokenService.java` | Token 绠＄悊 + 鍒锋柊 |
| API 瀹㈡埛绔?| `backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java` | API 灏佽 |
| API 灞?| `backend/src/main/java/com/colonel/saas/douyin/api/` | 娲诲姩/鍟嗗搧/璁㈠崟/鎺ㄥ箍/杈句汉 API |
| SDK 灏佽 | `backend/src/main/java/com/colonel/saas/douyin/sdk/` | 鎶栧簵 SDK 闆嗘垚 |
| 褰掑洜鏈嶅姟 | `backend/src/main/java/com/colonel/saas/service/AttributionService.java` | 褰掑洜绠＄悊 |
| 鏄犲皠鏈嶅姟 | `backend/src/main/java/com/colonel/saas/service/PickSourceMappingService.java` | pick_source 鏄犲皠 |
| 鐖櫕鍩虹被 | `backend/src/main/java/com/colonel/saas/crawler/CrawlerBase.java` | 鐖櫕鍩虹被 |
| 鐖櫕璋冨害 | `backend/src/main/java/com/colonel/saas/crawler/CrawlerScheduler.java` | 鐖櫕瀹氭椂璋冨害 |
| 璁㈠崟鍚屾 | `backend/src/main/java/com/colonel/saas/service/OrderSyncService.java` | 璁㈠崟鍚屾鏈嶅姟 |
| 鍚屾浠诲姟 | `backend/src/main/java/com/colonel/saas/job/OrderSyncJob.java` | 瀹氭椂鍚屾浠诲姟 |
| Lint 瑙勫垯 | `doc/rules/api-security.md` | API 瀹夊叏绾︽潫 |

---

## 鍗併€佽缁?API 鍝嶅簲缁撴瀯

### 10.1 鎺堟潈 Token 鎺ュ彛锛坆uyin.oauth.token锛?
**璇锋眰鍙傛暟**锛?```
grant_type = authorization_code
code = 鎺堟潈鐮?client_key = 搴旂敤 client_key
client_secret = 搴旂敤 client_secret
```

**鎴愬姛鍝嶅簲**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "access_token": "REPEATToken...",
    "refresh_token": "REFRESHToken...",
    "expires_in": 7200,
    "refresh_expires_in": 2592000
  }
}
```

**澶辫触鍝嶅簲**锛?```json
{
  "err_no": 31012,
  "err_msg": "refresh token invalid",
  "log_id": "20260420xxxx"
}
```

> **閿欒鐮?31012**锛歚refresh_token` 琚埛姝伙紙澶氳繘绋嬪苟鍙戝埛鏂帮級銆傚繀椤讳娇鐢?Redis 鍒嗗竷寮忛攣闃查噸銆?
---

### 10.2 娲诲姩鍒楄〃鎺ュ彛锛坆uyin.colonel.activity.list锛?
**璇锋眰鍙傛暟**锛?| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `access_token` | string | 鉁?| 鏈夋晥 access_token |
| `app_id` | integer | 鉁?| 搴旂敤 app_id |
| `start_time` | integer | 鉁?| 寮€濮嬫椂闂达紙绉掔骇鏃堕棿鎴筹級 |
| `end_time` | integer | 鉁?| 缁撴潫鏃堕棿锛堢绾ф椂闂存埑锛?|
| `page_size` | integer | 鉂?| 姣忛〉鏉℃暟锛岄粯璁?0锛屾渶澶?00 |

**鎴愬姛鍝嶅簲**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "activity_id": "7318828232912345678",
        "title": "鍥㈤暱娲诲姩鏍囬",
        "colonel_buyin_id": "7348293728374323456",
        "commission_rate": 85,
        "service_rate": 35,
        "status": 1,
        "start_time": 1711900800,
        "end_time": 1714492800,
        "create_time": 1711897200
      }
    ],
    "page_size": 20,
    "total": 100,
    "has_more": true
  }
}
```

**涓氬姟鏍￠獙**锛?- `commission_rate`锛氭嫑鍟嗘彁鎴愭瘮渚嬶紝0-100鏁存暟锛?*涓嶅緱瓒呰繃 90**
- `service_rate`锛氭湇鍔¤垂姣斾緥锛?-100鏁存暟锛?*涓嶅緱瓒呰繃 40**
- 杩斿洖鍊间负鐧惧垎姣旀暣鏁帮紙濡?85 琛ㄧず 85%锛?
---

### 10.3 娲诲姩鍟嗗搧鎺ュ彛锛坆uyin.colonel.product.list锛?
**璇锋眰鍙傛暟**锛?| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `access_token` | string | 鉁?| 鏈夋晥 access_token |
| `activity_id` | string | 鉁?| 鍥㈤暱娲诲姩 ID |
| `page_size` | integer | 鉂?| 姣忛〉鏉℃暟锛岄粯璁?0锛屾渶澶?00 |
| `cursor` | integer | 鉂?| 缈婚〉娓告爣锛?琛ㄧず绗竴椤碉級 |

**鎴愬姛鍝嶅簲**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "product_id": "7495829108234567890",
        "title": "鍟嗗搧鏍囬",
        "amount": 299900,
        "cos_ratio": 10,
        "cos_fee": 2999,
        "service_ratio": 35,
        "min_refer_amount": 0,
        "status": 1,
        "assignee_id": "7392837498234567890"
      }
    ],
    "page_size": 20,
    "has_more": true,
    "next_cursor": 21
  }
}
```

> **閲戦鍗曚綅**锛歚amount`锛堝晢鍝佷环鏍硷級銆乣cos_fee`锛堟妧鏈湇鍔¤垂锛夊潎涓?*鍒?*锛堟暣鏁帮級锛?*绂佹**浣跨敤 Double銆?
---

### 10.4 鍥㈤暱璁㈠崟鎺ュ彛锛坆uyin.settlement.order.list锛?
**璇锋眰鍙傛暟**锛?| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `access_token` | string | 鉁?| 鏈夋晥 access_token |
| `app_id` | integer | 鉁?| 搴旂敤 app_id |
| `start_time` | integer | 鉁?| 寮€濮嬫椂闂达紙绉掔骇鏃堕棿鎴筹級 |
| `end_time` | integer | 鉁?| 缁撴潫鏃堕棿锛堢绾ф椂闂存埑锛?|
| `phase_id` | string | 鉂?| 澶т績闃舵 ID锛堝彲涓嶄紶锛?|
| `page_size` | integer | 鉂?| 姣忛〉鏉℃暟锛岄粯璁?0锛屾渶澶?00 |

**鎴愬姛鍝嶅簲**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "list": [
      {
        "order_id": "74958291082345678912345678901234",
        "product_id": "7495829108234567890",
        "activity_id": "7318828232912345678",
        "talent_uid": "9876543210987654321",
        "talent_nickname": "杈句汉鏄电О",
        "pick_source": "usr_abc123_1712000000",
        "pick_extra": "749582",
        "order_amount": 299900,
        "actual_amount": 299900,
        "service_fee": 104965,
        "platform_fee": 10496,
        "talent_commission": 89970,
        "commission_rate": 85,
        "status": 2,
        "create_time": 1712000000,
        "settle_time": 1714684800,
        "phase_id": "20260401_20260430"
      }
    ],
    "page_size": 20,
    "has_more": true
  }
}
```

**鍏抽敭瀛楁璇存槑**锛?- `order_amount`锛氳鍗曢噾棰濓紙鍒嗭級锛?*涓嶅緱**浣跨敤 Double
- `actual_amount`锛氬疄闄呮敮浠橀噾棰濓紙鍒嗭級
- `service_fee`锛氭湇鍔¤垂锛堝垎锛夛紝鐢ㄤ簬涓氱哗璁＄畻
- `platform_fee`锛氬钩鍙版妧鏈湇鍔¤垂锛堝垎锛?- `talent_commission`锛氳揪浜轰剑閲戯紙鍒嗭級
- `pick_source`锛氬綊鍥犲弬鏁帮紝**蹇呴』**閫氳繃鏄犲皠琛ㄨВ鏋愶紝绂佹瀛楃涓茶В鏋?- `pick_extra`锛氶€忎紶鍊硷紙鈮?0瀛楃锛夛紝鐢ㄤ簬杈呭姪褰掑洜

---

### 10.5 璁㈠崟瑙ｅ瘑鎺ュ彛锛坥rder.batchSensitiveDataRequest锛?
**璇锋眰鍙傛暟**锛?| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `access_token` | string | 鉁?| 鏈夋晥 access_token |
| `order_ids` | array[string] | 鉁?| 璁㈠崟 ID 鍒楄〃锛堟渶澶?0鏉?娆★級 |
| `type` | integer | 鉁?| 瑙ｅ瘑绫诲瀷锛?=鎵嬫満鍙?|

**鎴愬姛鍝嶅簲锛堝惈铏氭嫙鍙峰鐞嗭級**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": [
    {
      "order_id": "74958291082345678912345678901234",
      "is_virtual_tel": true,
      "phone_no_a": "138****0001",
      "phone_no_b": "138****0002",
      "expire_time": 1717363200
    },
    {
      "order_id": "74958291082345678912345678901235",
      "is_virtual_tel": false,
      "phone": "13812345678"
    }
  ]
}
```

**铏氭嫙鍙峰瓧娈佃鏄?*锛?| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `is_virtual_tel` | boolean | 鏄惁涓鸿櫄鎷熷彿锛堝揩閫掕仈缁滃彿锛?|
| `phone_no_a` | string | 铏氭嫙鍙稟锛堣揪浜烘墜鏈哄彿锛岃劚鏁忥級 |
| `phone_no_b` | string | 铏氭嫙鍙稡锛堝揩閫掑憳鎵嬫満鍙凤紝鑴辨晱锛?|
| `expire_time` | integer | 铏氭嫙鍙疯繃鏈熸椂闂达紙绉掔骇鏃堕棿鎴筹級锛岃繃鏈熷悗鑱旂粶澶辨晥 |
| `phone` | string | 闈炶櫄鎷熷彿鏃讹紝鐩存帴杩斿洖鐪熷疄鎵嬫満鍙凤紙鑴辨晱锛?|

> **铏氭嫙鍙锋湁鏁堟湡**锛氳櫄鎷熷彿鍦?`expire_time` 涔嬪墠鏈夋晥锛岃繃鏈熷悗鑱旂粶澶辨晥锛岄渶鍦ㄩ〉闈㈡槑纭睍绀鸿繃鏈熸彁绀恒€?
**涓氬姟绾︽潫**锛?- [ ] **绂佹**锛氬皢瑙ｅ瘑缁撴灉鎸佷箙鍖栧埌鏁版嵁搴?- [ ] **绂佹**锛氭棩蹇楁墦鍗拌В瀵嗗悗鐨勬墜鏈哄彿
- [ ] **蹇呴』**锛氳В瀵嗙粨鏋滀粎杩斿洖鍓嶇灞曠ず锛岀紦瀛樹笉瓒呰繃 5 鍒嗛挓
- [ ] **蹇呴』**锛氭瘡娆¤В瀵嗚姹傞檺鍒?50 鏉¤鍗?
---

### 10.6 杈句汉杞摼鎺ュ彛锛坆uyin.instPickSourceConvert锛?
**璇锋眰鍙傛暟**锛?| 鍙傛暟 | 绫诲瀷 | 蹇呭～ | 璇存槑 |
|------|------|------|------|
| `access_token` | string | 鉁?| 鏈夋晥 access_token |
| `account_id` | string | 鉁?| 璐﹀彿 ID锛坅pp_id锛?|
| `product_id` | string | 鉁?| 鍟嗗搧 ID |
| `talent_uid` | string | 鉁?| 杈句汉 UID |
| `pick_extra` | string | 鉂?| 閫忎紶鍊硷紙**鈮?20 瀛楃**锛?|

**鎴愬姛鍝嶅簲**锛?```json
{
  "err_no": 0,
  "err_msg": "success",
  "data": {
    "pick_source": "usr_abc123_1712000000",
    "pick_url": "https://haohuo.snssdk.com/..."
  }
}
```

> **pick_extra 闄愬埗**锛欰PI 鏄庣‘瑕佹眰 `pick_extra` 鈮?20 瀛楃锛岃秴闀挎埅鏂紙鍙栧悗20浣嶏級銆?
---

## 鍗佷竴銆佹柟妗圔褰掑洜锛圫hortID锛?
### 11.1 鏂规B璁捐鑳屾櫙

褰?`pick_source` 涓?`userId` 閮ㄥ垎瓒呰繃 `pick_extra` 20瀛楃闄愬埗鏃讹紝寮曞叆 ShortID 鏂规锛?
```
pick_extra = short_id锛?浣岯ase36锛屼粎鈮?0瀛楃锛?pick_source_mapping.uuid_seed = 鍘熷UUID锛堢敤浜庡弽鏌ヨ繕鍘燂級
```

### 11.2 鏄犲皠琛ㄥ瓧娈?
| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `short_id` | VARCHAR(10) UNIQUE | 8浣岯ase36鐭爜锛屸墹10瀛楃 |
| `uuid_seed` | UUID | 鍘熷UUID锛岀敤浜庡弽鏌ヨ繕鍘?|
| `pick_extra` | VARCHAR(10) | 瀹為檯閫忎紶鍊?short_id |
| `pick_source` | VARCHAR(128) | 瀹屾暣褰掑洜瀛楃涓诧紙淇濈暀锛?|
| `expire_time` | TIMESTAMP | 杩囨湡鏃堕棿锛堥粯璁?0澶╁悗锛?|

### 11.3 褰掑洜杩樺師娴佺▼

```java
public UUID resolveFromShortId(String shortId) {
    PickSourceMapping mapping = pickSourceMapper.selectByShortId(shortId);
    if (mapping == null) {
        return null;
    }
    if (mapping.getExpireTime() != null && mapping.getExpireTime().isBefore(LocalDateTime.now())) {
        return null;  // 宸茶繃鏈?    }
    return mapping.getUuidSeed();
}
```

> **杩囨湡娓呯悊**锛氬畾鏃朵换鍔℃瘡30澶╂壂鎻忚繃鏈熸槧灏勶紝`expire_time < now()` 鏃剁墿鐞嗗垹闄ゃ€?
---

## 鍗佷簩銆乀oken 绠＄悊锛圧edis 鍒嗗竷寮忛攣锛?
### 12.1 骞跺彂鍒锋柊闂

澶氫釜杩涚▼鍚屾椂妫€娴嬪埌 Token 鍗冲皢杩囨湡鏃讹紝浼氬苟鍙戣皟鐢ㄥ埛鏂版帴鍙ｏ紝瀵艰嚧锛?- 閿欒鐮?31012锛坮efresh token invalid锛?- refresh_token 琚埛姝?- 闇€閲嶆柊鎺堟潈

### 12.2 Redis 鍒嗗竷寮忛攣瀹炵幇

```java
public String refreshTokenWithLock(String appId) {
    String lockKey = "douyin:token:lock:" + appId;

    // 灏濊瘯鑾峰彇閿侊紙5鍒嗛挓鑷姩杩囨湡锛?    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));

    if (Boolean.TRUE.equals(acquired)) {
        try {
            return doRefreshToken(appId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 绛夊緟閿侀噴鏀?        while (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Thread.sleep(100);
        }
        // 閿侀噴鏀惧悗閲嶆柊鑾峰彇 Token
        return getValidToken(appId);
    }
}
```

### 12.3 缂撳瓨绛栫暐

| 缂撳瓨閿?| 鍊?| TTL | 璇存槑 |
|--------|----|-----|------|
| `douyin:token:{appId}` | access_token | 2灏忔椂锛堝疄闄呰繃鏈熸椂闂达級 | 鏈夋晥 Token |
| `douyin:refresh:{appId}` | refresh_token | 30澶?| 鍒锋柊 Token |
| `douyin:token:lock:{appId}` | 1 | 5鍒嗛挓 | 鍒锋柊閿?|

---

## 鍗佷笁銆佸畾鏃朵换鍔¤璁?
### 13.1 Token 鍒锋柊浠诲姟

```sql
-- 姣忓皬鏃舵墽琛岋紝妫€鏌ュ嵆灏嗚繃鏈熺殑 Token 骞跺埛鏂?SELECT refresh_token_if_expiring();
```

```java
@Scheduled(cron = "0 0 * * * ?")  // 姣忓皬鏃舵暣鐐?public void refreshExpiringTokens() {
    List<DouyinToken> expiringTokens = douyinTokenMapper.selectExpiringSoon(300); // 5鍒嗛挓鍐呰繃鏈?    for (DouyinToken token : expiringTokens) {
        try {
            tokenService.refreshTokenWithLock(token.getAppId());
        } catch (Exception e) {
            log.error("Token 鍒锋柊澶辫触: appId={}", token.getAppId(), e);
        }
    }
}
```

### 13.2 璁㈠崟鍚屾浠诲姟锛堟粦鍔ㄧ獥鍙ｏ級

```java
@Scheduled(fixedDelay = 60000)  // 姣忓垎閽熸墽琛?public void syncOrders() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime start = now.minusMinutes(11);  // 10鍒嗛挓鍓嶏紙鐣?鍒嗛挓閲嶅彔锛?    LocalDateTime end = now.minusMinutes(1);     // 1鍒嗛挓鍓?
    // 浣跨敤婊戝姩绐楀彛閬垮厤婕忓崟
    orderSyncService.syncByTimeRange(start, end);
}
```

> **婊戝姩绐楀彛绛栫暐**锛氭瘡娆″悓姝ヨ鐩栨渶杩?10 鍒嗛挓鏁版嵁锛屼笂涓€娆″悓姝ュ拰鏈鏈?1 鍒嗛挓閲嶅彔锛岀‘淇濅笉婕忓崟銆?
### 13.3 鏄犲皠琛ㄦ竻鐞嗕换鍔?
```sql
-- 姣忓懆涓€鍑屾櫒3鐐规墽琛岋紝娓呯悊90澶╁墠杩囨湡鐨勬槧灏勮褰?SELECT cleanup_expired_mappings();
```

```java
@Scheduled(cron = "0 0 3 ? * MON")  // 姣忓懆涓€鍑屾櫒3鐐?public void cleanupExpiredMappings() {
    LocalDateTime deadline = LocalDateTime.now().minusDays(90);
    int count = pickSourceMapper.deleteExpired(deadline);
    log.info("娓呯悊杩囨湡鏄犲皠璁板綍: {} 鏉?, count);
}
```

---

## 鍗佸洓銆佸叧閿闄╄鍛?
### 14.1 閲戦鍗曚綅闄烽槺

```java
// 鉂?閿欒锛氬亣璁?amount 鏄厓
BigDecimal amount = new BigDecimal(order.getAmount()) / 100;

// 鉁?姝ｇ‘锛歛mount 宸茬粡鏄垎锛岀洿鎺ヤ娇鐢?Long amount = order.getAmount();  // 鍗曚綅锛氬垎
```

> 鎵€鏈夐噾棰濆瓧娈碉紙`order_amount`銆乣actual_amount`銆乣service_fee`銆乣platform_fee`銆乣talent_commission`銆乣cos_fee`銆乣amount`锛夊崟浣嶅潎涓?*鍒?*锛圠ong锛夛紝绂佹闄や互100銆?
### 14.2 ID 闀垮害闄愬埗

- `pick_extra` 鈮?20 瀛楃锛岃秴闀挎埅鏂彇鍚?0浣?- `short_id` 鏂规B 鈮?10 瀛楃
- 娓犻亾鐮?`channel_code` 瀛樺偍鐭爜锛岄潪 UUID

### 14.3 鏄犲皠琛ㄨ繃鏈?
- 榛樿杩囨湡鏃堕棿锛?0 澶?- 杩囨湡鐨?`pick_source` 鏃犳硶鍙嶆煡锛岃鍗曞綊鍥犲け璐?- 瀹氭湡娓呯悊浠诲姟姣忓懆鎵ц

### 14.4 骞跺彂鍒锋柊 31012

- 澶氳繘绋嬪悓鏃跺埛鏂?Token 瀵艰嚧 refresh_token 澶辨晥
- **蹇呴』**浣跨敤 Redis 鍒嗗竷寮忛攣锛歚douyin:token:lock:{appId}`
- 閿?TTL 5 鍒嗛挓锛岄槻姝㈡閿?
### 14.5 铏氭嫙鍙锋湁鏁堟湡

- `expire_time` 鍒拌揪鍚庯紝铏氭嫙鍙峰け鏁?- 椤甸潰灞曠ず鏃堕渶鎻愮ず"璇ヨ仈缁滄柟寮忓凡杩囨湡"
- 瑙ｅ瘑璇锋眰鏃舵鏌?`expire_time`锛岃繃鏈熶笉杩斿洖铏氭嫙鍙?
---

## 鍗佷簲銆丳ython 瀹炵幇鍙傝€冿紙Celery 浠诲姟锛?
```python
# backend/tasks/order_sync.py
from celery import Celery
from sqlalchemy import text
import logging

logger = logging.getLogger(__name__)
bp = Celery('order_sync', broker=os.getenv('REDIS_URL'))

@bp.task
def sync_orders(start_time: int, end_time: int):
    """婊戝姩绐楀彛璁㈠崟鍚屾"""
    page_size = 100
    cursor = 0

    while True:
        resp = douyin_api.colonel_order_list(
            start_time=start_time,
            end_time=end_time,
            page_size=page_size,
            cursor=cursor
        )

        orders = resp.get('data', {}).get('list', [])
        if not orders:
            break

        # 鎵归噺 upsert锛坧hase_id 涓哄垎鍖烘爣璇嗭級
        with DBSession() as db:
            db.execute(text("""
                INSERT INTO colonelsettlement_order (...)
                VALUES (...)
                ON CONFLICT (id, create_time) DO UPDATE SET
                    phase_id = EXCLUDED.phase_id
            """), orders)

        has_more = resp.get('data', {}).get('has_more', False)
        if not has_more:
            break
        cursor += page_size

    logger.info(f"璁㈠崟鍚屾瀹屾垚: {cursor} 鏉?)

@bp.task
def refresh_token_if_expiring():
    """Token 鍗冲皢杩囨湡鏃跺埛鏂?""
    with DBSession() as db:
        tokens = db.execute(text("""
            SELECT app_id FROM douyin_token
            WHERE expire_time < NOW() + INTERVAL '5 minutes'
        """)).fetchall()

    for token in tokens:
        with redis_lock(f"douyin:token:lock:{token['app_id']}"):
            refresh_token(token['app_id'])
