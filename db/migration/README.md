# 数据库迁移脚本

本目录按 **Flyway 命名规范** 管理数据库 schema 的增量变更，每个文件对应一个版本变更。

> ⚠️ **旧版 `sql/` 目录已废弃**（V1.0.0 起），请使用本目录。
> 旧 `sql/init.sql` 与 V1.0.0 内容重叠，但缺少 `created_at`/`action` 索引和 `duration` 列。

## 命名规范

```
V<版本号>__<描述>.sql
```

- **V** 前缀 + 版本号（点分）
- **双下划线** `__` 分隔版本和描述
- **单下划线** `_` 分隔单词
- 描述使用英文或拼音

## 文件列表

| 文件 | 说明 | 影响 |
|------|------|------|
| V1.0.0__init_schema.sql | 初始表结构 + 基础索引 | 全新部署 |
| V1.0.1__add_composite_indexes.sql | 复合索引（性能优化） | 200w 数据查询 30+ 秒 → < 1 秒 |

## 使用方法

### 全新部署

按版本号顺序依次执行：
```bash
mysql -u root -p < V1.0.0__init_schema.sql
mysql -u root -p < V1.0.1__add_composite_indexes.sql
```

### 已有数据升级

只执行增量脚本（V1.0.0 可跳过）：
```sql
USE game_log;
CREATE INDEX idx_player_created ON game_log (player, created_at);
CREATE INDEX idx_game_name_created ON game_log (game_name, created_at);
CREATE INDEX idx_action_created ON game_log (action, created_at);
```

## 注意事项

- **不可修改已发布的脚本** - 一旦上线，新版本只增不删
- **新版本号必须严格递增** - 推荐使用 `V1.0.2`、`V1.1.0`
- **幂等性** - 复杂脚本可用 `CREATE TABLE IF NOT EXISTS`、`DROP PROCEDURE IF EXISTS`
- **测试** - 升级前在测试库验证，特别是大表加索引（200w 数据加索引约 1-2 分钟）
