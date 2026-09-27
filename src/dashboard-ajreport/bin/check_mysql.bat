@echo off
chcp 65001 >nul
echo ====== MySQL连接问题完整诊断 ======
echo.

echo 1. MySQL服务状态:
sc query MySQL80
echo.

echo 2. 当前配置文件中的密码:
cd /d "%~dp0.."
findstr "password" conf\bootstrap.yml
echo.

echo 3. 测试配置文件中的密码:
set PASS=xuan123456
mysql -h 127.0.0.1 -u root -p%PASS% -e "SELECT '配置文件密码测试' as result;" 2>&1
echo.

echo 4. 尝试无密码连接:
mysql -u root -e "SELECT '无密码测试' as result;" 2>&1
echo.

echo 5. 查看MySQL错误日志:
for %%i in ("%ProgramData%\MySQL\MySQL Server 8.0\Data\*.err") do (
  echo 日志文件: %%i
  tail -5 "%%i"
)
echo.

echo 6. 创建测试用户方案:
echo 如果以上都失败，建议创建新用户:
echo   CREATE USER 'ajreport'@'localhost' IDENTIFIED BY 'AjReport123!';
echo   GRANT ALL ON aj_report.* TO 'ajreport'@'localhost';
echo   FLUSH PRIVILEGES;
echo.

pause