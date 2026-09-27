# TDProject-main 接入 AJ-Report 使用说明

## 1. 这次接入是什么

这次接入的目标，不是把 `大屏代码` 直接合并进 `TDProject-main`，而是采用下面这种方式：

- `AJ-Report` 继续作为一个独立服务单独运行
- `TDProject-main` 提供统一访问入口
- `TDProject-main` 补充一份新手也能照着做的接入文档

也就是说：

- `TDProject-main` 负责主后端业务
- `AJ-Report` 负责大屏展示
- 两者通过访问地址关联，不做源码级硬合并

这样做的好处是：

- 不容易引发 Spring Boot 版本冲突
- 不会把 AJ-Report 的独立配置、数据库、上传目录强行塞进当前项目
- 后续测试环境、生产环境改地址也更方便

---

## 2. 目录说明

### 主后端项目

```text
<项目根>\src\backend-main
```

这是你当前主要运行的 Spring Boot 后端项目。

### 大屏发布包

```text
<项目根>\src\dashboard-ajreport
```

这里不是前端源码，而是 AJ-Report 的独立发行包。

其中几个关键文件如下：

- 主程序 jar：
  `lib\aj-report-1.3.0.RELEASE.jar`
- 配置文件：
  `conf\bootstrap.yml`
- Windows 启动脚本：
  `bin\start.bat`
- Linux 启动脚本：
  `bin\start.sh`
- 初始化 SQL：
  `aj_report.sql`
  `data_board.sql`

---

## 3. 启动前准备

正式操作前，先确保下面几件事已经准备好。

### 3.1 准备 JDK

建议区分使用：

- `TDProject-main`：使用 JDK 17
- `AJ-Report`：优先使用 JDK 8

原因：

- `TDProject-main` 当前是 Spring Boot 3 项目，`pom.xml` 中 `java.version` 是 `17`
- AJ-Report 1.3.0 属于独立旧版发行包，通常用 JDK 8 更稳妥

如果你电脑上同时装了多个 JDK，启动 AJ-Report 时请确认用的是 JDK 8。

### 3.2 准备 MySQL

需要本地或服务器上有一个可用的 MySQL 实例。

建议至少准备两个数据库：

- `foreign_trade_qa_db`：给 `TDProject-main` 用
- `aj_report`：给 AJ-Report 用

### 3.3 确保主后端可正常运行

先确认 `TDProject-main` 在不接 AJ-Report 的情况下就能正常启动。

默认端口：

```text
http://localhost:8080
```

如果主后端自己都还没跑起来，先不要急着接大屏。

---

## 4. 初始化 AJ-Report 数据库

### 4.1 创建数据库

先在 MySQL 中创建数据库：

```sql
CREATE DATABASE aj_report DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 4.2 导入 SQL

把下面两个 SQL 文件都导入到 `aj_report` 数据库中：

- `aj_report.sql`
- `data_board.sql`

文件位置一般在：

```text
<项目根>\src\dashboard-ajreport
```

### 4.3 导入方式示例

如果你用的是 Navicat：

1. 右键 `aj_report` 数据库
2. 选择“运行 SQL 文件”
3. 先导入 `aj_report.sql`
4. 再导入 `data_board.sql`

如果你用命令行，也可以执行：

```bash
mysql -uroot -p aj_report < aj_report.sql
mysql -uroot -p aj_report < data_board.sql
```

注意：命令中的账号、密码、文件路径要按你的实际环境修改。

---

## 5. 修改 AJ-Report 配置

AJ-Report 的配置文件路径：

```text
<项目根>\src\dashboard-ajreport\conf\bootstrap.yml
```

至少要重点检查下面几项。

### 5.1 数据库配置

找到：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${AJ_DB_HOST:localhost}:${AJ_DB_PORT:3306}/${AJ_DB_NAME:aj_report}?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true
    username: ${AJ_DB_USERNAME:root}
    password: ${AJ_DB_PASSWORD:123456}
```

你需要把它改成你自己实际能连通的 MySQL 信息 —— 凭据统一放在项目根 `config/.env`
（不入库，详见 `config/README.md`），改那里即可，不要改这里的源码默认值。

### 5.2 端口配置

找到：

```yaml
server:
  port: 9095
```

默认 AJ-Report 跑在 `9095` 端口。

如果这个端口被占用，可以改成别的端口，比如 `9096`。

但注意：如果你改了这里，后面 `TDProject-main` 里的接入地址也要跟着改。

### 5.3 下载地址配置

找到：

```yaml
spring:
  gaea:
    subscribes:
      oss:
        downloadPath: http://localhost:9095/file/download
```

这项表示上传文件成功后，AJ-Report 生成下载地址时使用的基础地址。

如果你部署到服务器，请把 `localhost` 改成服务器实际 IP 或域名，例如：

```yaml
downloadPath: http://192.168.1.100:9095/file/download
```

### 5.4 上传目录配置

找到：

```yaml
spring:
  gaea:
    subscribes:
      oss:
        nfs:
          path: /app/disk/upload/
```

这项表示 AJ-Report 上传文件保存的位置。

Windows 示例：

```yaml
path: D:/aj-report/upload/
```

Linux 示例：

```yaml
path: /app/aj-report/upload/
```

注意：

- 目录通常不会自动创建
- 目录必须真实存在
- 当前启动用户必须对这个目录有读写权限

---

## 6. 启动 AJ-Report

### 6.1 Windows 启动方式

进入目录：

```text
<项目根>\src\dashboard-ajreport\bin
```

双击或执行：

```bat
start.bat
```

### 6.2 Linux 启动方式

进入 `bin` 目录后执行：

```bash
sh start.sh
```

或者：

```bash
chmod +x start.sh
./start.sh
```

### 6.3 如何判断启动成功

启动成功后，优先访问：

```text
http://localhost:9095/
```

如果你改过端口，就访问修改后的端口。

能打开 AJ-Report 登录页或首页，通常就说明服务已经起来了。

如果打不开，先检查：

- JDK 版本是不是合适
- 数据库有没有连上
- 9095 端口有没有被占用
- `bootstrap.yml` 有没有配错

---

## 7. 启动 TDProject-main

### 7.1 检查主后端 AJ-Report 接入配置

文件位置：

```text
<项目根>\src\backend-main\src\main\resources\application.yaml
```

当前已新增配置：

```yaml
integration:
  aj-report:
    base-url: ${AJ_REPORT_BASE_URL:http://127.0.0.1:9095}
    entry-path: ${AJ_REPORT_ENTRY_PATH:/}
    screen1-path: ${AJ_REPORT_SCREEN1_PATH:/index.html#/aj/eYmVi5sx}
    screen2-path: ${AJ_REPORT_SCREEN2_PATH:/index.html#/aj/9jMigyT7}
    embed-enabled: ${AJ_REPORT_EMBED_ENABLED:true}
    frame-title: ${AJ_REPORT_FRAME_TITLE:AJ-Report 大屏入口}
```

各字段含义：

- `base-url`：AJ-Report 服务基础地址
- `entry-path`：AJ-Report 的默认入口路径，默认是 `/`
- `screen1-path`：前端“大屏1”使用的 AJ-Report 页面路径
- `screen2-path`：前端“大屏2”使用的 AJ-Report 页面路径
- `embed-enabled`：是否在统一入口页里用 iframe 内嵌
- `frame-title`：iframe 标题

如果你的 AJ-Report 跑在别的机器上，只需要改 `base-url`。

例如：

```yaml
integration:
  aj-report:
    base-url: http://192.168.1.100:9095
    entry-path: /
    screen1-path: /index.html#/aj/eYmVi5sx
    screen2-path: /index.html#/aj/9jMigyT7
    embed-enabled: true
    frame-title: AJ-Report 大屏入口
```

### 7.2 启动主后端

在 `TDProject-main` 根目录执行 Maven 启动，或者用 IDEA 运行主类：

主类位置：

```text
src/main/java/com/example/tdproject/TdProjectApplication.java
```

默认访问地址：

```text
http://localhost:8080/
```

---

## 8. 访问方式

接入完成后，你可以用两种方式访问大屏。

### 8.1 直接访问 AJ-Report

直接打开：

```text
http://localhost:9095/
```

这是最直接的方式，也最适合先排查 AJ-Report 自己是否正常。

### 8.2 通过 TDProject-main 统一入口访问

主后端已新增统一入口：

#### 大屏入口页

```text
http://localhost:8080/aj-report
```

这个页面会显示：

- 当前 AJ-Report 地址
- 当前入口路径
- 最终跳转地址
- 一键打开按钮
- 可选的 iframe 内嵌区域

#### 直接跳转接口

```text
http://localhost:8080/aj-report/go
```

访问后会直接重定向到 AJ-Report。

#### 当前配置查看接口

```text
http://localhost:8080/aj-report/config
```

可用来快速确认主后端当前读取到的 AJ-Report 配置是否正确。

---

## 9. 常见问题

### 9.1 9095 端口打不开

检查顺序：

1. AJ-Report 是否真的已经启动
2. `bootstrap.yml` 里的端口是不是改过
3. 端口是否被别的程序占用
4. 防火墙是否拦截

如果你把 AJ-Report 改成了 `9096`，那主后端也要同步修改：

```yaml
integration:
  aj-report:
    base-url: http://127.0.0.1:9096
```

### 9.2 数据库连接失败

重点检查 `conf/bootstrap.yml`：

- 数据库地址
- 数据库名是否是 `aj_report`
- 用户名密码是否正确
- MySQL 是否允许当前用户连接

另外确认：

- `aj_report.sql`
- `data_board.sql`

是否确实已经导入。

### 9.3 上传目录无权限

如果 AJ-Report 报上传、下载、附件相关错误，重点看：

```yaml
spring:
  gaea:
    subscribes:
      oss:
        nfs:
          path: ...
```

处理方式：

- 手动创建这个目录
- 确保启动 AJ-Report 的用户对目录有读写权限
- Windows 路径尽量写成 `D:/aj-report/upload/` 这种形式

### 9.4 iframe 无法加载

如果 `http://localhost:8080/aj-report` 页面里下方区域空白，但直接打开 AJ-Report 正常，通常是下面这些原因：

- AJ-Report 响应头限制了 iframe 嵌入
- 浏览器拦截了跨站嵌入
- 存在 `X-Frame-Options` 或 `Content-Security-Policy` 限制

这时建议：

- 先直接使用 `http://localhost:8080/aj-report/go`
- 或把 `embed-enabled` 改成 `false`

例如：

```yaml
integration:
  aj-report:
    embed-enabled: false
```

### 9.5 页面空白或跳转失败

优先检查：

1. `TDProject-main` 是否已启动
2. `http://localhost:8080/aj-report/config` 是否能返回 JSON
3. JSON 里的 `targetUrl` 是否正确
4. AJ-Report 服务是否能直接打开

如果 `config` 接口都打不开，说明是主后端问题。

如果 `config` 正常但 `targetUrl` 打不开，说明主要是 AJ-Report 地址配置问题。

---

## 10. 本地联调建议

建议严格按下面顺序联调，不要一上来就同时排两个系统。

### 第一步：先验证 AJ-Report 独立可用

先只做 AJ-Report 本身的事情：

- 导入数据库 SQL
- 改好 `bootstrap.yml`
- 启动 AJ-Report
- 打开 `http://localhost:9095/`

确认这一步成功后，再继续。

### 第二步：再启动 TDProject-main

确保主后端也正常启动：

- 访问 `http://localhost:8080/`
- 确认主项目没有报错

### 第三步：最后验证统一入口

依次验证：

1. `http://localhost:8080/aj-report/config`
2. `http://localhost:8080/aj-report/go`
3. `http://localhost:8080/aj-report`

推荐理解方式：

- `config` 用来确认配置值
- `go` 用来确认跳转功能
- `aj-report` 页面用来确认统一入口是否友好可用

---

## 11. 这次接入改了哪些文件

本次在 `TDProject-main` 中新增或修改了下面几个文件：

### 修改

- `src/main/resources/application.yaml`

新增了 AJ-Report 接入配置：

- `integration.aj-report.base-url`
- `integration.aj-report.entry-path`
- `integration.aj-report.embed-enabled`
- `integration.aj-report.frame-title`

### 新增

- `src/main/java/com/example/tdproject/controller/AjReportController.java`
- `src/main/resources/static/aj-report.html`

### 文档

- `TDProject-main/AJ-REPORT-接入使用说明.md`

---

## 12. 给新手的最短操作清单

如果你只想先跑起来，可以直接照下面做：

1. 创建 MySQL 数据库 `aj_report`
2. 导入 `aj_report.sql` 和 `data_board.sql`
3. 修改 `大屏代码` 里的 `conf/bootstrap.yml`
4. 用 JDK 8 启动 AJ-Report
5. 先确认 `http://localhost:9095/` 能打开
6. 再启动 `TDProject-main`
7. 打开 `http://localhost:8080/aj-report`

如果第 5 步都没成功，就不要继续查主后端，先把 AJ-Report 单独跑通。
