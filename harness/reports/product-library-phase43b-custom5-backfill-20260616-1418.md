# Phase 4-3B CUSTOM_5 鍥炶ˉ鎶ュ憡

- 鎵ц鏃堕棿锛?026-06-16
- 鐜锛歳eal-pre
- 缁撹锛歚PASS`锛? 娲诲姩灏忔壒閲忕湡瀹?backfill 宸插畬鎴愶級
- 鏄庣‘璇存槑锛氭湰娆′负 5 娲诲姩灏忔壒閲忥紝浠呬负 `CUSTOM_ACTIVITY_IDS`锛岄潪 `RECENT_30D maxActivities=20`銆侀潪 `RECENT_30D maxActivities=10`銆侀潪鍏ㄩ噺銆?
## 1. 鍓嶇疆妫€鏌ワ紙鎵ц鏃剁偣锛?- `product_sync_job_log` RUNNING 鏁帮細`0`
- Redis `product:backfill*` lock锛歚0`
- backend health锛歚UP`
- `dry-run` /backfill `5` 鐨勬渶杩戞垚鍔熶綔涓氾細`product-backfill-0d258d25-399b-4584-9f76-ee8f20e3c7d9`

## 2. 鍩虹嚎锛堝紑濮嬫墽琛?4-3B 鍓嶏級
- `admin/counts`锛歚snapshotTotal=10151, relationTotal=10151, distinctProductTotal=8953, displayingTotal=3324, pendingTotal=346, hiddenTotal=6481`
- `/api/products` total锛歚3324`
- product_snapshot锛歚10151`
- product_operation_state锛歚10151`
- duplicate锛堟椿鍔?鍟嗗搧閲嶅瀵癸級锛歚0`

## 3. dry-run 5
- dry-run 5 SUCCESS jobId锛歚product-backfill-0d258d25-399b-4584-9f76-ee8f20e3c7d9`
- status锛歚SUCCESS`
- scope锛歚RECENT_30D`
- activitiesScanned锛歚5`
- activitiesSuccess锛歚5`
- activitiesFailed锛歚0`
- stopReasonStats锛歚{"DONE_NO_MORE":5}`
- 璇存槑锛氳 dry-run 鎴愬姛杩斿洖鏈惡甯﹀畬鏁?`activityIds` 瀛楁

## 4. 鐪熷疄鍥炶ˉ锛圕USTOM_ACTIVITY_IDS锛?- real async jobId锛歚product-backfill-bd65c240-694f-45c7-8786-3352cb666f57`
- status锛歚SUCCESS`
- scope锛歚CUSTOM_ACTIVITY_IDS`
- activitiesScanned锛歚5`
- activitiesSuccess锛歚5`
- activitiesFailed锛歚0`
- inserted/updated/skipped/failed锛歚0/1483/0/0`
- stopReasonStats锛歚{"DONE_NO_MORE":5}`
- lockWaitCount锛歚0`
- deadlockRetryCount锛歚0`
- activityIds锛堟潵鑷 job 鐨?request_params锛夛細`["3916506","3929905","3929906","3920684","3891192"]`

## 5. 杞璁板綍锛堟牳蹇冨瓧娈碉級
- jobId锛歚product-backfill-bd65c240-694f-45c7-8786-3352cb666f57`
- status锛歚SUCCESS`
- failed锛歚0`
- activitiesFailed锛歚0`
- stopReasonStats锛歚{"DONE_NO_MORE":5}`
- errorMessage锛歚绌篳
- lockWaitCount锛歚0`
- deadlockRetryCount锛歚0`

## 6. 楠屾敹鍚庡彛寰?- `RUNNING job`锛歚0`
- Redis lock锛歚0`
- backend health锛歚UP`
- `product_snapshot`锛歚10161`
- `product_operation_state`锛歚10161`
- duplicate锛堟椿鍔?鍟嗗搧閲嶅瀵癸級锛歚0`
- `/api/products` total锛歚3325`
- admin counting锛堟敹鍙ｅ悗锛夛細`snapshotTotal=10161, relationTotal=10161, distinctProductTotal=8962, displayingTotal=3325, pendingTotal=365, hiddenTotal=6471`

## 7. 缁撹
- `status=SUCCESS`
- `failed=0`
- `activitiesFailed=0`
- `stopReasonStats` 浠呬负 `DONE_NO_MORE`
- `RUNNING job=0`
- `Redis lock=0`
- `duplicate=0`
- `DISPLAYING total == /api/products total == 3325`

## 8. 涓嬩竴闃舵鍒ゆ柇
- 婊¤冻鏈 4-3B 5 娲诲姩灏忔壒閲?PASS 鐩爣銆?- 鍏佽杩涘叆 Phase 4-3C銆?
