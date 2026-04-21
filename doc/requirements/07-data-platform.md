# 闇€姹傦細鏁版嵁骞冲彴

**鏂囨。鐗堟湰**锛歏1.0
**鏉ユ簮**锛歏2.2 瀹氱鏂囨。 搂3.6
**鏅鸿兘浣撳叆鍙?*锛氱洿鎺ヨ鍙栨鏂囦欢

---

## 涓€銆佸姛鑳藉畾浣?
鏁版嵁骞冲彴鏄牳蹇冧笟缁╃湅鏉匡紝鏀拺绠＄悊灞傚喅绛栥€傝鐩栨牳蹇冩寚鏍囥€佽鍗曟槑缁嗐€佸缁村害绛涢€夊拰鏁版嵁瀵煎嚭銆?
---

## 浜屻€佹牳蹇冩寚鏍囧崱鐗?
| 鎸囨爣 | 璁＄畻鏂瑰紡 | 鏁版嵁鏉ユ簮 |
|------|----------|----------|
| 鎬昏鍗曟暟 | COUNT(order_id) | `colonelsettlement_order` |
| 璁㈠崟鎬婚 | SUM(order_amount) | `colonelsettlement_order` |
| 鏈嶅姟璐规敹鍏?| SUM(service_fee) | `colonelsettlement_order` |
| 鎶€鏈湇鍔¤垂 | SUM(platform_fee) | `colonelsettlement_order` |
| 鏈嶅姟璐规敮鍑?| SUM(talent_commission) | `colonelsettlement_order` |
| 鏈嶅姟璐规敹鐩?| 鏈嶅姟璐规敹鍏?- 鎶€鏈湇鍔¤垂 - 鏈嶅姟璐规敮鍑?| 琛嶇敓璁＄畻 |
| 鎷涘晢鎻愭垚 | SUM(commission_config.zs_ratio 脳 service_fee) | 璁㈠崟 + 閰嶇疆琛?|
| 娓犻亾鎻愭垚 | SUM(commission_config.qd_ratio 脳 service_fee) | 璁㈠崟 + 閰嶇疆琛?|
| 姣涘埄 | 鏈嶅姟璐规敹鐩?- 鎷涘晢鎻愭垚 - 娓犻亾鎻愭垚 | 琛嶇敓璁＄畻 |

---

## 涓夈€佷笟缁╁綊灞炶鍒?
### 3.1 褰掑睘浼樺厛绾?
```
1. 鐙杈句汉 鈫?褰掕棰嗘笭閬擄紙pick_source 褰掑洜閫昏緫澶辨晥锛?2. 鐙鍟嗗 鈫?褰掔嫭瀹舵嫑鍟嗭紙鎸夊晢鍝佸綊灞為€昏緫澶辨晥锛?3. 榛樿娓犻亾褰掑睘 鈫?pick_source 鈫?PickSourceMapping 鈫?channel_id
4. 榛樿鎷涘晢褰掑睘 鈫?鍟嗗搧 鈫?娲诲姩 鈫?鎷涘晢璐熻矗浜?```

### 3.2 娓犻亾涓氱哗锛坧ick_source 褰掑洜锛?
> 璇﹁ `doc/rules/attribution-logic.md`

```java
// OrderSettlementService.resolveChannel()
public UUID resolveChannel(Order order) {
    // 1. 妫€鏌ョ嫭瀹惰揪浜?    ExclusiveTalent et = exclusiveTalentService.getEffective(order.getTalentUid());
    if (et != null) {
        return et.getChannelId();  // 鐙杈句汉浼樺厛
    }

    // 2. 閫氳繃 pick_source 鏄犲皠琛ㄥ弽鏌ユ笭閬?    return pickSourceMappingService.resolveChannel(order.getPickSource());
}
```

### 3.3 鎷涘晢涓氱哗锛堟寜娲诲姩褰掑睘锛?
```java
// OrderSettlementService.resolvezsManager()
public UUID resolvezsManager(Order order) {
    // 1. 妫€鏌ョ嫭瀹跺晢瀹?    ExclusiveMerchant em = exclusiveMerchantService.getEffective(order.getMerchantId());
    if (em != null) {
        return em.getzsManagerId();  // 鐙鍟嗗浼樺厛
    }

    // 2. 鍟嗗搧 鈫?娲诲姩 鈫?鎷涘晢璐熻矗浜?    Product product = productService.getById(order.getProductId());
    ColonelActivity activity = colonelActivityService.getById(product.getActivityId());
    return activity.getzsManagerId();
}
```

---

## 鍥涖€佹彁鎴愬叕寮?
### 4.1 鏍稿績鍏紡

```
鏈嶅姟璐规敹鐩?= 璁㈠崟閲戦 脳 鏈嶅姟璐圭巼锛堜互鎶栭煶鎺ュ彛杩斿洖鐨?service_fee 涓哄噯锛?鎷涘晢鎻愭垚 = 鏈嶅姟璐规敹鐩?脳 鎷涘晢鎻愭垚姣斾緥
娓犻亾鎻愭垚 = 鏈嶅姟璐规敹鐩?脳 娓犻亾鎻愭垚姣斾緥
姣涘埄 = 鏈嶅姟璐规敹鐩?- 鎷涘晢鎻愭垚 - 娓犻亾鎻愭垚
```

### 4.2 鎻愭垚閰嶇疆寮曠敤

> 璇﹁ `doc/rules/exclusive-triggers.md`

```java
// CommissionService.getRatio()
public BigDecimal getCommissionRatio(UUID userId, CommissionType type) {
    // 1. 鍏堟煡涓汉閰嶇疆
    CommissionConfig personal = commissionConfigMapper.selectByUserId(userId);
    if (personal != null && personal.getRatio(type) != null) {
        return personal.getRatio(type);
    }

    // 2. 鍐嶆煡鍏ㄥ眬閰嶇疆
    SystemConfig global = systemConfigService.getGlobalConfig();
    return global.getDefaultRatio(type);  // 蹇呴』寮曠敤閰嶇疆锛岀姝㈢‖缂栫爜
}
```

### 4.3 绂佹纭紪鐮?
```java
// 鉂?绂佹
BigDecimal ratio = new BigDecimal("0.15");

// 鉁?姝ｇ‘
BigDecimal ratio = systemConfig.getCommissionRatio(CommissionType.ZS);
```

---

## 浜斻€佷笟缁╄绠楁椂闂村彛寰?
| 绫诲瀷 | 浣跨敤瀛楁 | 璇存槑 |
|------|----------|------|
| 棰勪及/鐪嬫澘 | `create_time` | 瀹炴椂鐩戞帶锛屽寘鍚湭缁撶畻璁㈠崟 |
| 瀹為檯缁撶畻 | `settle_time` | 浠ユ姈闊崇粨绠楁椂闂翠负鍑嗭紝鐢ㄤ簬璐㈠姟鏍哥畻 |

---

## 鍏€佸缁村害绛涢€?
| 绛涢€夌淮搴?| 瀛楁 | 璇存槑 |
|----------|------|------|
| 鏃堕棿鑼冨洿 | create_time / settle_time | 鐪嬫澘鏃堕棿 vs 缁撶畻鏃堕棿 |
| 璁㈠崟鐘舵€?| status | 宸茬粨绠?鏈粨绠?宸查€€娆?|
| 鍟嗗搧 | product_id | 鏀寔澶氶€?|
| 杈句汉 | talent_id / talent_uid | 鏀寔澶氶€?|
| 鎷涘晢 | user_id (鎷涘晢瑙掕壊) | 涓氱哗褰掑睘鐨勬嫑鍟?|
| 娓犻亾 | user_id (娓犻亾瑙掕壊) | 涓氱哗褰掑睘鐨勬笭閬?|
| 娲诲姩 | activity_id | 鏀寔澶氶€?|

### 6.1 DataScope 杩囨护

> 璇﹁ `doc/rules/data-scope-lint.md`

| 瑙掕壊 | 鏁版嵁鑼冨洿 | SQL 杩囨护 |
|------|----------|----------|
| 绠＄悊鍛?| 鍏ㄩ儴 | 鏃犺繃婊?|
| 缁勯暱 | 鏈粍 + 鑷繁 | `WHERE dept_id = ? OR user_id = ?` |
| 鏅€氭垚鍛?| 浠呰嚜宸?| `WHERE user_id = ?` |

---

## 涓冦€佽鍗曟槑缁嗚〃

### 7.1 灞曠ず瀛楁

| 瀛楁 | 鏉ユ簮 | 鏁忔劅 |
|------|------|------|
| 璁㈠崟鍙?| order_id | |
| 涓嬪崟鏃堕棿 | create_time | |
| 缁撶畻鏃堕棿 | settle_time | |
| 鍟嗗搧鍚嶇О | product.name | |
| 杈句汉淇℃伅 | talent.nickname | |
| 璁㈠崟閲戦 | order_amount | |
| 鏈嶅姟璐?| service_fee | |
| 鎷涘晢 | zs_manager.real_name | |
| 娓犻亾 | channel.real_name | |
| 褰掑洜鏂瑰紡 | 鐙/鏅€?| |

### 7.2 鏁忔劅鏁版嵁澶勭悊

- **鎵嬫満鍙疯В瀵?*锛氳皟鐢?`order.batchSensitiveDataRequest` 鎺ュ彛
- **瑙ｅ瘑缁撴灉**锛氫粎杩斿洖鍓嶇灞曠ず锛?*涓嶆寔涔呭寲**
- > 璇﹁ `doc/rules/api-security.md`

---

## 鍏€佹暟鎹鍑?
| 瑙掕壊 | 瀵煎嚭鏉冮檺 |
|------|----------|
| 绠＄悊鍛?| 鉁?鍙鍑?|
| 缁勯暱 | 鉁?鍙鍑?|
| 鏅€氭垚鍛?| 鉂?浠呯湅鏉挎煡鐪嬶紝涓嶅鍑?|

---

## 涔濄€佷笟鍔＄害鏉?
| 绾︽潫 | 鏂囦欢 | 绾у埆 |
|------|------|------|
| 褰掑洜蹇呴』閫氳繃鏄犲皠琛?| `doc/rules/attribution-logic.md` | **CRITICAL** |
| 鎻愭垚姣斾緥蹇呴』寮曠敤閰嶇疆 | `doc/rules/exclusive-triggers.md` | **CRITICAL** |
| DataScope 蹇呴』杩囨护 | `doc/rules/data-scope-lint.md` | **CRITICAL** |
| 鏁忔劅鏁版嵁涓嶆寔涔呭寲 | `doc/rules/api-security.md` | **CRITICAL** |

---

## 鍗併€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璺緞 |
|------|------|
| 璁㈠崟瀹炰綋 | `backend/src/main/java/com/colonel/saas/entity/ColonelsettlementOrder.java` |
| 鏁版嵁 Controller | `backend/src/main/java/com/colonel/saas/controller/DataController.java` |
| 璁㈠崟 Mapper | `backend/src/main/java/com/colonel/saas/mapper/ColonelsettlementOrderMapper.java` |
| 褰掑洜鏈嶅姟 | `backend/src/main/java/com/colonel/saas/service/AttributionService.java` |
| 鎻愭垚閰嶇疆瀹炰綋 | `backend/src/main/java/com/colonel/saas/entity/CommissionConfig.java`锛堝緟鍒涘缓锛?|
| 鎻愭垚鏈嶅姟 | `backend/src/main/java/com/colonel/saas/service/CommissionService.java`锛堝緟鍒涘缓锛?|
| 瑙掕壊娉ㄨВ | `backend/src/main/java/com/colonel/saas/annotation/RequireRoles.java` |
| DataScope 鍒囬潰 | `backend/src/main/java/com/colonel/saas/aspect/` |
