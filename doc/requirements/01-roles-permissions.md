# 闇€姹傦細鐢ㄦ埛瑙掕壊涓庢潈闄愪綋绯?
**鏂囨。鐗堟湰**锛歏1.0
**鐘舵€?*锛氬凡瀹氱
**鏅鸿兘浣撳叆鍙?*锛氱洿鎺ヨ鍙栨鏂囦欢

---

## 涓€銆佸矖浣嶅畾涔夛紙6澶ц鑹诧級

| 瑙掕壊 | 鏍稿績鑱岃矗 | 鏁版嵁鑼冨洿 |
|------|----------|----------|
| **绠＄悊鍛?* | 绯荤粺閰嶇疆銆佷汉鍛樼鐞嗐€佹潈闄愬垎閰?| 鍏ㄩ儴鏁版嵁 |
| **鎷涘晢缁勯暱** | 缁戝畾娲诲姩銆佸垎閰嶅晢鍝併€佺鐞嗘嫑鍟嗗洟闃?| 鏈粍鎷涘晢 + 鑷繁鐨勬暟鎹?|
| **鎷涘晢** | 瀹℃牳鍟嗗搧銆佽ˉ鍏呬俊鎭€佸鏍稿瘎鏍?| 鑷繁璐熻矗鐨勫晢鍝?娲诲姩/瀵勬牱 |
| **娓犻亾缁勯暱** | 绠＄悊娓犻亾鍥㈤槦銆佹暟鎹眹鎬?| 鏈粍娓犻亾 + 鑷繁鐨勬暟鎹?|
| **娓犻亾** | 杈句汉瀵规帴銆佺敵璇峰瘎鏍枫€佽窡杩涗骇鍑?| 鑷繁鐨勮揪浜?瀵勬牱/涓氱哗 |
| **杩愯惀** | 鐗╂祦褰曞叆銆佽緟鍔╂搷浣?| 鎸夐渶閰嶇疆 |

---

## 浜屻€佹潈闄愰厤缃煩闃?
### 2.1 鏁版嵁鑼冨洿鎺у埗锛圖ataScope锛?
```
1 = 鏈汉鏁版嵁锛坲ser_id = current_user锛?2 = 鏈粍鏁版嵁锛坉ept_id = current_user's dept锛?3 = 鍏ㄩ儴鏁版嵁锛堜笉闄愶級
```

### 2.2 瑙掕壊-鏉冮檺鏄犲皠

| 瑙掕壊 | 鏁版嵁鑼冨洿 | 鍙厤缃搷浣?|
|------|----------|------------|
| 绠＄悊鍛?| 3锛堝叏閮級 | 鎵€鏈?CRUD + 鏉冮檺绠＄悊 |
| 鎷涘晢缁勯暱 | 2锛堟湰缁勶級 | 娲诲姩缁戝畾銆佸晢鍝佸垎閰嶃€佹煡鐪嬬粍鍐呬笟缁?|
| 鎷涘晢 | 1锛堟湰浜猴級 | 鍟嗗搧瀹℃牳銆佸瘎鏍峰鏍搞€佹煡鐪嬩釜浜轰笟缁?|
| 娓犻亾缁勯暱 | 2锛堟湰缁勶級 | 鏌ョ湅缁勫唴涓氱哗銆佺鐞嗙粍鍛?|
| 娓犻亾 | 1锛堟湰浜猴級 | 杈句汉璁ら銆佺敵璇峰瘎鏍枫€佹煡鐪嬩釜浜烘暟鎹?|
| 杩愯惀 | 鑷畾涔?| 鐗╂祦褰曞叆銆佸彂璐ф搷浣?|

---

## 涓夈€佸彲閰嶇疆瑙勫垯锛圫ystemConfig 琛級

| 閰嶇疆閿?| 榛樿鍊?| 璇存槑 |
|--------|--------|------|
| `sample_limit_days` | 7 | 鍚屼竴娓犻亾+鍚屼竴杈句汉+鍚屼竴鍟嗗搧鐨勯噸澶嶇敵璇烽棿闅旓紙澶╋級 |
| `sample_limit_enabled` | true | 鏄惁鍚敤瀵勬牱闄愬埗 |
| `exclusive_talent_threshold` | 70 | 鐙杈句汉鏈嶅姟璐瑰崰姣旈槇鍊硷紙%锛?|
| `exclusive_talent_samples` | 10 | 鐙杈句汉鏈堝瘎鏍锋暟閲忛槇鍊?|
| `exclusive_merchant_threshold` | 70 | 鐙鍟嗗鏈嶅姟璐瑰崰姣旈槇鍊硷紙%锛?|
| `talent_protection_days` | 30 | 璁ら鍚庢棤浜у嚭鑷姩閲婃斁鏃堕棿锛堝ぉ锛?|

---

## 鍥涖€佸紑鍙戠害鏉?
### 4.1 蹇呴』閬靛畧

- [ ] 鎵€鏈夋秹鍙婃暟鎹寖鍥存煡璇㈢殑 Service 鏂规硶锛屽繀椤讳紶鍏?`userId` 鍜?`deptId` 鍙傛暟
- [ ] 鏌ヨ鏂规硶鍛藉悕瑙勮寖锛歚listByDataScope(DataScope scope, ...)`, `countByDataScope(...)`
- [ ] 鏉冮檺鏍￠獙鍦?Controller 灞傜粺涓€澶勭悊锛屼笉鍦?Service 灞傛暎钀?
### 4.2 绂佹鍋氭硶

- [ ] 绂佹鍦?Controller 灞傜洿鎺ヨ繑鍥炲畬鏁存暟鎹泦锛堜笉缁忚繃鏁版嵁鑼冨洿杩囨护锛?- [ ] 绂佹纭紪鐮佺敤鎴?ID 鎴栭儴闂?ID

---

## 浜斻€侀獙鏀舵爣鍑?
1. **鍗曞厓娴嬭瘯**锛氶獙璇?6 涓鑹茬殑鏁版嵁鑼冨洿杩囨护閫昏緫姝ｇ‘
2. **闆嗘垚娴嬭瘯**锛氭ā鎷熺鐞嗗憳銆佺粍闀裤€佺粍鍛樼櫥褰曪紝楠岃瘉鍚勮嚜鍙兘鐪嬪埌琚巿鏉冪殑鏁版嵁
3. **閰嶇疆鐢熸晥**锛氫慨鏀?SystemConfig 鍚庯紝鏉冮檺瑙勫垯绔嬪嵆鐢熸晥锛堟棤闇€閲嶅惎锛?
---

## 鍏€佺浉鍏虫枃浠剁储寮?
| 鏂囦欢 | 璺緞 | 璇存槑 |
|------|------|------|
| 瑙掕壊瀹炰綋 | `backend/src/main/java/com/colonel/saas/entity/SysRole.java` | 瑙掕壊瀹炰綋锛屽惈 permissions JSONB |
| 鐢ㄦ埛瀹炰綋 | `backend/src/main/java/com/colonel/saas/entity/SysUser.java` | 鐢ㄦ埛瀹炰綋锛屽惈 deptId銆乧hannelCode |
| 瑙掕壊甯搁噺 | `backend/src/main/java/com/colonel/saas/constant/RoleCodes.java` | 6澶ц鑹茬爜甯搁噺 |
| 璁よ瘉鏈嶅姟 | `backend/src/main/java/com/colonel/saas/auth/service/AuthService.java` | JWT 璁よ瘉 + 鐧诲綍 |
| 瑙掕壊鏈嶅姟 | `backend/src/main/java/com/colonel/saas/auth/service/SysRoleService.java` | 瑙掕壊 CRUD |
| 鐢ㄦ埛鏈嶅姟 | `backend/src/main/java/com/colonel/saas/auth/service/SysUserService.java` | 鐢ㄦ埛 CRUD + 瑙掕壊鍒嗛厤 |
| DataScope 鍒囬潰 | `backend/src/main/java/com/colonel/saas/aspect/` | DataScope 鏉冮檺鎷︽埅鍒囬潰 |
| 鍓嶇 RBAC | `frontend/src/constants/rbac.ts` | 鍓嶇瑙掕壊鏉冮檺鐮?+ hasAccess |
| 鍓嶇璺敱瀹堝崼 | `frontend/src/router/index.ts` | 瑙掕壊璺敱瀹堝崼 + 棣栭〉璺宠浆 |
| 楠岃瘉瑙勫垯 | `doc/rules/data-scope-lint.md` | Lint 瑙勫垯锛氱姝㈢粫杩囨暟鎹寖鍥?|
