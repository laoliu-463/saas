# 瑙勫垯锛氬綊鍥犻€昏緫绾︽潫

**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氭墍鏈夋秹鍙婅鍗曞綊鍥犵殑浠ｇ爜

---

## 涓€銆佸綊鍥犳牳蹇冪害鏉?
### 1.1 蹇呴』閫氳繃 pick_source 鏄犲皠 channel_id

```java
// 鉁?姝ｇ‘锛氬繀椤婚€氳繃 PickSourceMapping 琛ㄥ綊鍥?public UUID resolveChannelId(String pickSource) {
    PickSourceMapping mapping = pickSourceMappingMapper
        .selectOne(Wrappers.<PickSourceMapping>lambdaQuery()
            .eq(PickSourceMapping::getPickSource, pickSource));
    return mapping != null ? mapping.getChannelId() : null;
}

// 鉂?閿欒锛氱洿鎺ヤ粠 pick_source 瑙ｆ瀽 channel_id
public UUID wrongResolve(String pickSource) {
    // 绂佹锛氫粠 pick_source 瀛楃涓茶В鏋?channel_id
    String channelId = pickSource.split("_")[0]; // 绂佹锛?    return UUID.fromString(channelId);
}
```

### 1.2 pick_source 鏍煎紡楠岃瘉

| 瀛楁 | 绾︽潫 | 楠岃瘉姝ｅ垯 |
|------|------|----------|
| pick_source | 闈炵┖锛屸墹128瀛楃 | `^[a-zA-Z0-9_-]{1,128}$` |
| pick_extra | 鈮?0瀛楃 | `^.{0,20}$` |

---

## 浜屻€佸綊鍥犺Е鍙戞椂鏈?
### 2.1 璁㈠崟鍐欏叆鏃跺繀椤诲綊鍥?
```java
// 璁㈠崟鍏ュ簱鍓嶅繀椤诲畬鎴愬綊鍥?@PrePersist
public void attributeOrder() {
    if (this.channelId == null && this.pickSource != null) {
        this.channelId = resolveChannelId(this.pickSource);
        if (this.channelId == null) {
            throw new BusinessException("褰掑洜澶辫触锛歱ick_source 鏈壘鍒板搴旀笭閬?);
        }
    }
}
```

### 2.2 绂佹鏃犲綊鍥犺鍗?
- [ ] 绂佹灏?`channel_id` 涓?NULL 鐨勮鍗曞啓鍏?`colonelsettlement_order`
- [ ] 绂佹鍦ㄥ綊鍥犲畬鎴愬墠鎵ц涓氬姟鎿嶄綔锛堝璁＄畻鎻愭垚锛?
---

## 涓夈€佺煭閾剧敓鎴愮害鏉?
### 3.1 pick_source 鐢熸垚瑙勫垯

```java
// pick_source = {userId鍓嶇紑}_{shortId}_{timestamp}
public String generatePickSource(UUID userId, String activityId) {
    String shortId = generateUniqueShortId();
    return String.format("%s_%s_%d",
        userId.toString().substring(0, 8).toLowerCase(),
        shortId,
        System.currentTimeMillis() / 1000
    );
}
```

### 3.2 short_id 鍞竴鎬т繚璇?
- [ ] `short_id` 蹇呴』鍦?`pick_source_mapping` 琛ㄤ腑 UNIQUE
- [ ] 閲嶅鐢熸垚鏃跺繀椤诲鐞?`DuplicateKeyException`
- [ ] 蹇呴』浣跨敤鏁版嵁搴?Sequence 鎴栧垎甯冨紡 ID 鐢熸垚鍣?
---

## 鍥涖€侀獙鏀舵祴璇?
```java
@Test
void shouldAttributeOrderThroughPickSourceMapping() {
    // given
    String pickSource = "usr_abc12345_1712000000";

    // when
    UUID channelId = attributionService.resolveChannelId(pickSource);

    // then
    assertThat(channelId).isNotNull();
}

@Test
void shouldRejectOrderWithoutChannelId() {
    // given
    Order order = new Order();
    order.setPickSource("valid_pick_source");
    order.setChannelId(null); // 鏈綊鍥?
    // when/then
    assertThatThrownBy(() -> orderService.save(order))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("褰掑洜澶辫触");
}
```

---

## 浜斻€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `PickSourceMapping.java` | 褰掑洜鏄犲皠瀹炰綋 |
| `AttributionService.java` | 褰掑洜鏈嶅姟 |
| `Order.java` | 璁㈠崟瀹炰綋 |
| 闇€姹傚叆鍙?| `doc/requirements/03-api-specs.md` |
