@echo off
chcp 65001 >nul
REM ================================================================
REM  中哈贸易系统 · 一键启动（Windows，主要用于本机验证）
REM
REM  部署到 Linux 服务器请使用 deploy/start-all.sh
REM
REM  关键：脚本先 cd 到项目根再启动 Java，
REM        这样配置里的 ${trade.home}（默认 = ${user.dir}）= 项目根，
REM        无需在任何配置文件里写死绝对路径。
REM ================================================================
setlocal

pushd "%~dp0.."
set "ROOT=%CD%"
echo 项目根: %ROOT%

REM ---- 统一凭据：载入 config\.env（真实值，不入库；详见 config\README.md）----
REM eol=# 跳过注释行；tokens=1* delims== 以第一个 = 切分；%%~b 去掉包裹的引号
if exist "%ROOT%\config\.env" (
  for /f "usebackq eol=# tokens=1* delims==" %%a in ("%ROOT%\config\.env") do (
    if not "%%~b"=="" set "%%a=%%~b"
  )
  echo 配置文件: config\.env
) else (
  echo 配置文件: 未找到 config\.env，将使用代码内默认值
  echo           执行 copy config\.env.example config\.env 后填写
)

if not defined TRADE_HOME set "TRADE_HOME=%ROOT%"
if not exist "runtime" mkdir runtime
if not exist "logs"    mkdir logs

set "MAIN_JAR="
for %%f in ("src\backend-main\target\*.jar") do (
  echo %%f | findstr /i /v "\.original$" >nul && set "MAIN_JAR=%%f"
)
set "TRAIN_JAR="
for %%f in ("src\backend-training\target\*.jar") do (
  echo %%f | findstr /i /v "\.original$" >nul && set "TRAIN_JAR=%%f"
)

if "%MAIN_JAR%"=="" (
  echo 错误：未找到主后端 jar，请先执行 mvn -f src\backend-main\pom.xml package -DskipTests
  popd & exit /b 1
)

echo.
echo 启动训练后端 (8081)...
if not "%TRAIN_JAR%"=="" (
  start "trade-training" cmd /c "java -jar "%TRAIN_JAR%" --server.port=8081 > logs\backend-training.log 2>&1"
  timeout /t 3 /nobreak >nul
) else (
  echo   未找到训练后端 jar，跳过
)

echo 启动主后端 (8080)...
start "trade-main" cmd /c "java -jar "%MAIN_JAR%" --server.port=8080 > logs\backend-main.log 2>&1"

echo.
echo ================================================================
echo  服务地址
echo    主后端    http://localhost:8080
echo    接口文档  http://localhost:8080/swagger-ui.html
echo    训练后端  http://localhost:8081/api
echo    Flask     http://localhost:5000/health
echo.
echo  前端（开发模式）：
echo    cd src\frontend-vue ^&^& npm run serve    -^> http://localhost:3000
echo.
echo  数据库初始化（首次部署必做）：
echo    python deploy\init_mysql.py --password ^<你的MySQL密码^>
echo ================================================================
popd
endlocal
