@echo off
chcp 65001 >nul

REM 切到本檔所在目錄，雙擊執行時亦可正確解析 config / input / output 等相對路徑
pushd "%~dp0"

echo ========================================
echo  傷害及健康保險共保月帳單報表產生系統
echo ========================================
echo.

REM ===== 指定 Java 17 =====
if exist "C:\Program Files\Amazon Corretto\jdk17.0.18_9" (
    set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk17.0.18_9"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo 使用 Java：
java -version
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
