# Game Log Service

游戏日志采集与查询服务。接收游戏服务器的 HTTP 请求，将游戏日志异步写入 MySQL，并支持按条件查询、聚合统计、CSV 导出。

---

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.2.5 / Java 17 | 基础框架 |
| 数据库 | MySQL 8.0+ | HikariCP 连接池，Flyway 迁移 |
| ORM | Spring Data JPA / Hibernate | TABLE 主键生成 + batch INSERT |
| 缓存 | Caffeine（本地堆内缓存） | 统计接口 30s 缓存 |
| 日志 | Log4j2（排除 Logback） | 双文件写入：gateway.log + game-log.jsonl |
| 构建 | Maven | `pom.xml` 统一管理依赖 |
| 部署 | Tomcat 10 / WAR | 也支持 `java -jar` / `mvn spring-boot:run` |

---

## 整体架构

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

### 数据安全保障（三重）

1. **写文件备份**：每条请求先同步写入 `game-log.jsonl`，再入队
2. **内存队列 + 后台 flush**：异步批量入库，队列 20000 容量
3. **启动时文件恢复**：服务重启后 `DataRecoveryRunner` 扫描 `.jsonl` 文件，与数据库查重后恢复缺失数据

---

## 快速开始（本地开发）

### 前置条件

- JDK 17+
- Maven 3.8+
- MySQL 8.0+（创建数据库 `game_log`）

### 启动

```bash
# 1. 创建 MySQL 数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS game_log DEFAULT CHARSET utf8mb4;"

# 2. 启动服务（自动建表+迁移）
git clone <repo-url> && cd game-log-service
scripts\start.bat          # Windows
bash scripts/start.sh      # Linux（部署到 Tomcat）
# 或直接运行：
mvn spring-boot:run         # 开发模式
```

应用启动后访问：`http://localhost:8080`

---

## 云服务部署

### 环境变量配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/game_log?...` | 数据库 JDBC URL |
| `MYSQL_USERNAME` | `root` | 数据库用户名 |
| `MYSQL_PASSWORD` | `root` | 数据库密码 |
| `HIKARI_MAX_POOL_SIZE` | `5` | 连接池最大连接数 |
| `ASYNC_QUEUE_CAPACITY` | `20000` | 异步队列容量 |
| `ASYNC_BATCH_SIZE` | `200` | 每批入库条数 |
| `ASYNC_FLUSH_INTERVAL_MS` | `500` | 队列空时轮询间隔（ms） |

### Linux 部署（Tomcat）

```bash
# 项目自带部署脚本
bash scripts/start.sh       # 编译 → 停 Tomcat → 部署 WAR → 启动
bash scripts/stop.sh        # 优雅关闭 Tomcat

# Tomcat 路径：/usr/local/tomcat
# 启动日志：tail -f /usr/local/tomcat/logs/catalina.out
```

### 目录结构（运行期）

```
game-log-service/
├── logs/
│   ├── gateway/
│   │   └── gateway.log              # 网关请求日志（按天滚动，保留 7 天）
│   └── data/
│       ├── game-log.jsonl           # 数据备份日志（当前文件）
│       └── game-log-YYYY-MM-DD.jsonl # 滚动备份（保留 7 天）
└── db/migration/
    └── V1.0.0__init_schema.sql      # 建表脚本（Flyway 自动执行）
    └── V1.0.1__add_composite_indexes.sql  # 复合索引
    └── V1.0.2__add_unique_constraint.sql  # 唯一约束
    └── V1.0.3__create_id_sequence.sql     # ID 序列表
```

---

## API 接口

### 写入接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/game-logs` | 创建单条游戏日志 |
| `POST` | `/api/game-logs/batch` | 批量创建游戏日志 |

### 查询接口

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| `POST` | `/api/game-logs/list` | `gameName`, `player`, `action`, `startTime`, `endTime`, 分页 | 分页查询 |
| `DELETE` | `/api/game-logs` | `gameName`, `player` | 删除记录 |
| `DELETE` | `/api/game-logs/time` | `startTime`, `endTime` | 按时间范围删除 |
| `GET` | `/api/game-logs/count` | `gameName`, `player` | 统计总数（缓存 30s） |
| `GET` | `/api/game-logs/count/distinct/players` | `gameName` | 统计去重玩家数（缓存 30s） |
| `GET` | `/api/game-logs/count/distinct/actions` | `gameName`, `player` | 统计去重动作数（缓存 30s） |
| `GET` | `/api/game-logs/count/duration/avg` | `gameName` | 统计平均时长（缓存 30s） |
| `GET` | `/api/game-logs/count/duration/max` | `gameName` | 统计最大时长（缓存 30s） |
| `GET` | `/api/game-logs/count/by-action` | `gameName`, `startTime`, `endTime` | 按 action 分组统计（缓存 30s） |
| `GET` | `/api/game-logs/activity/daily` | `gameName`, `startTime`, `endTime` | 每日活跃玩家数（缓存 30s） |

### 统计数据

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/game-logs/stats/summary` | 概要统计（缓存 30s） |
| `GET` | `/api/game-logs/export` | 导出 CSV |

---

## 项目结构

```
src/main/java/com/gamelog/
├── GameLogApplication.java        # 启动入口
├── async/
│   ├── GameLogAsyncWriter.java    # 异步写入器（队列 + flush 线程）
│   └── DataLogWriter.java         # 日志文件写入器（Log4j2）
├── config/
│   ├── AsyncConfig.java           # 异步配置（queue/batch/flush）
│   ├── CacheConfig.java           # Caffeine 缓存配置
│   ├── DataRecoveryRunner.java    # 启动恢复 Runner
│   └── WebConfig.java             # Filter 注册
├── controller/
│   └── GameLogController.java     # REST 接口（16 个）
├── dto/
│   └── GameLogCreateDTO.java      # 创建请求 DTO
├── entity/
│   └── GameLog.java               # JPA 实体
├── filter/
│   └── ContentCachingFilter.java  # 网关日志 Filter
├── repository/
│   └── GameLogRepository.java     # JPA Repository（含统计查询）
├── service/
│   └── GameLogService.java        # 业务逻辑层
└── exception/
    └── GlobalExceptionHandler.java # 统一异常处理

db/migration/                      # Flyway 迁移脚本
scripts/                           # 部署脚本（start/stop）
logs/                              # 运行期日志（已 gitignore）
```

---

## 关键设计要点

| 设计 | 说明 |
|------|------|
| **异步写入** | `GameLogAsyncWriter` 使用 `LinkedBlockingQueue` + 单守护线程批量入库，不阻塞 Tomcat 请求线程 |
| **双文件日志** | `gateway.log`（HTTP 请求记录）+ `game-log.jsonl`（业务数据备份），互为补充 |
| **批量 INSERT** | 主键策略为 `GenerationType.TABLE`（`allocationSize=50`），Hibernate batch INSERT 真正生效，200 条入库仅 4 次 JDBC 往返 |
| **启动恢复** | `DataRecoveryRunner` 启动时扫描 `.jsonl` 文件，按 `playTime` 范围查数据库去重，恢复未入库数据 |
| **数据库唯一约束** | `UNIQUE(game_name, player, action, play_time)` 防止重复入库 |
| **Caffeine 缓存** | 统计接口 30s 过期，避免高频查询压数据库 |
| **优雅关闭** | 注册 JVM shutdown hook，关闭前 drain 队列剩余数据入库 |

---

## 线程模型

| 线程 | 类型 | 数量 | 职责 |
|------|------|:----:|------|
| Tomcat 工作线程 | 非守护 | 200 | 处理 HTTP 请求，写文件，入队 |
| `gamelog-flush` | 守护 | 1 | drain 内存队列 → 批量入库 |
| `gamelog-shutdown-hook` | 非守护 | 1 | 关闭时 drain 剩余数据入库 |
| HikariCP 连接池 | 守护 | max=5 | 所有数据库操作 |

---

## 常见命令

```bash
mvn spring-boot:run                # 开发模式启动
mvn clean compile                  # 编译
mvn test                           # 运行测试
mvn clean package -DskipTests      # 打包 WAR
bash scripts/start.sh              # Linux 部署
bash scripts/stop.sh               # Linux 停止
scripts\start.bat                  # Windows 本地启动
```

---

## 配置参考

### `application.yml` 关键配置项

```yaml
async:
  writer:
    queue-capacity: 20000    # 异步队列容量
    batch-size: 200          # 每批入库条数
    flush-interval-ms: 500   # 轮询间隔（ms）

spring:
  jpa:
    hibernate:
      ddl-auto: update       # 自动建表/迁移
    properties:
      hibernate:
        jdbc:
          batch_size: 50     # JDBC batch INSERT 大小
        order_insert: true   # 启用以使 batch 生效
```

> 所有配置均支持环境变量覆盖，详见[上方环境变量表](#云服务部署)。