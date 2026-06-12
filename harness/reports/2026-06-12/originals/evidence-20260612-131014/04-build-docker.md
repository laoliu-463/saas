---
split_from: evidence-20260612-131014.md
split_task: HARNESS-DOC-GC-OPTIMIZE-003 Step 3
split_at: 2026-06-12T20:10:00+08:00
original_lines: 249
section_index: see reports/2026-06-12/originals/evidence-20260612-131014/_index.md
audit_trail: see reports/2026-06-12/originals/evidence-20260612-131014/evidence-20260612-131014.md-original.md
---

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

### docker compose ps

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O鈥?   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.鈥?   frontend-real-pre   46 minutes ago       Up 46 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s鈥?   postgres-real-pre   41 hours ago         Up 47 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s鈥?   redis-real-pre      6 days ago           Up 47 minutes (healthy)       6379/tcp
~~~

### docker ps

~~~text
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up 46 minutes (healthy)       127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 47 minutes (healthy)       5432/tcp
saas-active-redis-real-pre-1      Up 47 minutes (healthy)       6379/tcp
saas-test-backend-1               Up 47 minutes (healthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
saas-test-postgres-1              Up 47 minutes (healthy)       0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp
saas-test-redis-1                 Up 47 minutes (healthy)       6379/tcp
~~~

## Health Check Result
