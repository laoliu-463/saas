# 瑙勫垯锛欰PI 瀹夊叏绾︽潫

**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈夋姈闊?API 璋冪敤銆佹晱鎰熸暟鎹鐞?
---

## 涓€銆佹晱鎰熸暟鎹鐞?
### 1.1 璁㈠崟瑙ｅ瘑瑙勮寖

```java
// 鉁?姝ｇ‘锛氳皟鐢ㄥ畼鏂硅В瀵嗘帴鍙ｏ紝涓嶅瓨鍌ㄦ槑鏂?@Service
public class OrderDecryptService {

    public OrderDetailVO decryptAndShow(String orderId) {
        // 1. 妫€鏌ヨВ瀵嗘湁鏁堟湡
        if (isDecryptExpired(orderId)) {
            throw new BusinessException("璁㈠崟宸茶繃瑙ｅ瘑鏈夋晥鏈?);
        }

        // 2. 璋冪敤瀹樻柟瑙ｅ瘑鎺ュ彛
        DecryptResponse response = douyinApi.decryptOrder(orderId);

        // 3. 浠呰繑鍥炵粰鍓嶇锛屼笉瀛樺偍
        return OrderDetailVO.builder()
            .phone(response.getPhone()) // 浠呭睍绀?            .build();
    }
}

// 鉂?閿欒锛氬瓨鍌ㄨВ瀵嗘槑鏂?public void wrongSaveDecryptedData(String orderId, DecryptResponse response) {
    Order order = new Order();
    order.setPhone(response.getPhone()); // 绂佹锛佸瓨鍌ㄦ槑鏂?    orderMapper.updateById(order);
}
```

### 1.2 绂佹娓呭崟

- [ ] **绂佹**锛氬皢瑙ｅ瘑鍚庣殑鎵嬫満鍙峰瓨鍌ㄥ埌鏁版嵁搴?- [ ] **绂佹**锛氬皢瑙ｅ瘑鍚庣殑韬唤璇佸彿瀛樺偍鍒版暟鎹簱
- [ ] **绂佹**锛氬湪鏃ュ織涓墦鍗拌В瀵嗗悗鐨勬晱鎰熷瓧娈?- [ ] **绂佹**锛氬皢瑙ｅ瘑鏄庢枃鍐欏叆鏂囦欢

---

## 浜屻€乀oken 瀹夊叏

### 2.1 Token 鑾峰彇

```java
// 鉁?姝ｇ‘锛氶€氳繃 TokenService 鑾峰彇
@Service
public class OrderSyncService {

    public void syncOrders() {
        String token = tokenService.getValidToken(appId);
        // 浣跨敤 token...
    }
}

// 鉂?閿欒锛氱洿鎺ユ煡璇㈡暟鎹簱鑾峰彇 token
public void wrongSync() {
    String token = tokenMapper.selectByAppId(appId).getAccessToken(); // 绂佹锛?}
```

### 2.2 Token 缂撳瓨

- [ ] 浣跨敤 Redis 缂撳瓨 access_token
- [ ] refresh_token 鍗曠嫭缂撳瓨锛?0 澶╄繃鏈?- [ ] Token 鍗冲皢杩囨湡锛? 5 鍒嗛挓锛夋椂鑷姩鍒锋柊

---

## 涓夈€丄PI 閿欒澶勭悊

### 3.1 閿欒鐮佸鐞?
```java
public class DouyinApiException extends RuntimeException {
    private int errorCode;
    private String errorMsg;
    private String requestId;

    public static boolean isTokenExpired(int errorCode) {
        return errorCode == 10009 || errorCode == 10008;
    }

    public static boolean isRateLimited(int errorCode) {
        return errorCode == 40017;
    }
}

// 鉁?姝ｇ‘锛氬尯鍒嗗鐞嗛敊璇?public void callApi() {
    try {
        douyinApi.getOrderList(request);
    } catch (DouyinApiException e) {
        if (DouyinApiException.isTokenExpired(e.getErrorCode())) {
            tokenService.refreshToken(appId);
            // 閲嶈瘯
        } else if (DouyinApiException.isRateLimited(e.getErrorCode())) {
            // 绛夊緟鍚庨噸璇?            Thread.sleep(60_000);
        } else {
            throw e;
        }
    }
}
```

---

## 鍥涖€佹帴鍙ｈ皟鐢ㄩ檺鍒?
| 鎺ュ彛绫诲瀷 | 瓒呮椂鏃堕棿 | 閲嶈瘯娆℃暟 | 鐗规畩闄愬埗 |
|----------|----------|----------|----------|
| 璁㈠崟鍚屾 | 30s | 3 | 闇€ rate limit 澶勭悊 |
| 娲诲姩鍒楄〃 | 15s | 2 | - |
| 鍟嗗搧鍒楄〃 | 15s | 2 | - |
| 杈句汉杞摼 | 10s | 1 | pick_extra 鈮?20 瀛楃 |
| 璁㈠崟瑙ｅ瘑 | 10s | 2 | 闇€鏈夋晥鏈熸鏌?|

---

## 浜斻€佽姹傛棩蹇楄鑼?
### 5.1 蹇呴』璁板綍鐨勬棩蹇?
```java
// 鉁?姝ｇ‘锛氳褰?request_id
log.info("API call: buyin.settlement.order.list, request_id: {}, params: {}",
    response.getRequestId(), params);
```

### 5.2 绂佹鏃ュ織鍐呭

- [ ] **绂佹**锛氬湪鏃ュ織涓墦鍗?access_token
- [ ] **绂佹**锛氬湪鏃ュ織涓墦鍗拌В瀵嗗悗鐨勬墜鏈哄彿/韬唤璇?- [ ] **绂佹**锛氬湪鏃ュ織涓墦鍗?Cookie 鍐呭

---

## 鍏€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `DouyinApiClient.java` | API 瀹㈡埛绔?|
| `DouyinTokenService.java` | Token 鏈嶅姟 |
| `OrderDecryptService.java` | 瑙ｅ瘑鏈嶅姟 |
| 闇€姹傚叆鍙?| `doc/requirements/03-api-specs.md` |
