@echo off
chcp 65001 > nul
echo =======================================================
echo     🚀 正在启动若依前端 Vue 成品全自动上云管道...
echo =======================================================

:: 🎯 1. 锁死本地 Vue 打包出来的 dist 目录和云端目标路径
set LOCAL_DIST=dist
set SERVER_IP=8.148.13.80
set REMOTE_DIR=/www/wwwroot/ry-vue-tinc

echo [1/1] 📦 正在将本地编译好的 dist 静态文件暴力覆盖至云端...
:: 💡 核心大招：利用 scp -r 递归传输整个文件夹内容，直接精准覆盖云端
scp -r "%LOCAL_DIST%\*" root@%SERVER_IP%:%REMOTE_DIR%/

if %ERRORLEVEL% NEQ 0 (
    echo ❌ [ERROR] 前端文件传输失败！请检查网络。
    pause
    exit /b %ERRORLEVEL%
)

echo =======================================================
echo     🎉 [SUCCESS] 前端 Vue 静态资源热更新成功！
echo =======================================================
pause