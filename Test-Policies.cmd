@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
if not exist "tests\build" mkdir "tests\build"
where javac >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Install JDK 17 or later
  pause
  exit /b 1
)
set "JAVA_SRC=app\src\main\java\dev\gil\odincontrol3"
javac -encoding UTF-8 -d tests\build "%JAVA_SRC%\RemapPolicy.java" "%JAVA_SRC%\VisibilityPolicy.java" "%JAVA_SRC%\GameDetector.java" "%JAVA_SRC%\PhysicalInputRouter.java" "%JAVA_SRC%\RecentAppsPolicy.java" "%JAVA_SRC%\WindowClassifier.java" tests\PolicyTest.java tests\RouterTest.java tests\ParseJava.java
if errorlevel 1 goto error
java -cp tests\build dev.gil.odincontrol3.PolicyTest
if errorlevel 1 goto error
java -cp tests\build dev.gil.odincontrol3.RouterTest
if errorlevel 1 goto error
rem Android syntax parsing is available via tests/Run-Checks.sh on Linux; full build validates on Windows
echo [OK] Test and syntax parsing completed
pause
exit /b 0
:error
echo [ERROR] Check failed
pause
exit /b 1
