---
split_from: evidence-20260612-130919.md
split_task: HARNESS-DOC-GC-OPTIMIZE-003 Step 3
split_at: 2026-06-12T20:00:00+08:00
original_lines: 1390
section_lines: 32
audit_trail: see reports/2026-06-12/originals/evidence-20260612-130919-original.md
section_index: see reports/2026-06-12/originals/_index.md
note: |
  鏈枃浠舵槸 evidence 鎶ュ憡鐨?6 娈垫媶鍒嗕箣涓€銆?  鍘熷 1390 琛?evidence-20260612-130919.md 鍥?audit 瀵嗗害鏃犳硶鍗曟枃浠?鈮?200 琛屻€?  鏈垎娈佃涓?evidence 绫昏眮鍏嶏紙GC-003 manifest 鎺ュ彈锛夈€?---


~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

### docker compose ps

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O鈥?   backend-real-pre    27 seconds ago   Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.鈥?   frontend-real-pre   45 minutes ago   Up 45 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s鈥?   postgres-real-pre   41 hours ago     Up 46 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s鈥?   redis-real-pre      6 days ago       Up 46 minutes (healthy)   6379/tcp
~~~

### docker ps

~~~text
NAMES                             STATUS                    PORTS
saas-active-backend-real-pre-1    Up 24 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 45 minutes (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 46 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 46 minutes (healthy)   6379/tcp
saas-test-backend-1               Up 46 minutes (healthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 46 minutes (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 46 minutes (healthy)   6379/tcp
~~~

## Health Check Result
