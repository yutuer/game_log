# 项目规则

## 0. 重要文件保护
⚠️ **禁止删除或移动以下重要文件：**
- `.trae/rules/AGENTS.md` - Agent行为准则和项目规范
- `.trae/rules/project_rules.md` - 本规则文件

## 1. 依赖管理
新依赖添加后**立即**在 `pom.xml` 中声明，使用 `<version>` 指定版本。
- 优先使用 Spring Boot Starter 依赖
- 第三方库版本通过 `<properties>` 统一管理

## 2. 敏感配置
- API Key/Token 配置文件添加到 `.gitignore`
- 使用 `application.yml` / `application.properties` 统一管理配置
- 敏感信息通过环境变量或配置中心注入，不硬编码

## 3. 测试
- 测试类放 `src/test/java/` 目录，命名：`<类名>Test.java`
- 使用 JUnit 5 + Spring Boot Test 编写测试
- 测试类与被测类包路径保持一致

## 4. 文档
- 新功能更新 `docs/` 目录文档，保持与代码同步
- REST API 使用 Swagger/OpenAPI 注解自动生成文档

## 5. 代码规范
- 有意义命名，方法只做一件事，注释解释"为什么"
- 遵循 Java 命名规范：类名 PascalCase，方法名 camelCase，常量 UPPER_SNAKE_CASE
- Controller / Service / Repository 分层架构
- 使用 Lombok 减少样板代码（需在 pom.xml 中引入）

## 6. SpringBoot 规范
1. **配置管理**：使用 `@ConfigurationProperties` 替代 `@Value` 管理配置
2. **异常处理**：使用 `@ControllerAdvice` + `@ExceptionHandler` 统一异常处理
3. **日志规范**：使用 SLF4J + Logback，禁止 `System.out.println`
4. **接口规范**：RESTful 风格，统一返回 `Result<T>` 包装类

## 7. 数据存储
### 目录结构
```
data/pdfs/{股票代码}_{股票名称}/    # 例：603662_柯力传感
data/reports/{股票代码}_{股票名称}/
```
### .gitignore
```
data/pdfs/*/
data/reports/*/
!data/pdfs/.gitkeep
!data/reports/.gitkeep
```
⚠️ PDF和报告不提交Git，需自行备份。

### 常用命令
```bash
mvn spring-boot:run                    # 启动应用
mvn clean package                      # 打包
mvn test                               # 运行测试
mvn compile                            # 编译检查
```