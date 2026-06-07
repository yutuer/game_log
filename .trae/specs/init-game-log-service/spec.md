# 游戏日志记录系统 Spec

## Why

需要一个后端服务来记录和查询游戏日志，使用 SpringBoot 构建，MySQL 存储，支持通过 HTTP 接口进行日志的增删查，并准备部署到阿里云服务器（Tomcat 方式）。当前压力较小，但需为未来高并发场景做好架构准备。

## What Changes

* 创建 SpringBoot 项目骨架（Maven 构建，WAR 包打包方式以支持 Tomcat 部署）

* 实现 GameLog 实体和 MySQL 数据库表（含索引优化）

* 实现 GameLog 的 CRUD REST API

* 引入异步写入机制，应对高并发写入场景

* 数据库连接池配置优化（HikariCP）

* 添加启动和关闭脚本（Linux shell 脚本，适配阿里云服务器）

* 配置数据库连接和应用参数

* 支持开发环境热更新（Spring DevTools）

* 提供可视化控制台界面（纯 HTML + ECharts，内嵌于 SpringBoot static 目录）

## Impact

* Affected code: 全新项目，无现有代码受影响

* 技术栈: SpringBoot 3.x + JPA + MySQL + Maven + HikariCP + 异步写入 + Spring DevTools 热更新 + ECharts 可视化

***

## ADDED Requirements

### Requirement: 项目骨架

系统 SHALL 基于 SpringBoot 3.x 创建 Maven 项目，打包方式为 WAR（支持外部 Tomcat 部署）。

#### Scenario: 项目结构

* **WHEN** 项目初始化完成

* **THEN** 包含标准 SpringBoot 分层结构：controller / service / repository / entity / dto / config / async

* **AND** pom.xml 包含 spring-boot-starter-web、spring-boot-starter-data-jpa、mysql-connector-java 依赖

* **AND** 提供 SpringBootServletInitializer 子类以支持 WAR 部署

### Requirement: 游戏日志数据模型

系统 SHALL 提供游戏日志实体 GameLog，存储到 MySQL。

#### Scenario: GameLog 字段

* **WHEN** 创建 GameLog 实体

* **THEN** 包含以下字段：

  * id (Long, 自增主键)

  * gameName (String, 游戏名称)

  * player (String, 玩家标识)

  * action (String, 游戏动作/事件)

  * detail (String, 详细信息, 可选)

  * playTime (LocalDateTime, 游戏时间)

  * createdAt (LocalDateTime, 记录创建时间, 自动填充)

#### Scenario: 数据库表与索引

* **WHEN** 应用启动

* **THEN** 自动创建 game\_log 表（通过 JPA DDL auto）

* **AND** 创建以下索引以优化查询性能：

  * idx\_game\_name (gameName) — 按游戏名称筛选

  * idx\_player (player) — 按玩家筛选

  * idx\_play\_time (playTime) — 按时间范围查询

  * idx\_game\_name\_play\_time (gameName, playTime) — 复合查询覆盖索引

### Requirement: 游戏 CRUD REST API

系统 SHALL 提供 RESTful HTTP 接口管理游戏日志。

#### Scenario: 新增日志（异步写入）

* **WHEN** POST /api/game-logs，body 包含 gameName、player、action、detail、playTime

* **THEN** 将日志写入任务提交到异步队列，立即返回 202（已接受）

* **AND** 异步线程池消费队列，批量写入 MySQL

* **AND** 当队列积压超过阈值时，降级为同步写入确保不丢数据

#### Scenario: 批量新增日志

* **WHEN** POST /api/game-logs/batch，body 包含日志数组

* **THEN** 批量提交到异步队列，返回 202

#### Scenario: 查询日志列表

* **WHEN** GET /api/game-logs，支持分页参数 page、size，以及可选筛选参数 gameName、player、startTime、endTime

* **THEN** 返回分页的日志列表

#### Scenario: 查询单条日志

* **WHEN** GET /api/game-logs/{id}

* **THEN** 返回对应日志详情；不存在时返回 404

#### Scenario: 删除日志

* **WHEN** DELETE /api/game-logs/{id}

* **THEN** 删除对应日志；不存在时返回 404

### Requirement: 异步写入机制

系统 SHALL 提供异步写入能力，应对高并发写入场景。

#### Scenario: 异步线程池配置

* **WHEN** 应用启动

* **THEN** 初始化异步写入线程池，核心线程数可配置（默认 4），最大线程数可配置（默认 8）

* **AND** 配置有界队列（默认容量 10000），防止 OOM

#### Scenario: 批量写入优化

* **WHEN** 异步线程从队列消费日志

* **THEN** 积攒到一定数量（默认 100 条）或等待一定时间（默认 1 秒）后批量 INSERT

* **AND** 使用 JPA saveAll 批量写入，减少数据库交互次数

#### Scenario: 队列满降级

* **WHEN** 异步队列已满

* **THEN** 降级为同步写入，确保数据不丢失

* **AND** 记录 WARN 日志提示队列积压

### Requirement: 数据库连接池优化

系统 SHALL 使用 HikariCP 连接池并针对高并发优化配置。

#### Scenario: HikariCP 配置

* **WHEN** 应用启动

* **THEN** HikariCP 连接池参数可通过 application.yml 配置：

  * maximumPoolSize（默认 20）

  * minimumIdle（默认 5）

  * connectionTimeout（默认 30000ms）

  * idleTimeout（默认 600000ms）

  * maxLifetime（默认 1800000ms）

### Requirement: 统一响应格式

系统 SHALL 使用统一的 JSON 响应格式。

#### Scenario: 成功响应

* **WHEN** 接口调用成功

* **THEN** 返回 `{"code": 200, "message": "success", "data": ...}`

#### Scenario: 错误响应

* **WHEN** 接口调用失败

* **THEN** 返回 `{"code": <错误码>, "message": "<错误描述>", "data": null}`

### Requirement: 部署脚本

系统 SHALL 提供启动和关闭脚本，适配阿里云 Linux 服务器 + Tomcat 部署。

#### Scenario: 启动脚本

* **WHEN** 执行 start.sh

* **THEN** 编译打包 WAR 文件，部署到 Tomcat webapps 目录，启动/重启 Tomcat

#### Scenario: 关闭脚本

* **WHEN** 执行 stop.sh

* **THEN** 优雅关闭 Tomcat

### Requirement: 应用配置
系统 SHALL 通过 application.yml 管理配置。

#### Scenario: 配置文件
- **WHEN** 应用启动
- **THEN** 读取 application.yml 中的 MySQL 连接信息、HikariCP 参数、异步线程池参数、服务端口等配置
- **AND** 敏感信息（数据库密码等）可通过环境变量覆盖

### Requirement: 热更新支持
系统 SHALL 支持开发环境下的热更新，修改代码后无需手动重启即可生效。

#### Scenario: Spring DevTools 集成
- **WHEN** 项目初始化
- **THEN** pom.xml 中包含 spring-boot-devtools 依赖（scope: runtime, optional: true）
- **AND** application.yml 中配置 devtools.restart.enabled=true（开发环境 profile）
- **AND** devtools 依赖不参与生产环境打包（optional + runtime scope）

#### Scenario: 热更新触发
- **WHEN** 开发者修改 Java 源文件并保存（IDE 自动编译）或触发 build
- **THEN** DevTools 检测到 classpath 变化，自动重启应用上下文
- **AND** 重启仅刷新 Spring 上下文，不重启 JVM（速度远快于冷启动）

#### Scenario: 静态资源热更新
- **WHEN** 开发者修改静态资源（如配置文件）
- **THEN** DevTools 自动触发 LiveReload，浏览器端无需手动刷新

### Requirement: 可视化控制台界面
系统 SHALL 提供基于纯 HTML + ECharts 的可视化控制台，内嵌于 SpringBoot 的 static 目录，随 WAR 包一起部署。

#### Scenario: 控制台入口
- **WHEN** 用户访问 GET /
- **THEN** 返回可视化控制台首页（index.html）

#### Scenario: 日志概览仪表盘
- **WHEN** 用户打开控制台首页
- **THEN** 展示日志概览数据：
  - 今日日志总数（数字卡片）
  - 近 7 天日志趋势（ECharts 折线图）
  - 各游戏日志占比（ECharts 饼图）
  - 最近日志列表（表格，显示 gameName、player、action、playTime）

#### Scenario: 日志查询页面
- **WHEN** 用户在控制台输入筛选条件（游戏名称、玩家、时间范围）
- **THEN** 调用 /api/game-logs 接口查询，以表格形式展示结果
- **AND** 支持分页浏览

#### Scenario: 技术实现
- **WHEN** 构建控制台页面
- **THEN** 使用纯 HTML + CSS + JavaScript，无前端构建工具
- **AND** ECharts 通过 CDN 引入
- **AND** 页面通过 fetch API 调用后端 REST 接口获取数据
- **AND** 所有前端文件放在 src/main/resources/static/ 目录下

