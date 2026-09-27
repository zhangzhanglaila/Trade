# AJ-Report 大屏项目 — 部署到启动全流程

本文档覆盖环境准备、数据库初始化、配置修改、启动与停止、日志与排错的完整步骤，适用于 Windows 和 Linux。

## 概览
- 后端发行版位于：[aj-report-1.3.0/aj-report-1.3.0](aj-report-1.3.0/aj-report-1.3.0)
  - 启动脚本：[aj-report-1.3.0/aj-report-1.3.0/bin](aj-report-1.3.0/aj-report-1.3.0/bin)
  - 配置文件：[aj-report-1.3.0/aj-report-1.3.0/conf/bootstrap.yml](aj-report-1.3.0/aj-report-1.3.0/conf/bootstrap.yml)
  - 主程序包：位于 [aj-report-1.3.0/aj-report-1.3.0/lib](aj-report-1.3.0/aj-report-1.3.0/lib) 下的 aj-report-*.jar
- 初始化数据脚本：
  - AJ-Report 库：[aj_report.sql](aj_report.sql)
  - 大屏示例库：[data_board.sql](data_board.sql)
- 可选大屏示例配置：[QIWOebbw/dashboard.json](QIWOebbw/dashboard.json)

---

## 1. 环境准备
- Java：JDK 8（1.8）。
  - Windows：如未全局配置，请在 [aj-report-1.3.0/aj-report-1.3.0/bin/start.bat](aj-report-1.3.0/aj-report-1.3.0/bin/start.bat) 第 4 行修改 JAVA_HOME 到本机 JDK 安装路径（例如 C:\\Program Files\\Java\\jdk1.8.x_xxx）。
  - Linux：确保 java 命令可用（java -version）。
- 数据库：MySQL 5.6+（推荐 5.7/8.0）。
- 服务器端口：默认 9095（可在 bootstrap.yml 调整）。
- 文件上传目录：需要在 bootstrap.yml 设置有效路径（Windows 使用盘符路径，Linux 使用绝对路径且确保权限）。

---

## 2. 数据库初始化
1) 创建数据库（AJ-Report 与示例库）：
- AJ-Report 后端默认连接库为 aj_report，并通过 Flyway 自动建库；但推荐先导入完整初始化数据。
- 可选导入示例数据：data_board 库用于演示数据看板。

2) 通过命令行导入（请根据实际 MySQL 账户调整）：

```bash
# Windows 或 Linux（命令相同），在项目根目录执行或保证 .sql 可见
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS aj_report CHARACTER SET utf8 COLLATE utf8_general_ci;"
mysql -u root -p aj_report < aj_report.sql

mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS data_board CHARACTER SET utf8 COLLATE utf8_general_ci;"
mysql -u root -p data_board < data_board.sql
```

- 若使用图形工具（如 Navicat / Workbench），新建库后导入对应 .sql 文件即可。

---

## 3. 修改配置（bootstrap.yml）
编辑：[aj-report-1.3.0/aj-report-1.3.0/conf/bootstrap.yml](aj-report-1.3.0/aj-report-1.3.0/conf/bootstrap.yml)

必改项：
- 数据库连接：
  - spring.datasource.url: jdbc:mysql://<DB主机>:3306/aj_report?...
  - spring.datasource.username: <用户名>
  - spring.datasource.password: <密码>
- 端口：server.port: 9095（如需更改）。
- 文件下载公网地址：
  - gaea.subscribes.oss.downloadPath: http://<服务IP>:<端口>/file/download
  - 请将示例中的 10.108.26.197 替换为后端实际对外 IP 或域名。
- 上传存储路径（无 MinIO/S3 时走 NFS/本地）：
  - Windows 示例：gaea.subscribes.oss.nfs.path: D:\\upload\\（确保目录存在且读写权限正常）
  - Linux 示例：gaea.subscribes.oss.nfs.path: /app/disk/upload/

可选项：
- spring.profiles.active: dev（按需切换环境）。
- customer.user.default.password: 新建用户默认密码，取值见项目根 `config/.env` 的 `AJ_USER_DEFAULT_PASSWORD`（不入库，详见 `config/README.md`）。
- Redis 如需启用，参考注释段并按需配置。

---

## 4. 启动与停止

### Windows
- 方式一：双击脚本
  - 进入：[aj-report-1.3.0/aj-report-1.3.0/bin](aj-report-1.3.0/aj-report-1.3.0/bin)，双击 start.bat。
- 方式二：命令行启动

```bat
cd aj-report-1.3.0\aj-report-1.3.0\bin
start.bat
```

- 停止/重启（Linux 脚本更完整，Windows 版本通常通过关闭窗口或任务管理器结束进程，必要时建议使用 Linux 部署以便管理 PID）：

### Linux
- 赋权并启动：

```bash
cd aj-report-1.3.0/aj-report-1.3.0/bin
chmod +x start.sh stop.sh restart.sh
./start.sh
```

- 查看日志提示：脚本会输出日志位于 logs/aj-report.log。
- 停止：

```bash
./stop.sh
```

- 重启：

```bash
./restart.sh
```

---

## 5. 访问与验证
- 后端默认端口：9095。
- 本机访问示例：
  - http://localhost:9095/
- 若设置了公网地址，请使用 http://<服务IP或域名>:<端口>/。
- 初次运行时，Flyway 会尝试初始化数据库。若数据库权限不足或连接信息错误，服务会启动失败，请参考下文排错。

---

## 6. 日志与排错
- 日志目录：
  - Linux 脚本提示为：[aj-report-1.3.0/aj-report-1.3.0/logs](aj-report-1.3.0/aj-report-1.3.0/logs)
  - Windows 脚本未重定向日志，可关注控制台输出或检查发行版 logs 目录。
- 常见问题：
  - 未配置 JDK：Windows 启动脚本会提示未找到 JAVA_HOME，请在 start.bat 第 4 行设置正确路径。
  - 端口占用：调整 server.port，或释放 9095 后重试。
  - 数据库无法连接：核对 spring.datasource.* 信息、账号权限、网络连通。
  - 文件存储不可写：确认 gaea.subscribes.oss.nfs.path 所指目录存在且有写权限。
  - Flyway 初始化失败：检查库版本/权限；必要时先手动导入 [aj_report.sql](aj_report.sql) 并重试。

快速检查端口占用示例：
```bat
:: Windows
netstat -ano | findstr :9095
```
```bash
# Linux
ss -lntp | grep 9095
```

---

## 7. 导入示例大屏（可选）
- 若需演示或快速体验，可以将 [data_board.sql](data_board.sql) 导入至 data_board 库，并在系统中配置对应的数据源与报表。
- 如需导入现成的看板配置，可参考 [QIWOebbw/dashboard.json](QIWOebbw/dashboard.json)（具体导入入口以 AJ-Report 前端界面“大屏管理/导入”功能为准）。

---

## 8. 目录结构说明
- [aj-report-1.3.0/aj-report-1.3.0/bin](aj-report-1.3.0/aj-report-1.3.0/bin)：启动、停止、重启脚本，以及缓存/日志目录。
- [aj-report-1.3.0/aj-report-1.3.0/conf](aj-report-1.3.0/aj-report-1.3.0/conf)：应用配置（bootstrap.yml）。
- [aj-report-1.3.0/aj-report-1.3.0/lib](aj-report-1.3.0/aj-report-1.3.0/lib)：后端主程序与依赖包。
- [aj_report.sql](aj_report.sql)：AJ-Report 系统库初始化数据脚本。
- [data_board.sql](data_board.sql)：示例数据看板库初始化脚本。

---

## 9. 生产部署建议
- 以 Linux 后台运行为主（start.sh 使用 nohup，更利于守护与日志管理）。
- 将 downloadPath 指向外网可达的域名，并使用反向代理（如 Nginx）终止 HTTPS。
- 配置独立持久化的上传目录，并定期备份。
- 数据库账号最小权限原则，仅开放必需权限给 aj_report 与相关库。

如需我帮你完善特定环境（例如指定 IP、域名、MinIO/S3 配置或 Nginx 反代示例），告诉我你的环境参数即可。