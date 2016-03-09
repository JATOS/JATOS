@REM JATOS loader for Windows
@setlocal enabledelayedexpansion

@echo off

rem Change IP address and port here
set address=127.0.0.1
set port=9000

rem Don't change after here unless you know what you're doing
rem ###################################

set JATOS_HOME=%~dp0
set JATOS_HOME=%JATOS_HOME:~0,-1%
set LOCAL_JRE=jre\win32_jre

rem Detect if we were double clicked
for %%x in (%cmdcmdline%) do if %%~x==/c set DOUBLECLICKED=1
if defined DOUBLECLICKED (
  call :start
  exit
)

rem If we were started from CMD, evaluate start parameter
if "%1"=="start" (
  call :start
  exit /b
) else if "%1"=="stop" (
  call :stop
  exit /b
) else (
  @echo "Usage: loader.bat start|stop"
  exit /b
)

exit /b

rem ### Functions ###

:start
  IF EXIST "%JATOS_HOME%\RUNNING_PID" (
    echo JATOS already running
	if defined DOUBLECLICKED pause
    goto:eof
  )

  echo Starting JATOS
  rem # Generate application secret for the Play framework
  rem # If it's the first start, create a new secret, otherwise load it from the file.
  IF NOT EXIST "%JATOS_HOME%\play.crypto.secret" (
    set rand=%RANDOM%%RANDOM%%RANDOM%
    echo !rand!>"%JATOS_HOME%\play.crypto.secret"
  )
  set /p SECRET=<"%JATOS_HOME%\play.crypto.secret"
  
  call :checkjava
  if errorlevel 1 (
    exit /b 1
  )

  rem # Start JATOS with configuration file and application secret
  set JATOS_OPTS=-Dconfig.resource="production.conf" -Dplay.crypto.secret=!SECRET! -Dhttp.port=%port% -Dhttp.address=%address%
  if defined DOUBLECLICKED (
    set JATOS_OPTS=-Dpidfile.path="NUL" %JATOS_OPTS%
  ) else (
    set JATOS_OPTS=-Dpidfile.path="%JATOS_HOME%\RUNNING_PID" %JATOS_OPTS%
  )
  set "APP_CLASSPATH=%JATOS_HOME%\lib\*"
  set "APP_MAIN_CLASS=play.core.server.NettyServer"
  set CMD=%JAVACMD% %JATOS_OPTS% -cp "%APP_CLASSPATH%" %APP_MAIN_CLASS%
  cd %JATOS_HOME%
  if defined DOUBLECLICKED (
    start /b %CMD% > nul
  ) else (
    start /b %CMD% > nul
  )
  
  echo To use JATOS type %address%:%port% in your browser's address bar
  goto:eof

:stop
  if not exist "%JATOS_HOME%\RUNNING_PID" (
    echo JATOS isn't running
    goto:eof
  )
  echo Stopping JATOS
  set /p PID=<"%JATOS_HOME%\RUNNING_PID"
  taskkill /pid %PID% /f
  if errorlevel 1 (
    echo ...failed
  ) else (
    del "%JATOS_HOME%\RUNNING_PID"
    echo ...stopped
  )
  goto:eof
  
:checkjava
  rem Java's path can be defined in PATH or JAVA_HOME
  rem Don't confuse JAVA_HOME with JATOS_HOME
  if exist "%JATOS_HOME%\%LOCAL_JRE%" (
    set "JAVA_HOME=%JATOS_HOME%\%LOCAL_JRE%"
	echo JATOS uses local JRE
  )
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" (
	  set "JAVACMD=%JAVA_HOME%\bin\java.exe"
	)
  )
  
  if "%JAVACMD%"=="" set JAVACMD=java

  rem Detect if this java is ok to use.
  for /F %%j in ('"%JAVACMD%" -version  2^>^&1') do (
    if %%~j==Java set JAVAINSTALLED=1
  )
  
  if "%JAVAINSTALLED%"=="" (
    echo.
    echo A Java JDK or JRE is not installed or cannot be found.
    echo.
    echo Please go to
    echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
    echo and download a valid Java JRE and install it before running JATOS.
    echo.
    echo If you think this message is in error, please check
    echo your environment variables to see if "java.exe" is
    echo available via JAVA_HOME or PATH.
    echo.
	
	if defined DOUBLECLICKED pause
    exit /b 1
  )
  goto:eof
  

