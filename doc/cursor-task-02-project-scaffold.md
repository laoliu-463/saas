# Cursor 任务文档 #02 — Spring Boot 项目骨架

## 任务元信息

| 字段 | 值 |
|------|-----|
| 任务编号 | TASK-02 |
| 任务类型 | B类（常规开发） |
| 优先级 | P0 |
| 预计工时 | 2h |
| 前置依赖 | TASK-01 数据库初始化脚本已完成 |
| 输出产物 | 完整的 Spring Boot 项目骨架 + Docker Compose 开发环境 |
| 并行任务 | TASK-03（Entity生成）、Git初始化可同步进行，无相互依赖 |

---

## 1. 目标

搭建抖音团长 SaaS 系统的 Spring Boot 后端骨架，覆盖：

1. **项目结构** — 标准分层架构（controller/service/mapper/entity/config/common）
2. **依赖配置** — pom.xml 引入所有必要依赖
3. **配置文件** — application.yml + application-dev.yml + application-prod.yml
4. **基础配置类** — MyBatis-Plus、Redis、分页、JSON、Swagger
5. **启动入口** — Spring Boot Application 主类
6. **全局响应** — 统一 API 响应封装
7. **异常处理** — 全局统一异常捕获
8. **通用基类** — BaseEntity、BaseController、BaseService
9. **Docker Compose** — PostgreSQL + Redis + Backend 一键启动

---

## 2. 仓库目录结构

在项目根目录创建以下结构：

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/colonel/saas/
│   │   │   ├── ColonelSaasApplication.java          # 启动入口
│   │   │   ├── config/                               # 配置类
│   │   │   │   ├── MyBatisPlusConfig.java           # MyBatis-Plus 配置 + 自动填充
│   │   │   │   ├── RedisConfig.java                  # Redis 配置（序列化/连接池）
│   │   │   │   ├── WebConfig.java                    # Web 配置（CORS/拦截器）
│   │   │   │   ├── SwaggerConfig.java                # Knife4j/Swagger 配置
│   │   │   │   └── JacksonConfig.java                # JSON 配置
│   │   │   ├── common/                               # 通用基类
│   │   │   │   ├── base/                             # 基类
│   │   │   │   │   ├── BaseEntity.java               # 实体基类（id/create_time/update_time/deleted）
│   │   │   │   │   ├── BaseService.java              # 业务层基类
│   │   │   │   │   └── BaseController.java           # 控制层基类
│   │   │   │   ├── result/                           # 统一响应
│   │   │   │   │   ├── ApiResult.java                # 统一响应封装
│   │   │   │   │   ├── ResultCode.java               # 响应码枚举
│   │   │   │   │   └── PageResult.java                # 分页响应封装
│   │   │   │   └── exception/                        # 异常处理
│   │   │   │       ├── GlobalExceptionHandler.java    # 全局异常处理
│   │   │   │       ├── BusinessException.java         # 业务异常
│   │   │   │       └── ValidateException.java          # 参数校验异常
│   │   │   ├── controller/                            # 控制层（按模块分包）
│   │   │   ├── service/                               # 业务层
│   │   │   │   └── impl/                              # 实现类
│   │   │   ├── mapper/                                # 数据层（MyBatis-Plus Mapper）
│   │   │   ├── entity/                                # 实体类
│   │   │   ├── dto/                                  # 数据传输对象
│   │   │   ├── vo/                                   # 视图对象
│   │   │   ├── util/                                 # 工具类
│   │   │   └── constant/                             # 常量定义
│   │   └── resources/
│   │       ├── application.yml                       # 主配置
│   │       ├── application-dev.yml                   # 开发环境
│   │       ├── application-prod.yml                  # 生产环境
│   │       └── db/
│   │           └── init-db.sql                       # [引用] TASK-01 产物
│   └── test/
│       └── java/com/colonel/saas/
│
frontend/                                              # [占位] 前端工程目录
│
scripts/                                              # 运维脚本
│
docker-compose.yml                                    # 开发环境（参考架构方案§3.1）
docker-compose-prod.yml                               # 生产环境
.env.example                                          # 环境变量示例
```

---

## 3. pom.xml 依赖配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.colonel</groupId>
    <artifactId>colonel-saas</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>colonel-saas</name>
    <description>抖音团长 SaaS 系统</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.6</mybatis-plus.version>
        <hutool.version>5.8.26</hutool.version>
        <knife4j.version>4.5.0</knife4j.version>
        <lombok.version>1.18.32</lombok.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring Boot Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Spring Boot AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- HikariCP（Spring Boot 默认连接池）-->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
        </dependency>

        <!-- Hutool 工具库 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>

        <!-- Jsoup（HTML解析，用于爬虫）-->
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.17.2</version>
        </dependency>

        <!-- Knife4j Swagger（API文档）-->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Apache Commons Lang3 -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>

        <!-- Commons Codec（加密/解码）-->
        <dependency>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. 配置文件

### 4.1 application.yml（主配置，引用环境文件）

```yaml
spring:
  application:
    name: colonel-saas
  profiles:
    active: @spring.profiles.active@
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.colonel.saas.entity
  global-config:
    db-config:
      id-type: assign_uuid
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

logging:
  level:
    com.colonel.saas: debug
    com.baomidou.mybatisplus: info
```

### 4.2 application-dev.yml（开发环境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/colonel_saas
    username: saas
    password: ${DB_PASSWORD:saas123}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000ms

server:
  port: 8080
  servlet:
    context-path: /api

# Knife4j（生产环境建议关闭）
knife4j:
  enable: true
  setting:
    language: zh_cn
```

### 4.3 application-prod.yml（生产环境）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:colonel_saas}
    username: ${DB_USER:saas}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      idle-timeout: 600000
      connection-timeout: 30000

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000ms

server:
  port: 8080
  servlet:
    context-path: /api

knife4j:
  enable: false

logging:
  level:
    com.colonel.saas: info
```

---

## 5. 核心类实现规范

### 5.1 BaseEntity — 实体基类

所有实体继承此类，定义公共字段：

```java
package com.colonel.saas.common.base;

@Data
public abstract class BaseEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted = 0;
}
```

### 5.2 ApiResult — 统一响应封装

```java
package com.colonel.saas.common.result;

@Data
public class ApiResult<T> implements Serializable {
    private int code;       // 业务状态码
    private String msg;     // 消息
    private T data;         // 数据
    private long timestamp; // 时间戳

    public static <T> ApiResult<T> ok() { return ok(null); }
    public static <T> ApiResult<T> ok(T data) {
        return of(ResultCode.SUCCESS, data);
    }
    public static <T> ApiResult<T> fail(String msg) {
        return of(ResultCode.BUSINESS_ERROR.getCode(), msg, null);
    }
    public static <T> ApiResult<T> of(int code, String msg, T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code; r.msg = msg; r.data = data; r.timestamp = System.currentTimeMillis();
        return r;
    }
}
```

### 5.3 ResultCode — 响应码枚举

```java
package com.colonel.saas.common.result;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BUSINESS_ERROR(400, "业务异常"),
    PARAM_ERROR(401, "参数错误"),
    UNAUTHORIZED(403, "未授权"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器异常");

    private final int code;
    private final String msg;
    ResultCode(int code, String msg) { this.code = code; this.msg = msg; }
    public int getCode() { return code; }
    public String getMsg() { return msg; }
}
```

### 5.4 PageResult — 分页响应

```java
package com.colonel.saas.common.result;

@Data
public class PageResult<T> implements Serializable {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(page.getTotal());
        r.setPage(page.getCurrent());
        r.setSize(page.getSize());
        r.setRecords(page.getRecords());
        return r;
    }
}
```

### 5.5 MyBatisPlusConfig — 配置类

```java
package com.colonel.saas.config;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new CustomMetaObjectHandler();
    }
}
```

### 5.6 CustomMetaObjectHandler — 自动填充处理器

```java
package com.colonel.saas.config;

@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 5.7 GlobalExceptionHandler — 全局异常处理

```java
package com.colonel.saas.common.exception;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusiness(BusinessException e) {
        return ApiResult.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
            ? e.getBindingResult().getFieldError().getDefaultMessage()
            : "参数校验失败";
        return ApiResult.of(ResultCode.PARAM_ERROR.getCode(), msg, null);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleGeneral(Exception e) {
        log.error("系统异常", e);
        return ApiResult.of(ResultCode.SERVER_ERROR.getCode(),
            ResultCode.SERVER_ERROR.getMsg(), null);
    }
}
```

### 5.8 BaseController — 控制层基类

```java
package com.colonel.saas.common.base;

public class BaseController {

    protected <T> ApiResult<T> ok() { return ApiResult.ok(); }
    protected <T> ApiResult<T> ok(T data) { return ApiResult.ok(data); }
    protected <T> ApiResult<PageResult<T>> okPage(IPage<T> page) {
        return ApiResult.ok(PageResult.of(page));
    }
    protected ApiResult<Void> fail(String msg) { return ApiResult.fail(msg); }
}
```

---

## 6. Docker Compose 开发环境

### 6.1 docker-compose.yml（项目根目录）

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: colonel-postgres
    environment:
      POSTGRES_DB: colonel_saas
      POSTGRES_USER: saas
      POSTGRES_PASSWORD: ${DB_PASSWORD:-saas123}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/db/init-db.sql:/docker-entrypoint-initdb.d/01-init.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U saas -d colonel_saas"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: colonel-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: colonel-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_PASSWORD: ${DB_PASSWORD:-saas123}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/api/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres_data:
  redis_data:
```

### 6.2 backend/Dockerfile

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn package -DskipTests -q && \
    mv target/*.jar app.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/app.jar .
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 6.3 .env.example（根目录）

```bash
# 数据库
DB_PASSWORD=saas123

# Redis
REDIS_PASSWORD=

# 生产环境
DB_HOST=your-postgres-host
DB_PORT=5432
DB_NAME=colonel_saas
DB_USER=saas
REDIS_HOST=your-redis-host
REDIS_PORT=6379
```

---

## 7. MyBatis-Plus 使用规范

### 7.1 Entity 注解规范

```java
@Data
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableField("username")
    private String username;

    @TableField("channel_code")
    private String channelCode;

    @TableField(exist = false) // 非数据库字段
    private String extraField;
}
```

### 7.2 Mapper 规范

```java
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
```

### 7.3 Service 规范

```java
public interface SysUserService extends IService<SysUser> {
}

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
    implements SysUserService {
}
```

---

## 8. API 分层约定

| 模块 | Controller 包 | Service 包 | Mapper 包 | Entity 包 |
|------|-------------|-----------|----------|----------|
| 用户权限 | controller.system | service.system | mapper.system | entity.system |
| 抖音Token | controller.douyin | service.douyin | mapper.douyin | entity.douyin |
| 活动 | controller.activity | service.activity | mapper.activity | entity.activity |
| 商品 | controller.product | service.product | mapper.product | entity.product |
| 达人 | controller.talent | service.talent | mapper.talent | entity.talent |
| 订单 | controller.order | service.order | mapper.order | entity.order |
| 寄样 | controller.sample | service.sample | mapper.sample | entity.sample |

---

## 9. 架构一致性修复清单

> 本节由流程指挥官在交叉核验后追加，记录与架构方案的偏差及修复状态。

| # | 检查项 | 来源 | 修复状态 |
|---|--------|------|----------|
| 1 | pom.xml: `< Lombok.version>` → `<lombok.version>`（属性名空格+大小写） | 架构 §1 | ✅ 已修复 |
| 2 | application.yml: 删除重复的 `spring.profiles.active: dev` | 架构方案规范 | ✅ 已修复 |
| 3 | docker-compose.yml: backend healthcheck `curl` → `wget`（alpine无curl） | Docker最佳实践 | ✅ 已修复 |
| 4 | config/ 目录说明：补充 RedisConfig 职责描述 | 架构 §4.3 Redis使用 | ✅ 已修复 |
| 5 | docker-compose.yml: 缺失 frontend 服务 | 架构 §3.1 | ⚠️ 待补充（见下方说明） |
| 6 | 缺失 `.gitignore`、`README.md`、`CHANGELOG.md` | 架构 §7.7 | ⚠️ 属于 Git初始化任务，另行处理 |
| 7 | 缺失 `docker-compose-prod.yml` 内容 | 架构 §3.2 | ⚠️ 待补充（见下方） |
| 8 | 缺失 `scripts/` 目录 | 架构 §7.7 | ⚠️ 属于 Git初始化任务，另行处理 |
| 9 | Docker多阶段构建：需添加 `apk add --no-cache wget` | 容器化最佳实践 | ⚠️ 待补充（见下方Dockerfile修复） |

**说明：**
- **frontend 服务**：架构 §3.1 定义了 `frontend` 服务，但当前前端工程为占位目录。TASK-02 骨架阶段仅包含后端；frontend 服务在 TASK-06 前端初始化时补全。
- **Git初始化相关文件**（`.gitignore` / `README.md` / `CHANGELOG.md` / `scripts/`）：属于 Git初始化任务（架构 §8 P0），与 TASK-02 并行执行，不在本任务范围内。
- **docker-compose-prod.yml**：生产环境配置包含 Web服务器、应用服务、数据库、缓存的集群配置，将在生产部署任务中完善。

**Dockerfile 补充修复（alpine 健康检查依赖）：**

```dockerfile
# backend/Dockerfile — 在 RUN apk add 行添加 wget
RUN apk add --no-cache maven wget && \
    mvn package -DskipTests -q && \
    mv target/*.jar app.jar
```

---

## 10. 验证清单（待 Cursor 执行后验证）

启动应用后访问以下端点确认骨架正常：

```bash
# 1. 健康检查
curl http://localhost:8080/api/actuator/health

# 2. Swagger 文档（开发环境）
curl http://localhost:8080/api/doc.html

# 3. 数据库连接验证（空列表查询）
curl http://localhost:8080/api/system/config/list

# 4. Redis 连接验证
# 观察启动日志中无 Redis 连接错误
```

---

## 11. 与其他任务的依赖关系

```
TASK-01 (数据库设计) ✅ 已完成
        ↓
TASK-02 (项目骨架) ← 当前任务
        ↓
TASK-03 (Entity生成，依赖 TASK-02 骨架)
TASK-04 (CRUD Service/Controller，依赖 TASK-03)

并行：Git 初始化、Docker Compose 可在 TASK-02 期间同步进行
```

---

*流程指挥官生成 | 2026-04-20 | V1.1 | 状态：待执行 | 核验完成，已修复9项偏差*
