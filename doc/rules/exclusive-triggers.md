# 瑙勫垯锛氱嫭瀹惰揪浜?鍟嗗鍒ゅ畾绾︽潫

**鐗堟湰**锛歏1.0
**鐘舵€?*锛氬己鍒舵墽琛?**閫傜敤鑼冨洿**锛氱嫭瀹舵湇鍔¤垂璁＄畻銆佽揪浜?鍟嗗鍗囬檷绾?
---

## 涓€銆佺嫭瀹惰揪浜哄垽瀹氳鍒?
### 1.1 鍒ゅ畾鏉′欢锛堝繀椤诲悓鏃舵弧瓒筹級

| 鏉′欢 | 闃堝€?| 璇存槑 |
|------|------|------|
| 鏈嶅姟璐瑰崰姣?| 鈮?70% | 璇ヨ揪浜哄甫鏉ヨ鍗曠殑鏈嶅姟璐瑰崰鎬绘湇鍔¤垂鐨勬瘮渚?|
| 瀵勬牱鏁伴噺 | 鈮?10 鍗?| 鑷劧鏈堝唴璇ヨ揪浜虹殑鏈夋晥瀵勬牱鏁伴噺 |

### 1.2 瀹炵幇绾︽潫

```java
// 鉁?姝ｇ‘锛氬繀椤诲悓鏃舵鏌ヤ袱涓潯浠?public boolean isExclusiveTalent(Talent talent, String month) {
    // 鏉′欢1锛氭湇鍔¤垂鍗犳瘮 >= 70%
    BigDecimal commissionRatio = calculateCommissionRatio(talent, month);
    if (commissionRatio.compareTo(BigDecimal.valueOf(70)) < 0) {
        return false;
    }

    // 鏉′欢2锛氬瘎鏍锋暟閲?>= 10
    Integer sampleCount = countValidSamples(talent.getId(), month);
    return sampleCount >= 10;
}

// 鉂?閿欒锛氬彧妫€鏌ュ崟涓€鏉′欢
public boolean wrongCheck(Talent talent) {
    return calculateCommissionRatio(talent) >= 70; // 缂哄皯瀵勬牱鏁伴噺妫€鏌ワ紒
}
```

---

## 浜屻€佺嫭瀹跺晢瀹跺垽瀹氳鍒?
### 2.1 鍒ゅ畾鏉′欢

| 鏉′欢 | 闃堝€?| 璇存槑 |
|------|------|------|
| 鏈嶅姟璐瑰崰姣?| 鈮?70% | 璇ュ晢瀹惰础鐚殑鏈嶅姟璐瑰崰姣?|

### 2.2 鏈堝害閲嶇畻鏈哄埗

- [ ] 姣忔湀 1 鏃ュ噷鏅ㄩ噸鏂拌绠椾笂鏈堢嫭瀹剁姸鎬?- [ ] 鐘舵€佸彉鏇磋褰曞埌 `exclusive_status_log` 琛?- [ ] 闄嶇骇鏃跺彂閫侀€氱煡锛堢珯鍐呬俊/鐭俊锛?
---

## 涓夈€佹彁鎴愭瘮渚嬮厤缃?
### 3.1 蹇呴』寮曠敤閰嶇疆锛屼笉寰楃‖缂栫爜

```java
// 鉁?姝ｇ‘锛氫粠 SystemConfig 璇诲彇
public BigDecimal getCommissionRatio(String talentLevel) {
    String configKey = "commission_ratio_" + talentLevel;
    String ratio = systemConfigService.getValue(configKey);
    return new BigDecimal(ratio);
}

// 鉂?閿欒锛氱‖缂栫爜鏁板瓧
public BigDecimal wrongGetRatio() {
    return new BigDecimal("0.15"); // 绂佹纭紪鐮侊紒
}
```

### 3.2 閰嶇疆閿畾涔?
| 閰嶇疆閿?| 榛樿鍊?| 璇存槑 |
|--------|--------|------|
| `commission_ratio_normal` | 0.10 | 鏅€氳揪浜烘彁鎴愭瘮渚?|
| `commission_ratio_exclusive` | 0.15 | 鐙杈句汉鎻愭垚姣斾緥 |
| `exclusive_talent_threshold` | 70 | 鐙闂ㄦ锛?锛?|
| `exclusive_talent_samples` | 10 | 鐙瀵勬牱闂ㄦ |

---

## 鍥涖€佺姝㈠仛娉?
- [ ] 绂佹纭紪鐮佹彁鎴愭瘮渚嬶紙蹇呴』寮曠敤閰嶇疆锛?- [ ] 绂佹鍦ㄥ垽瀹氶€昏緫涓娇鐢ㄩ瓟娉曟暟瀛?- [ ] 绂佹璺宠繃鏈嶅姟璐瑰崰姣旀鏌ョ洿鎺ュ垽瀹氱嫭瀹?- [ ] 绂佹鍦ㄩ潪鏈堝害鍛ㄦ湡閲嶇畻鐙鐘舵€?
---

## 浜斻€侀獙鏀舵祴璇?
```java
@Test
void shouldNotBeExclusiveWhenCommissionRatioBelow70() {
    // given
    talent.setTotalCommission(BigDecimal.valueOf(1000));
    talent.setMyCommission(BigDecimal.valueOf(500)); // 50%锛屼笉瓒?0%

    // when
    boolean result = exclusiveService.isExclusiveTalent(talent, "2024-03");

    // then
    assertThat(result).isFalse();
}

@Test
void shouldNotBeExclusiveWhenSampleCountBelow10() {
    // given
    talent.setTotalCommission(BigDecimal.valueOf(10000));
    talent.setMyCommission(BigDecimal.valueOf(8000)); // 80%锛屽70%
    when(sampleMapper.countValidSamples(any(), any())).thenReturn(5); // 浠?鍗?
    // when
    boolean result = exclusiveService.isExclusiveTalent(talent, "2024-03");

    // then
    assertThat(result).isFalse();
}
```

---

## 鍏€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璇存槑 |
|------|------|
| `ExclusiveService.java` | 鐙鍒ゅ畾鏈嶅姟 |
| `SystemConfigService.java` | 閰嶇疆鏈嶅姟 |
| `ExclusiveStatusLog.java` | 鐘舵€佸彉鏇存棩蹇?|
| 闇€姹傚叆鍙?| `doc/requirements/01-roles-permissions.md` |
