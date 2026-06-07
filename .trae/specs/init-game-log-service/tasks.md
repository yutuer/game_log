# Tasks

- [x] Task 1: 创建 SpringBoot 项目骨架
  - [x] SubTask 1.1: 创建 Maven 项目结构（pom.xml、目录结构，含 spring-boot-devtools 依赖）
  - [x] SubTask 1.2: 创建主启动类和 ServletInitializer（支持 WAR 部署）
  - [x] SubTask 1.3: 配置 application.yml（MySQL 连接、HikariCP、JPA、异步线程池、服务端口、DevTools 热更新）
  - [x] SubTask 1.4: 创建统一响应类 Result 和全局异常处理器

- [x] Task 2: 实现数据层（Entity + Repository）
  - [x] SubTask 2.1: 创建 GameLog 实体类（含 JPA 注解和索引定义）
  - [x] SubTask 2.2: 创建 GameLogRepository 接口（继承 JpaRepository，含自定义查询方法）

- [x] Task 3: 实现异步写入机制
  - [x] SubTask 3.1: 创建异步线程池配置类（AsyncConfig，核心/最大线程数、有界队列）
  - [x] SubTask 3.2: 创建 GameLogAsyncWriter（消费队列、批量攒写、队列满降级为同步写入）

- [x] Task 4: 实现业务层和接口层（Service + Controller + DTO）
  - [x] SubTask 4.1: 创建 DTO 类（GameLogCreateDTO、GameLogBatchCreateDTO、GameLogQueryDTO）
  - [x] SubTask 4.2: 创建 GameLogService（写入走异步队列、查询走同步、删除走同步）
  - [x] SubTask 4.3: 创建 GameLogController（REST API 端点：单条新增、批量新增、分页查询、单条查询、删除）

- [x] Task 5: 创建部署脚本
  - [x] SubTask 5.1: 创建 start.sh（编译打包 + 部署到 Tomcat + 启动）
  - [x] SubTask 5.2: 创建 stop.sh（关闭 Tomcat）

- [x] Task 6: 实现可视化控制台界面
  - [x] SubTask 6.1: 创建后端统计接口（/api/game-logs/stats：今日总数、7天趋势、游戏占比）
  - [x] SubTask 6.2: 创建控制台首页 index.html（概览仪表盘：数字卡片 + ECharts 折线图/饼图 + 最近日志表格）
  - [x] SubTask 6.3: 创建日志查询页面 query.html（筛选表单 + 分页表格）

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 2, Task 3]
- [Task 5] depends on [Task 1]
- [Task 6] depends on [Task 4]