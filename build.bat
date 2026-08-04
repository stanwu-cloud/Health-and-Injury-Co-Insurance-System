@echo off
chcp 65001 >nul

REM 切到本檔所在目錄，雙擊執行時亦可正確解析相對路徑
pushd "%~dp0"

REM ===== 指定 Java 17 =====
if exist "C:\Program Files\Amazon Corretto\jdk17.0.18_9" (
    set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.18_9"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo 使用 Java：
java -version

echo.
echo 正在編譯專案...
call "%~dp0mvnw.cmd" clean package
set EXITCODE=%ERRORLEVEL%

if %EXITCODE% equ 0 (
    echo.
    echo 編譯成功！JAR 位於 target\health-and-injury-co-insurance-system.jar
) else (
    echo.
    echo 編譯失敗，請檢查錯誤訊息
)

popd

REM 自動化執行時請先設 NOPAUSE=1，避免卡在等待按鍵
if not defined NOPAUSE pause
exit /b %EXITCODE%
