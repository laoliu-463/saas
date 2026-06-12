# DDD-TALENT-003 Facade 璺敱鎶ュ憡

鏃堕棿锛?026-06-12
鍒嗘敮锛歚feature/ddd/DDD-SAMPLE-005-FIX-sample-agent`
浠诲姟锛欱atch3 Replace 鈥?TalentController 鍒?Facade

## 鍙樻洿鎽樿

| 鍘熻皟鐢ㄧ偣 | 鏂拌皟鐢ㄧ偣 | 寮€鍏?| 鍥為€€ |
|---------|---------|------|------|
| `TalentController` 鈫?`TalentQueryService` | `TalentController` 鈫?`TalentQueryApplicationService` 鈫?`TalentQueryService` | `ddd.refactor.enabled` + `ddd.refactor.talent-facade.enabled` | 瀛愬紑鍏?false 鏃?1:1 濮旀淳鏃ф湇鍔?|
| detail / assertCanOperate | 寮€鍏冲紑鍚椂鍏?`TalentDomainFacade.existsById` | 鍚屼笂 | 鍏抽棴寮€鍏宠烦杩?Facade |

## 鏂板鏂囦欢

- `domain/talent/application/TalentQueryApplicationService.java`
- `architecture/DddTalent003TalentRoutingTest.java`

## 楠岃瘉

- `DddTalent003TalentRoutingTest` 鈥?寮€鍏?on/off 涓夊垎鏀?- `TalentControllerTest` 鈥?鏋勯€犱笌 page 濮旀墭鏇存柊
- 寰呰窇锛歚mvn test` + `agent-do -Scope backend`

## 缁撹

闃舵鎬?**PASS**锛堝崟娴嬬豢 + agent-do 鍚庡畾绋匡級
