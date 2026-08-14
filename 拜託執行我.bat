@echo off
chcp 65001 >nul

REM 切到本檔所在目錄：雙擊執行或自其他目錄呼叫時，皆可正確解析
REM config / input / output 等相對路徑（每個結束點皆以 popd 還原）
pushd "%~dp0"

echo ========================================
echo   自動執行 Build + Run
echo ========================================
echo.

REM ===== 指定 Java =====
set "JAVA_EXE=C:\Users\user\.jdks\corretto-17.0.18\bin\java.exe"

REM ===== 檢查 Java =====
if not exist "%JAVA_EXE%" (
    echo [錯誤] 找不到 Java：
    echo %JAVA_EXE%
    popd
    pause
    exit /b 1
)

REM ===== 設定 JAVA_HOME =====
for %%i in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpi"
set "JAVA_HOME=%JAVA_HOME:~0,-1%"
for %%i in ("%JAVA_HOME%") do set "JAVA_HOME=%%~dpi"
set "JAVA_HOME=%JAVA_HOME:~0,-1%"

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo 使用 Java：
"%JAVA_EXE%" -version
echo.

REM ========================================
REM Step 1. Build
REM ========================================
echo [Step 1] 編譯中...
call mvnw.cmd clean package -DskipTests -q

if %ERRORLEVEL% neq 0 (
    echo ❌ 編譯失敗，停止執行
    popd
    pause
    exit /b 1
)

echo ✅ 編譯成功！
echo.

set "JAR_FILE=%~dp0target\health-and-injury-co-insurance-system.jar"

if not exist "%JAR_FILE%" (
    echo [錯誤] 找不到 JAR 檔案: %JAR_FILE%
    echo 請先執行 build.bat 進行編譯
    popd
    if not defined NOPAUSE pause
    exit /b 2
)

REM CLI 批次模式。年月預設取自 config\application.xlsx；
REM 如需覆寫請於執行時加上參數，例如：run.bat --year=115 --month=5
java -jar "%JAR_FILE%" --mode=cli --spring.config.additional-location=file:./config/ %*
set EXITCODE=%ERRORLEVEL%

echo.
echo ========================================
if %EXITCODE% equ 0 (
    echo  執行完成，請查看 output 資料夾
) else (
    echo  執行結束，代碼 %EXITCODE%，請查看 logs\report.json
)
echo ========================================

popd

REM 排程執行時請先設 NOPAUSE=1，避免卡在等待按鍵
if not defined NOPAUSE pause
exit /b %EXITCODE%
