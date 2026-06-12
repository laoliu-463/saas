# DDD-PRODUCT-003 Facade 璺敱鎶ュ憡

鏃堕棿锛?026-06-12
浠诲姟锛欱atch3 Replace 鈥?QuickSample 鍒?Facade/Port

## 鍙樻洿鎽樿

| 鍘熻皟鐢ㄧ偣 | 鏂拌皟鐢ㄧ偣 | 寮€鍏?| 鍥為€€ |
|---------|---------|------|------|
| `ProductController` 鈫?`ProductQuickSampleService` | 鈫?`ProductQuickSampleApplicationService` 鈫?`ProductQuickSampleService` | `ddd.refactor.product-quick-sample.enabled` | 瀛愬紑鍏?false 鏃?1:1 濮旀淳鏃ф湇鍔?|
| QuickSample 鍒涘缓 | 寮€鍏冲紑鍚椂鍏?`LegacyProductDomainFacade` 鏍￠獙 | 鍚屼笂 | 鍏抽棴寮€鍏宠烦杩?Facade |

## 鏂板/淇敼

- `ProductQuickSampleApplicationService`锛堟柊澧?ApplicationService锛?- `LegacyProductDomainFacade`锛堝凡瀛樺湪锛岃皟鏁村揩鐓ц DTO锛?- `ProductSnapshotReadDTO`锛堟柊澧?DTO锛?- `ProductController` / `ProductQuickSampleService`锛堣皟鏁村娲撅級
- `DddProduct003ProductRoutingTest`锛堟柊澧炴灦鏋勬姢鏍忔祴璇曪級
- `QuickSampleApplyTest`锛堣皟鏁村崟娴嬶級

## 楠岃瘉

- Backend build: PASS锛坄mvn -f backend/pom.xml -DskipTests package`锛?- Backend scope preflight: PASS锛坄npm run e2e:real-pre:p0:preflight`锛?- Docker: backend-real-pre healthy
- 涓?evidence锛歚harness/reports/evidence-20260612-131449.md`
- 寰呰窇锛歚mvn test` + `agent-do -Scope backend` 鍏ㄩ噺鍥炲綊

## 缁撹

闃舵鎬?**PASS**锛堝緟 agent-do 瀹氱锛