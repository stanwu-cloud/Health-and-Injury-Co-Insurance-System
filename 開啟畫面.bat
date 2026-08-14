@echo off
chcp 65001 >nul
setlocal

REM ========================================================================
REM  雙擊開啟圖形畫面（GUI）用。
REM
REM  為什麼需要這支檔案：雙擊 .jar 能不能啟動，取決於這台電腦有沒有把 .jar
REM  關聯到 Java。以解壓縮方式安裝的 JDK（例如 IDE 下載到 %USERPROFILE%\.jdks\
REM  的版本）不會建立這個關聯，解壓縮軟體（7-Zip / WinRAR）也常把 .jar 關聯
REM  搶走，雙擊就會變成沒反應或開出壓縮檔視窗。本檔自行尋找 Java 並以絕對
REM  路徑啟動，完全繞過檔案關聯。
REM
REM  為什麼用 java.exe 而不是 javaw.exe：javaw 沒有主控台，Java 版本不符等
REM  啟動失敗會被靜默丟棄，症狀同樣是「雙擊沒反應」，最難查。保留這個視窗，
REM  出問題時才看得到原因。
REM
REM  Java 探測順序（詳見下方 :find_java）：環境變數 COINSURANCE_JAVA →
REM  JAVA_HOME → 常見安裝目錄 → PATH。只接受 17 以上的版本。
REM ========================================================================

pushd "%~dp0"

echo ========================================
echo   共保月帳單報表系統－開啟畫面
echo ========================================
echo.

call :find_java

if not defined JAVA_EXE (
    echo [錯誤] 這台電腦找不到可用的 Java（需要 17 以上版本）。
    echo.
    echo 已尋找過下列位置：
    echo   - 環境變數 COINSURANCE_JAVA
    echo   - 環境變數 JAVA_HOME
    echo   - %USERPROFILE%\.jdks\
    echo   - C:\Program Files\ 下的 Amazon Corretto、Microsoft、Java、
    echo     Eclipse Adoptium、Zulu、BellSoft
    echo   - 系統 PATH
    echo.
    echo 請聯繫系統維護人員。若 Java 裝在其他位置，可設定環境變數
    echo COINSURANCE_JAVA 指向該 java.exe，本檔即會直接採用，毋須修改程式。
    popd
    pause
    exit /b 1
)

set "JAR_FILE=%~dp0target\health-and-injury-co-insurance-system.jar"

if not exist "%JAR_FILE%" (
    echo [錯誤] 找不到程式檔案：
    echo %JAR_FILE%
    echo.
    echo 請先執行 build.bat 進行編譯。
    popd
    pause
    exit /b 2
)

echo 使用 Java：%JAVA_EXE%
echo 版本：%JAVA_VER%
echo.
echo 啟動中，請稍候。畫面出現後即可操作。
echo 這個視窗請勿關閉，關閉會一併結束程式。
echo.

"%JAVA_EXE%" -jar "%JAR_FILE%" --spring.config.additional-location=file:./config/
set EXITCODE=%ERRORLEVEL%

if %EXITCODE% neq 0 (
    echo.
    echo ========================================
    echo  程式異常結束，代碼 %EXITCODE%
    echo  請將本畫面訊息與 logs\report.json 提供給維護人員
    echo ========================================
    popd
    pause
    exit /b %EXITCODE%
)

popd
exit /b 0


REM ========================================================================
REM  :find_java  依序探測候選位置，找到第一個 17 以上的版本就停。
REM              成功時設定 JAVA_EXE 與 JAVA_VER，失敗時兩者皆未定義。
REM ========================================================================
:find_java
set "JAVA_EXE="
set "JAVA_VER="

REM --- 1. 環境變數覆寫：不必改本檔即可指定 Java ---
if defined COINSURANCE_JAVA call :try_java "%COINSURANCE_JAVA%"

REM --- 2. JAVA_HOME（可能帶結尾反斜線，先去掉再組路徑）---
if defined JAVA_HOME call :try_java_home "%JAVA_HOME%"

REM --- 3. 常見安裝目錄（逐一列出其下的版本子目錄）---
call :scan_dir "%USERPROFILE%\.jdks"
call :scan_dir "C:\Program Files\Amazon Corretto"
call :scan_dir "C:\Program Files\Microsoft"
call :scan_dir "C:\Program Files\Java"
call :scan_dir "C:\Program Files\Eclipse Adoptium"
call :scan_dir "C:\Program Files\Zulu"
call :scan_dir "C:\Program Files\BellSoft"

REM --- 4. PATH ---
for /f "delims=" %%p in ('where java 2^>nul') do call :try_java "%%p"

goto :eof


REM  :try_java_home  去掉 JAVA_HOME 結尾的反斜線後再試
:try_java_home
set "JH=%~1"
if "%JH:~-1%"=="\" set "JH=%JH:~0,-1%"
call :try_java "%JH%\bin\java.exe"
goto :eof


REM  :scan_dir  試探某目錄下每個子目錄的 bin\java.exe
:scan_dir
if defined JAVA_EXE goto :eof
if not exist "%~1" goto :eof
for /d %%d in ("%~1\*") do call :try_java "%%d\bin\java.exe"
goto :eof


REM  :try_java  檢查單一 java.exe 是否存在且版本 >= 17，是則採用
:try_java
if defined JAVA_EXE goto :eof
if not exist "%~1" goto :eof

REM 版本輸出先導向暫存檔再解析：for /f 的內嵌命令一旦同時含引號路徑與管線，
REM cmd 會把整串誤判為命令名；導檔可完全避開該問題，也順便讓探測失敗的
REM 錯誤訊息不會噴到畫面上。
set "CAND_VER="
set "VER_TMP=%TEMP%\cojava_%RANDOM%.txt"
"%~1" -version > "%VER_TMP%" 2>&1
if errorlevel 1 (
    del "%VER_TMP%" >nul 2>&1
    goto :eof
)
for /f "tokens=3" %%v in ('findstr /i "version" "%VER_TMP%"') do (
    if not defined CAND_VER set "CAND_VER=%%~v"
)
del "%VER_TMP%" >nul 2>&1
if not defined CAND_VER goto :eof

REM 取主版本號：Java 9 以後為 "17.0.18"；Java 8 以前為 "1.8.0_401"（主版本 1，排除）
for /f "tokens=1 delims=." %%m in ("%CAND_VER%") do set "CAND_MAJOR=%%m"
echo %CAND_MAJOR%| findstr /r "^[0-9][0-9]*$" >nul || goto :eof
if %CAND_MAJOR% lss 17 goto :eof

set "JAVA_EXE=%~1"
set "JAVA_VER=%CAND_VER%"
goto :eof
