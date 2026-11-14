@echo off
setlocal

set APP_BASE_NAME=%~n0
set APP_HOME=%~dp0

if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo ERROR: JAVA_HOME is not set and no java.exe was found on PATH.
goto fail

:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME points to an invalid Java installation: %JAVA_HOME%
goto fail

:execute
"%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% equ 0 goto end
goto fail

:fail
exit /b 1

:end
endlocal
