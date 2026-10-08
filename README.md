# Railway Security Check

铁路治安保卫监督检查管理系统，使用 Spring Boot 与 Vue 3 实现档案管理、季度覆盖筛选、检查任务、材料上传、隐患整改、提醒、统计与角色权限控制。

## 技术栈

- 后端：JDK 17+、Spring Boot 3.5、Spring Security、JWT、MyBatis-Plus、Flyway、MySQL 8。
- 前端：Vue 3、TypeScript、Pinia、Element Plus、ECharts、Vite。
- 可选组件：Redis、RabbitMQ、对象存储、ClamAV；本地 AI 助手使用 Ollama、Qdrant 与 Tesseract。

## 本地启动

准备 JDK 17+、Maven 3.8.6+、Node.js 22.12+ 和 MySQL 8。创建一个新的空演示库：

```sql
CREATE DATABASE railway_security_check_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

在 PowerShell 中配置数据库连接后启动后端：

```powershell
$env:DB_USERNAME = '你的数据库用户名'
$env:DB_PASSWORD = '你的数据库密码'
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

开发配置默认连接本机 `railway_security_check_demo`。需要改变连接地址时设置 `DB_URL`。Flyway 自动执行 `backend/src/main/resources/db/migration` 的结构迁移和 `db/devdata` 的演示初始化；不要同时执行 `sql/` 兼容脚本。

另开终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

前端入口：`http://localhost:5173`。后端监听 8080 端口，前端通过 `/api` 代理访问。

## 演示账号

| 账号 | 角色 |
| --- | --- |
| `admin` | 系统管理员 |
| `bureau` | 演示公安处 |
| `station01` 至 `station25` | 演示派出所 |

演示密码统一为 `Demo-Only-Change-Me!2026`，仅供全新本地演示库使用。所有单位名称和账号都是虚构示例，不包含实际人员、联系方式、业务档案或附件。生产环境应自行初始化身份和权限，并替换演示账号。

## 构建与测试

```powershell
cd backend
mvn clean verify
cd ../frontend
npm ci
npm run lint
npm run build
npm test
```

`scripts/verify.ps1` 可执行后端验证与前端格式、构建检查。需要 Docker 的集成测试使用 Testcontainers；涉及后端联调的浏览器用例需单独启动隔离演示服务。

## 部署与可选能力

`deploy/` 提供后端 Dockerfile、Nginx 配置和环境变量示例。生产 profile 要求显式传入数据库凭据和 `JWT_SECRET`，文件扫描默认启用，需要自行部署 ClamAV。其他基础设施默认按需开启。

AI 助手默认关闭。启用 `dev,ai-local` profile 前准备本地 Ollama、Qdrant、Tesseract 和 ClamAV，通过 `AI_OLLAMA_URL`、`AI_QDRANT_HOST`、`AI_OCR_EXECUTABLE` 等环境变量设置地址和工具路径。规章资料需要自行导入，AI 输出需人工确认。

源码仓库不包含真实业务数据库、上传文件、日志、备份、本机路径配置或密钥。运行数据保存在被 Git 忽略的目录中。
