# Game Log Service - Code Wiki

## 1. 项目概述

### 1.1 项目简介

**Game Log Service** 是一个高性能的游戏日志采集与查询服务，专门用于接收游戏服务器的 HTTP 请求，将游戏日志异步写入 MySQL 数据库，并支持按条件查询、聚合统计和 CSV 导出功能。

### 1.2 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 基础框架 | Spring Boot | 3.2.5 |
| Java 版本 | Java | 17 |
| 数据库 | MySQL | 8.0+ |
| 连接池 | HikariCP | Spring Boot 内置 |
| 数据库迁移 | Flyway | Spring Boot 内置 |
| ORM | Spring JDBC (JdbcTemplate) | Spring Boot 内置 |
| 缓存 | Caffeine | 3.1.x |
| 日志框架 | Log4j2 | 2.21.1 |
| 构建工具 | Maven | 3.8+ |
| 部署方式 | WAR (Tomcat 10) / JAR | - |

### 1.3 核心特性

- **异步写入**：使用 `LinkedBlockingQueue` + 后台 flush 线程实现异步批量入库，不阻塞 Tomcat 请求线程
- **数据三重保障**：
  1. 写文件备份：每条请求先同步写入 `game-log.jsonl`
  2. 内存队列 + 后台 flush：异步批量入库
  3. 启动恢复：`DataRecoveryRunner` 扫描 `.jsonl` 文件恢复数据
- **批量 INSERT**：200 条入库仅需 4 次 JDBC 往返
- **Caffeine 缓存**：统计接口 30s 过期缓存
- **优雅关闭**：JVM shutdown hook 确保队列数据全部入库

---

## 2. 项目架构

### 2.1 整体架构图

```
[客户端/游戏服] → HTTP POST → ContentCachingFilter（写 gateway.log）
                                   ↓
                              Controller（16 个 REST 接口）
                                   ↓
                              Service（带 Caffeine 缓存）
                                   ↓
     ┌──────────────────── GameLogAsyncWriter ────────────────────┐
     │  ① logData() → 写 game-log.jsonl（同步，Log4j2 保护）        │
     │  ② queue.offer() → LinkedBlockingQueue（20000 容量）        │
     │  ③ flush 线程 drainTo(200) → saveAll（batch INSERT 真生效）  │
     │  ④ 队列满时降级 → 同步 save(gameLog)                        │
     └────────────────────────────────────────────────────────────┘
                                   ↓
                              MySQL（game_log 表）
```

### 2.2 分层架构

| 层级 | 包路径 | 职责 |
|------|--------|------|
| **Controller** | `com.gamelog.controller` | 接收 HTTP 请求，参数校验，返回响应 |
| **Service** | `com.gamelog.service` | 业务逻辑处理，事务管理 |
| **Repository** | `com.gamelog.repository` | 数据访问层，SQL 查询 |
| **Entity** | `com.gamelog.entity` | 数据实体对象 |
| **DTO** | `com.gamelog.dto` | 数据传输对象 |
| **Config** | `com.gamelog.config` | 配置类，Bean 注入 |
| **Async** | `com.gamelog.async` | 异步写入模块 |

### 2.3 线程模型

| 线程名称 | 类型 | 数量 | 职责 |
|---------|------|:----:|------|
| Tomcat 工作线程 | 非守护 | 200 | 处理 HTTP 请求，写文件，入队 |
| `gamelog-flush` | 守护 | 1 | drain 内存队列 → 批量入库 |
| `gamelog-shutdown-hook` | 非守护 | 1 | 关闭时 drain 剩余数据入库 |
| HikariCP 连接池 | 守护 | max=5 | 所有数据库操作 |

---

## 3. 模块详解

### 3.1 启动模块

**文件路径**：[GameLogApplication.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/GameLogApplication.java)

```java
@SpringBootApplication
public class GameLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameLogApplication.class, args);
    }
}
```

**职责**：Spring Boot 应用入口，使用 `@SpringBootApplication` 注解自动配置。

---

### 3.2 控制器层 (Controller)

**文件路径**：[GameLogController.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/controller/GameLogController.java)

#### 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/game-logs` | 创建单条游戏日志（异步） |
| `POST` | `/api/game-logs/batch` | 批量创建游戏日志 |
| `GET` | `/api/game-logs` | 分页查询日志 |
| `GET` | `/api/game-logs/{id}` | 查询单条日志 |
| `DELETE` | `/api/game-logs/{id}` | 删除日志 |
| `GET` | `/api/game-logs/queue-status` | 队列状态监控 |
| `GET` | `/api/game-logs/stats/today-count` | 今日日志总数 |
| `GET` | `/api/game-logs/stats/average-duration` | 平均游戏时长 |
| `GET` | `/api/game-logs/stats/trend` | 近7天趋势 |
| `GET` | `/api/game-logs/stats/game-distribution` | 游戏分布占比 |
| `GET` | `/api/game-logs/stats/recent-logs` | 最近日志（TOP 10） |
| `GET` | `/api/game-logs/stats/player-leaderboard` | 玩家排行榜 Top 10 |
| `GET` | `/api/game-logs/stats/action-distribution` | 操作类型分布 |
| `GET` | `/api/game-logs/stats/hourly-activity` | 24小时活跃热力图 |
| `GET` | `/api/game-logs/export` | 导出 CSV |

#### 关键方法

**createGameLog** - 新增日志（异步写入）
```java
@PostMapping
public ResponseEntity<Result<Void>> createGameLog(@RequestBody GameLogCreateDTO dto) {
    gameLogService.createGameLog(dto);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Result.success(null));
}
```

**exportCsv** - 导出 CSV
```java
@GetMapping("/export")
public ResponseEntity<byte[]> exportCsv(GameLogQueryDTO queryDTO) {
    // 限制最大导出 10000 条
    if (queryDTO.getSize() == null || queryDTO.getSize() > 10000) {
        queryDTO.setSize(10000);
    }
    // 生成 CSV 格式数据
    StringBuilder csv = new StringBuilder();
    csv.append("ID,游戏名称,玩家,操作,详情,游戏时长(分钟),游戏时间,记录时间\n");
    // ... 填充数据
    return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
}
```

---

### 3.3 服务层 (Service)

**文件路径**：[GameLogService.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/service/GameLogService.java)

#### 主要方法

| 方法 | 说明 | 缓存 |
|------|------|------|
| `createGameLog` | 异步新增日志 | - |
| `createGameLogBatch` | 批量异步新增日志 | - |
| `queryGameLogs` | 分页查询日志 | - |
| `getGameLogById` | 查询单条日志 | - |
| `deleteGameLog` | 删除日志 | - |
| `getTodayCount` | 今日日志总数 | `@Cacheable` 10s |
| `getAverageDuration` | 平均游戏时长 | `@Cacheable` 10s |
| `getTrend` | 近7天趋势 | `@Cacheable` 10s |
| `getGameDistribution` | 游戏分布占比 | `@Cacheable` 10s |
| `getRecentLogs` | 最近日志（TOP 10） | `@Cacheable` 10s |
| `getPlayerLeaderboard` | 玩家排行榜 Top 10 | `@Cacheable` 10s |
| `getActionDistribution` | 操作类型分布 | `@Cacheable` 10s |
| `getHourlyActivity` | 24小时活跃热力图 | `@Cacheable` 10s |
| `getQueueStatus` | 获取队列状态 | - |

---

### 3.4 数据访问层 (Repository)

**文件路径**：[GameLogDao.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/repository/GameLogDao.java)

#### 技术选型

使用 **Spring JDBC (JdbcTemplate)** 而非 JPA/Hibernate，直接编写 SQL 语句。

#### 主要方法

**基础 CRUD**
| 方法 | 说明 | SQL |
|------|------|-----|
| `findById` | 按 ID 查询 | `SELECT ... WHERE id = ?` |
| `existsById` | 检查 ID 是否存在 | `SELECT COUNT(1) ...` |
| `deleteById` | 按 ID 删除 | `DELETE ... WHERE id = ?` |
| `batchInsert` | 批量插入 | `INSERT INTO ...` |

**查询接口**
| 方法 | 说明 |
|------|------|
| `findByConditions` | 动态条件分页查询 |
| `findByPlayTimeBetween` | 按时间范围查询 |

**统计接口**
| 方法 | 说明 |
|------|------|
| `countByCreatedAtBetween` | 按时间范围统计总数 |
| `countTotal` | 统计总记录数 |
| `findTop10ByOrderByCreatedAtDesc` | 查询最新 10 条 |
| `countByGameNameGroup` | 按游戏名称分组统计 |
| `countByDateGroup` | 按日期分组统计 |
| `findPlayerStats` | 玩家排行榜 |
| `findDailyHourlyStats` | 按天和小时分组统计 |
| `findActionStats` | 操作类型分布 |
| `findAverageDuration` | 平均游戏时长 |

---

### 3.5 实体类 (Entity)

**文件路径**：[GameLog.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/entity/GameLog.java)

```java
@Data
public class GameLog {
    private Long id;              // 主键ID
    private String gameName;      // 游戏名称
    private String player;        // 玩家
    private String action;        // 操作类型
    private String detail;        // 详细信息
    private LocalDateTime playTime;   // 游戏时间
    private Integer duration;      // 游戏时长（分钟）
    private LocalDateTime createdAt;  // 记录创建时间
}
```

**说明**：纯 POJO，无 ORM 注解，表结构由 Flyway migration 管理。

---

### 3.6 数据传输对象 (DTO)

#### GameLogCreateDTO

**文件路径**：[GameLogCreateDTO.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/dto/GameLogCreateDTO.java)

```java
@Data
public class GameLogCreateDTO {
    private String gameName;      // 游戏名称
    private String player;       // 玩家
    private String action;        // 操作类型
    private String detail;        // 详细信息
    private LocalDateTime playTime;   // 游戏时间
    private Integer duration;     // 游戏时长（分钟）
}
```

#### GameLogQueryDTO

**文件路径**：[GameLogQueryDTO.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/dto/GameLogQueryDTO.java)

```java
@Data
public class GameLogQueryDTO {
    private String gameName;
    private String player;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer page = 0;
    private Integer size = 20;
}
```

#### Result

**文件路径**：[Result.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/dto/Result.java)

统一响应格式：
```java
@Data
public class Result<T> {
    private int code;         // 状态码
    private String message;  // 消息
    private T data;          // 数据

    public static <T> Result<T> success(T data) { ... }
    public static <T> Result<T> success() { ... }
    public static <T> Result<T> error(int code, String message) { ... }
}
```

#### PageResult

**文件路径**：[PageResult.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/dto/PageResult.java)

分页结果：
```java
@Data
public class PageResult<T> {
    private List<T> content;       // 数据列表
    private int page;             // 当前页码
    private int size;             // 每页大小
    private long totalElements;   // 总记录数
    private int totalPages;       // 总页数
    private boolean first;        // 是否第一页
    private boolean last;         // 是否最后一页
    private boolean empty;        // 是否为空
}
```

#### QueueStatusDTO

**文件路径**：[QueueStatusDTO.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/dto/QueueStatusDTO.java)

队列状态监控：
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueueStatusDTO {
    private int queueSize;         // 当前队列积压数量
    private long totalWriteCount; // 总写入次数
    private Long lastFlushTime;   // 上次刷新时间（时间戳）
}
```

---

### 3.7 异步写入模块 (Async)

#### GameLogAsyncWriter

**文件路径**：[GameLogAsyncWriter.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/async/GameLogAsyncWriter.java)

**核心职责**：异步批量写入数据库

**关键特性**：
1. 使用 `LinkedBlockingQueue` 作为内存队列（容量 20000）
2. 后台 `gamelog-flush` 守护线程定期 drain 队列
3. JDBC batch INSERT 绕过 Hibernate 直接入库
4. 注册 JVM shutdown hook 保证优雅关闭

**主要方法**：

| 方法 | 说明 |
|------|------|
| `submit` | 提交单条日志到异步队列 |
| `submitBatch` | 批量提交日志到异步队列 |
| `getQueueSize` | 获取当前队列积压数量 |
| `getQueueStatus` | 获取队列状态监控信息 |

**内部方法**：
| 方法 | 说明 |
|------|------|
| `startFlushThread` | 启动 flush 守护线程 |
| `flushBatch` | 批量刷新数据到数据库 |
| `jdbcBatchInsert` | 执行 JDBC batch INSERT |
| `registerShutdownHook` | 注册关闭钩子 |

**提交流程**：
```
1. logData() → 写 game-log.jsonl（数据备份）
2. queue.offer() → 入队
3. flush 线程 drainTo(batch) → 批量取出
4. jdbcBatchInsert() → JDBC batch INSERT 入库
```

**降级策略**：队列满时（20000），直接丢弃数据（但已写入日志文件，重启后可恢复）。

#### DataLogWriter

**文件路径**：[DataLogWriter.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/async/DataLogWriter.java)

**核心职责**：将 GameLog 写入 JSON 文件备份

**关键特性**：
1. 使用 Log4j2 的 DataLogger
2. 写入 JSON Lines 格式（每行一个 JSON 对象）
3. 与内存队列配合实现数据双重保障

**主要方法**：
```java
public boolean logData(GameLog gameLog) {
    String json = objectMapper.writeValueAsString(gameLog);
    dataLogger.info(json);  // 写入 game-log.jsonl
    return true;
}
```

---

### 3.8 配置模块 (Config)

#### AsyncConfig

**文件路径**：[AsyncConfig.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/config/AsyncConfig.java)

```java
@Data
@Configuration
@ConfigurationProperties(prefix = "async.writer")
public class AsyncConfig {
    private int queueCapacity = 20000;     // 内存队列最大容量
    private int batchSize = 200;           // 批量入库大小
    private long flushIntervalMs = 500;    // 队列空时睡眠时间（ms）
}
```

#### CacheConfig

**文件路径**：[CacheConfig.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/config/CacheConfig.java)

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("stats");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)  // 写入后 10 秒过期
                .maximumSize(5000)
                .recordStats());
        return manager;
    }
}
```

#### DataRecoveryRunner

**文件路径**：[DataRecoveryRunner.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/config/DataRecoveryRunner.java)

**核心职责**：启动时检查日志文件，与数据库对比，恢复未入库的数据

**执行流程**：
```
1. 检查 logs/data 目录
2. 获取近 3 天的日志文件
3. 逐文件恢复：
   a. 读取 JSON Lines
   b. 分批处理（每批 10000 条）
   c. 查询数据库已有记录
   d. 去重后批量插入
4. 清理超过 7 天的过期日志文件
5. 清空已同步的当前日志文件
```

**去重策略**：
```java
private String buildKey(GameLog log) {
    return String.format("%s|%s|%s|%s",
            log.getGameName(),
            log.getPlayer(),
            log.getAction(),
            log.getPlayTime());
}
```

#### WebConfig

**文件路径**：[WebConfig.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/config/WebConfig.java)

注册 HTTP 日志 Filter：
```java
@Configuration
public class WebConfig {
    @Bean
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ContentCachingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("contentCachingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
```

#### GlobalExceptionHandler

**文件路径**：[GlobalExceptionHandler.java](file:///e:/trae_Projects/game_log/src/main/java/com/gamelog/config/GlobalExceptionHandler.java)

统一异常处理：
```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(...) { ... }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(...) { ... }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(...) { ... }
}
```

---

## 4. 数据库设计

### 4.1 表结构

**表名**：`game_log`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键ID |
| `game_name` | VARCHAR(100) | NOT NULL | 游戏名称 |
| `player` | VARCHAR(100) | NOT NULL | 玩家 |
| `action` | VARCHAR(200) | NOT NULL | 操作类型 |
| `detail` | VARCHAR(1000) | - | 详细信息 |
| `play_time` | DATETIME | NOT NULL | 游戏时间 |
| `duration` | INT | - | 游戏时长（分钟） |
| `created_at` | DATETIME | NOT NULL | 记录创建时间 |

### 4.2 索引设计

**单列索引**：
- `idx_game_name` (game_name)
- `idx_player` (player)
- `idx_play_time` (play_time)
- `idx_created_at` (created_at)
- `idx_action` (action)

**复合索引**：
- `idx_player_created` (player, created_at)
- `idx_game_name_created` (game_name, created_at)
- `idx_action_created` (action, created_at)
- `idx_game_name_play_time` (game_name, play_time)

**唯一约束**：
- `uk_game_player_action_time` (game_name, player, action, play_time)

### 4.3 Flyway 迁移脚本

| 版本 | 文件名 | 说明 |
|------|--------|------|
| 1.0.6 | V1.0.6__drop_id_sequence.sql | 删除废弃的 id_sequence 表 |
| 1.0.7 | V1.0.7__fresh_db_schema.sql | 完整数据库初始化 |
| 1.0.8 | V1.0.8__set_innodb_flush_log_at_trx_commit.sql | 优化 InnoDB 配置 |
| 1.0.9 | V1.0.9__set_sync_binlog.sql | 优化 binlog 配置 |

---

## 5. 配置文件

### 5.1 application.yml

**文件路径**：[application.yml](file:///e:/trae_Projects/game_log/src/main/resources/application.yml)

```yaml
server:
  port: 8080

spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:}  # 默认云模式，local 为本地模式
  
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/game_log?...}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:3}
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1.0.6

async:
  writer:
    queue-capacity: ${ASYNC_QUEUE_CAPACITY:20000}
    batch-size: ${ASYNC_BATCH_SIZE:500}
    flush-interval-ms: ${ASYNC_FLUSH_INTERVAL_MS:200}
```

### 5.2 Log4j2 配置

**文件路径**：[log4j2-spring.xml](file:///e:/trae_Projects/game_log/src/main/resources/log4j2-spring.xml)

**日志类型**：

| Logger 名称 | 文件路径 | 说明 |
|------------|----------|------|
| DataLogger | `logs/data/game-log.jsonl` | 数据日志（JSON Lines 格式） |
| GatewayLogger | `logs/gateway/gateway.log` | 网关请求日志 |

**滚动策略**：按天滚动，保留 7 天

---

## 6. 依赖关系

### 6.1 Maven 依赖

```xml
<!-- Spring Boot Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Cache + Caffeine -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Spring JDBC -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<!-- Flyway 数据库迁移 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- Log4j2 -->
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
</dependency>

<!-- MySQL Driver -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### 6.2 依赖版本管理

| 版本属性 | 默认值 |
|---------|--------|
| `java.version` | 17 |
| `log4j2.version` | 2.21.1 |
| Spring Boot 版本 | 3.2.5 |

---

## 7. 运行方式

### 7.1 本地开发

```bash
# 1. 创建 MySQL 数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS game_log DEFAULT CHARSET utf8mb4;"

# 2. 启动服务
mvn spring-boot:run

# 3. 访问应用
http://localhost:8080
```

### 7.2 使用启动脚本

**Windows**：
```bash
scripts\start.bat
```

**Linux (Tomcat)**：
```bash
bash scripts/start.sh
```

### 7.3 环境变量配置

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/game_log?...` | 数据库 JDBC URL |
| `MYSQL_USERNAME` | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | `root` | 数据库密码 |
| `HIKARI_MAX_POOL_SIZE` | `3` | 连接池最大连接数 |
| `ASYNC_QUEUE_CAPACITY` | `20000` | 异步队列容量 |
| `ASYNC_BATCH_SIZE` | `500` | 每批入库条数 |
| `ASYNC_FLUSH_INTERVAL_MS` | `200` | 队列空时轮询间隔（ms） |

### 7.4 常用命令

```bash
mvn spring-boot:run                # 开发模式启动
mvn clean compile                  # 编译
mvn test                           # 运行测试
mvn clean package -DskipTests      # 打包 WAR
bash scripts/start.sh              # Linux 部署
bash scripts/stop.sh              # Linux 停止
scripts\start.bat                  # Windows 本地启动
```

---

## 8. 关键设计要点

### 8.1 数据安全保障

1. **写文件备份**：每条请求先同步写入 `game-log.jsonl`，再入队
2. **内存队列 + 后台 flush**：异步批量入库，队列 20000 容量
3. **启动时文件恢复**：服务重启后 `DataRecoveryRunner` 扫描 `.jsonl` 文件恢复数据

### 8.2 性能优化

1. **批量 INSERT**：200 条入库仅需 4 次 JDBC 往返
2. **Caffeine 缓存**：统计接口 10 秒过期，避免高频查询压数据库
3. **数据库索引**：针对查询条件建立复合索引
4. **连接池优化**：HikariCP 配置合理的连接池大小

### 8.3 可靠性设计

1. **数据库唯一约束**：`UNIQUE(game_name, player, action, play_time)` 防止重复入库
2. **优雅关闭**：注册 JVM shutdown hook，关闭前 drain 队列剩余数据入库
3. **压力告警**：队列积压超过 15000 时记录 warn 日志

### 8.4 日志系统

1. **DataLogger**：记录业务数据到 JSON Lines 文件
2. **GatewayLogger**：记录 HTTP 请求日志
3. **滚动策略**：按天滚动，保留 7 天

---

## 9. 目录结构

```
game-log-service/
├── src/
│   ├── main/
│   │   ├── java/com/gamelog/
│   │   │   ├── GameLogApplication.java      # 启动入口
│   │   │   ├── ServletInitializer.java     # WAR 部署入口
│   │   │   ├── async/
│   │   │   │   ├── GameLogAsyncWriter.java # 异步写入器
│   │   │   │   └── DataLogWriter.java      # 数据日志写入器
│   │   │   ├── config/
│   │   │   │   ├── AsyncConfig.java        # 异步配置
│   │   │   │   ├── CacheConfig.java        # 缓存配置
│   │   │   │   ├── DataRecoveryRunner.java # 数据恢复启动器
│   │   │   │   ├── WebConfig.java          # Web 配置
│   │   │   │   └── GlobalExceptionHandler.java # 异常处理
│   │   │   ├── controller/
│   │   │   │   └── GameLogController.java  # REST 接口
│   │   │   ├── dto/
│   │   │   │   ├── GameLogCreateDTO.java
│   │   │   │   ├── GameLogQueryDTO.java
│   │   │   │   ├── GameLogBatchCreateDTO.java
│   │   │   │   ├── GameLogStatsDTO.java
│   │   │   │   ├── PageResult.java
│   │   │   │   ├── QueueStatusDTO.java
│   │   │   │   └── Result.java
│   │   │   ├── entity/
│   │   │   │   └── GameLog.java
│   │   │   ├── repository/
│   │   │   │   └── GameLogDao.java
│   │   │   └── service/
│   │   │       └── GameLogService.java
│   │   └── resources/
│   │       ├── db/migration/
│   │       │   ├── V1.0.6__drop_id_sequence.sql
│   │       │   ├── V1.0.7__fresh_db_schema.sql
│   │       │   ├── V1.0.8__set_innodb_flush_log_at_trx_commit.sql
│   │       │   └── V1.0.9__set_sync_binlog.sql
│   │       ├── static/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       └── log4j2-spring.xml
│   └── test/
│       └── java/com/gamelog/
├── scripts/
│   ├── start.bat / start.sh
│   ├── stop.bat / stop.sh
│   └── stress-remote.bat / stress.sh
├── pom.xml
├── README.md
└── CODE_WIKI.md
```

---

## 10. 常见问题

### 10.1 如何修改异步队列容量？

通过环境变量或配置文件：
```yaml
async:
  writer:
    queue-capacity: 50000  # 修改为 50000
```

或环境变量：`ASYNC_QUEUE_CAPACITY=50000`

### 10.2 如何查看队列积压情况？

调用接口：`GET /api/game-logs/queue-status`

### 10.3 服务重启后数据会丢失吗？

不会。数据有三重保障：
1. 每条数据先写入 `game-log.jsonl` 文件
2. 内存队列异步处理
3. 服务重启后 `DataRecoveryRunner` 自动恢复未入库数据

### 10.4 如何导出大量数据？

调用 `GET /api/game-logs/export` 接口，最大支持导出 10000 条。

### 10.5 如何调优数据库连接池？

通过环境变量：
```bash
HIKARI_MAX_POOL_SIZE=10  # 增加连接池大小
HIKARI_MIN_IDLE=3        # 最小空闲连接数
```

---

## 11. API 详细说明

### 11.1 创建游戏日志

**请求**：
```http
POST /api/game-logs
Content-Type: application/json

{
  "gameName": "王者荣耀",
  "player": "张三",
  "action": "登录",
  "detail": "玩家登录游戏",
  "playTime": "2026-06-12 10:00:00",
  "duration": 30
}
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 11.2 批量创建游戏日志

**请求**：
```http
POST /api/game-logs/batch
Content-Type: application/json

[
  {
    "gameName": "王者荣耀",
    "player": "张三",
    "action": "登录",
    "playTime": "2026-06-12 10:00:00"
  },
  {
    "gameName": "王者荣耀",
    "player": "李四",
    "action": "登出",
    "playTime": "2026-06-12 11:00:00"
  }
]
```

### 11.3 分页查询

**请求**：
```http
GET /api/game-logs?gameName=王者荣耀&player=张三&startTime=2026-06-01 00:00:00&endTime=2026-06-12 23:59:59&page=0&size=20
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "first": true,
    "last": false,
    "empty": false
  }
}
```

### 11.4 导出 CSV

**请求**：
```http
GET /api/game-logs/export?gameName=王者荣耀&size=1000
```

**响应**：
```
HTTP/1.1 200 OK
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="game_logs_1718164800000.csv"

ID,游戏名称,玩家,操作,详情,游戏时长(分钟),游戏时间,记录时间
1,王者荣耀,张三,登录,玩家登录游戏,30,2026-06-12 10:00:00,2026-06-12 10:00:01
```

---

## 12. 监控与运维

### 12.1 Actuator 端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康检查 |
| `/actuator/info` | 应用信息 |
| `/actuator/shutdown` | 关闭服务（需开启） |

### 12.2 日志查看

```bash
# 数据日志
tail -f logs/data/game-log.jsonl

# 网关日志
tail -f logs/gateway/gateway.log

# 应用日志（控制台）
tail -f logs/console.log
```

### 12.3 数据库监控

连接 MySQL 查看：
```sql
-- 查看总记录数
SELECT COUNT(*) FROM game_log;

-- 查看最近插入的数据
SELECT * FROM game_log ORDER BY created_at DESC LIMIT 10;

-- 按游戏名称统计
SELECT game_name, COUNT(*) as cnt FROM game_log GROUP BY game_name ORDER BY cnt DESC;
```

---

**文档版本**：1.0  
**最后更新**：2026-06-12  
**维护者**：Game Log Service Team