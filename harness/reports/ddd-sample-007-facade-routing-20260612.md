# DDD-SAMPLE-007 Facade 璺敱鎶ュ憡

鏃堕棿锛?026-06-12
浠诲姟锛欱atch3 Replace 鈥?SampleController 鍒?Facade/Port

## 鍙樻洿鎽樿

| 鍘熻皟鐢ㄧ偣 | 鏂拌皟鐢ㄧ偣 | 寮€鍏?| 鍥為€€ |
|---------|---------|------|------|
| `SampleController` 鈫?Query/Command Service | 鈫?`SampleQueryApplicationService` / `SampleCommandApplicationService` | `ddd.refactor.sample-application.enabled` | 瀛愬紑鍏?false 鏃?1:1 濮旀淳 |
| 鎸?ID 璇?鍐?| 寮€鍏冲紑鍚椂鍏?`SampleDomainFacade.existsById` | 鍚屼笂 | 鍏抽棴寮€鍏宠烦杩?Facade |

## 鏂板

- `SampleDomainFacade` + `LegacySampleDomainFacade`
- `SampleQueryApplicationService` / `SampleCommandApplicationService`

## 楠岃瘉

- `DddSample007SampleRoutingTest` / `LegacySampleDomainFacadeTest` / `SampleControllerTest`

## 缁撹

闃舵鎬?**PASS**锛堝緟 agent-do 瀹氱锛?