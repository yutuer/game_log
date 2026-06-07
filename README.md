# 游戏日志记录系统 (game-log-service)

## 项目简介

基于 SpringBoot 3.x 的游戏日志记录与查询服务，使用 MySQL 存储，支持高并发异步写入，提供 REST API 和可视化控制台。

## 技术栈

- Java 17 + SpringBoot 3.2.5
- Spring Data JPA + MySQL
- HikariCP 连接池
- Lombok
- ECharts 可视化
- Maven (WAR 打包，支持外部 Tomcat 部署)

## 项目结构

```
src/main/java/com/gamelog/
├── GameLogApplication.java          # 主启动类
├── ServletInitializer.java          # WAR 部署支持
├── config/
│   ├── AsyncConfig.java             # 异步线程池配置
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── async/
│   └── GameLogAsyncWriter.java      # 异步批量写入器
├── entity/
│   └── GameLog.java                 # 日志实体（含索引定义）
├── repository/
│   └── GameLogRepository.java       # 数据访问层
├── service/
│   └── GameLogService.java          # 业务逻辑层
├── controller/
│   └── GameLogController.java       # REST API 接口层
└── dto/
    ├── Result.java                  # 统一响应格式
    ├── GameLogCreateDTO.java        # 新增日志请求
    ├── GameLogBatchCreateDTO.java   # 批量新增请求
    ├── GameLogQueryDTO.java         # 查询参数
    └── GameLogStatsDTO.java         # 统计数据响应

src/main/resources/
├── application.yml                  # 主配置
├── application-dev.yml              # 开发环境配置（热更新）
└── static/
    ├── index.html                   # 可视化控制台首页
    └── query.html                   # 日志查询页面

scripts/
├── start.sh                         # Linux 部署启动脚本
├── stop.sh                          # Linux 停止脚本
├── start.bat                        # Windows 本地启动脚本
└── stop.bat                         # Windows 本地停止脚本
```

## 核心架构

### 分层架构
```
Controller → Service → Repository → MySQL
                ↓
         GameLogAsyncWriter（异步写入队列）
```

### 异步写入流程
```
请求 → 提交到有界队列(10000) → 立即返回202
              ↓
     后台线程积攒批量(100条/1秒)
              ↓
        saveAll 批量写入 MySQL
              ↓
     队列满时降级为同步写入（确保不丢数据）
```

### 数据库索引
| 索引名 | 字段 | 用途 |
|--------|------|------|
| idx_game_name | gameName | 按游戏名称筛选 |
| idx_player | player | 按玩家筛选 |
| idx_play_time | playTime | 按时间范围查询 |
| idx_game_name_play_time | gameName, playTime | 复合查询覆盖索引 |

---

## API 接口

基础路径：`http://localhost:8080/api/game-logs`

### 1. 新增日志

- **请求**：`POST /api/game-logs`
- **状态码**：202 Accepted（异步写入，已接受）
- **请求体**：
```json
{
  "gameName": "原神",
  "player": "player001",
  "action": "登录",
  "detail": "每日登录奖励",
  "playTime": "2026-06-08 10:00:00"
}
```
- **响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 2. 批量新增日志

- **请求**：`POST /api/game-logs/batch`
- **状态码**：202 Accepted
- **请求体**：
```json
{
  "logs": [
    {
      "gameName": "原神",
      "player": "player001",
      "action": "登录",
      "detail": "每日登录奖励",
      "playTime": "2026-06-08 10:00:00"
    },
    {
      "gameName": "王者荣耀",
      "player": "player002",
      "action": "对战",
      "detail": "排位赛胜利",
      "playTime": "2026-06-08 10:05:00"
    }
  ]
}
```
- **响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 3. 分页查询日志

- **请求**：`GET /api/game-logs`
- **状态码**：200 OK
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| gameName | String | 否 | 按游戏名称筛选 |
| player | String | 否 | 按玩家筛选 |
| startTime | String | 否 | 起始时间 (yyyy-MM-dd HH:mm:ss) |
| endTime | String | 否 | 结束时间 (yyyy-MM-dd HH:mm:ss) |
| page | Integer | 否 | 页码，默认 0 |
| size | Integer | 否 | 每页条数，默认 20 |

- **示例**：`GET /api/game-logs?gameName=原神&page=0&size=10`
- **响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "gameName": "原神",
        "player": "player001",
        "action": "登录",
        "detail": "每日登录奖励",
        "playTime": "2026-06-08 10:00:00",
        "createdAt": "2026-06-08 10:00:01"
      }
    ],
    "totalElements": 100,
    "totalPages": 10,
    "number": 0,
    "size": 10
  }
}
```

### 4. 查询单条日志

- **请求**：`GET /api/game-logs/{id}`
- **状态码**：200 OK / 404 Not Found
- **示例**：`GET /api/game-logs/1`
- **成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "gameName": "原神",
    "player": "player001",
    "action": "登录",
    "detail": "每日登录奖励",
    "playTime": "2026-06-08 10:00:00",
    "createdAt": "2026-06-08 10:00:01"
  }
}
```
- **失败响应**：
```json
{
  "code": 404,
  "message": "日志不存在",
  "data": null
}
```

### 5. 删除日志

- **请求**：`DELETE /api/game-logs/{id}`
- **状态码**：200 OK / 404 Not Found
- **示例**：`DELETE /api/game-logs/1`
- **成功响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 6. 统计数据

- **请求**：`GET /api/game-logs/stats`
- **状态码**：200 OK
- **响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "todayCount": 150,
    "trend": [
      { "date": "2026-06-02", "count": 80 },
      { "date": "2026-06-03", "count": 95 },
      { "date": "2026-06-08", "count": 150 }
    ],
    "gameDistribution": [
      { "gameName": "原神", "count": 500 },
      { "gameName": "王者荣耀", "count": 350 }
    ],
    "recentLogs": [
      {
        "id": 1,
        "gameName": "原神",
        "player": "player001",
        "action": "登录",
        "detail": "每日登录奖励",
        "playTime": "2026-06-08 10:00:00",
        "createdAt": "2026-06-08 10:00:01"
      }
    ]
  }
}
```

---

## 可视化控制台

| 页面 | 地址 | 说明 |
|------|------|------|
| 概览仪表盘 | `http://localhost:8080/` | 今日总数、7天趋势折线图、游戏占比饼图、最近日志 |
| 日志查询 | `http://localhost:8080/query.html` | 按游戏/玩家/时间筛选，分页浏览 |

---

## 配置说明

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| MYSQL_URL | jdbc:mysql://localhost:3306/game_log | 数据库连接地址 |
| MYSQL_USERNAME | root | 数据库用户名 |
| MYSQL_PASSWORD | root | 数据库密码 |
| HIKARI_MAX_POOL_SIZE | 20 | 连接池最大连接数 |
| HIKARI_MIN_IDLE | 5 | 连接池最小空闲数 |
| ASYNC_CORE_POOL_SIZE | 4 | 异步写入核心线程数 |
| ASYNC_MAX_POOL_SIZE | 8 | 异步写入最大线程数 |
| ASYNC_QUEUE_CAPACITY | 10000 | 异步队列容量 |
| ASYNC_BATCH_SIZE | 100 | 批量写入条数阈值 |
| ASYNC_FLUSH_INTERVAL_MS | 1000 | 批量写入时间阈值(ms) |

---

## 启动方式

### Windows 本地开发
```cmd
scripts\start.bat
```
使用 SpringBoot 内嵌 Tomcat 启动，启用 dev profile（热更新）。

### Linux 阿里云部署
```bash
chmod +x scripts/*.sh
./scripts/start.sh
```
编译 WAR 包部署到外部 Tomcat。

### 停止服务
- Windows：`scripts\stop.bat` 或在启动窗口按 Ctrl+C
- Linux：`./scripts/stop.sh`
