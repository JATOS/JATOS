@REM JATOS loader for Windows
@echo off
@setlocal EnableDelayedExpansion

where powershell.exe >NUL 2>&1 || (
  echo PowerShell was not found.
  exit /B 1
)

if not defined JATOS_HOME (
  set "JATOS_HOME=%~dp0"
  set "JATOS_HOME=!JATOS_HOME:~0,-1!"
)

set "LOCAL_JRE=jre\win64_jre"

rem Detect if we were double clicked, although theoretically a user could manually run cmd /c
for %%x in (!cmdcmdline!) do if /I "%%~x"=="/c" set "DOUBLECLICKED=1"

if defined DOUBLECLICKED (
  call :start %*
  set "EXIT_CODE=!ERRORLEVEL!"
  pause
  exit /B !EXIT_CODE!
)

rem If launched from an existing Command Prompt, evaluate the command.
if /I "%~1"=="start" (
  call :start %*
  exit /B !ERRORLEVEL!
) else if /I "%~1"=="stop" (
  call :stop
  exit /B !ERRORLEVEL!
) else (
  echo Usage: loader.bat start^|stop
  exit /B 1
)


rem ### Functions ###

:start
  rem Check if JATOS is already running
  call :is_running
  if not errorlevel 1 (
    echo JATOS is already running.
    exit /B 1
  )

  rem If there is an old RUNNING_PID file of an earlier JATOS run that wasn't orderly closed delete it
  del /Q "%JATOS_HOME%\RUNNING_PID" 2>NUL
  if exist "%JATOS_HOME%\RUNNING_PID" (
    echo Could not remove stale RUNNING_PID.
    exit /B 1
  )

  echo Starting JATOS ... please wait.

  rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
  set "_JAVA_OPTS=%JAVA_OPTS%"
  if "!_JAVA_OPTS!"=="" set "_JAVA_OPTS=!CFG_OPTS!"

  rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
  rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
  set "_JAVA_PARAMS="
  set "_APP_ARGS="

  rem Generate a fallback secret. This is inherited by the Java process as the GENERATED_SECRET environment variable.
  rem It is only used if jatos.conf/jatos.secret or env var JATOS_SECRET aren't used.
  set "GENERATED_SECRET="
  for /F "usebackq delims=" %%S in (`powershell.exe -NoProfile -Command ^
    "$b = New-Object byte[] 48; " ^
    "$rng = [Security.Cryptography.RandomNumberGenerator]::Create(); " ^
    "try { $rng.GetBytes($b) } finally { $rng.Dispose() }; " ^
    "[Convert]::ToBase64String($b)"`) do (
    set "GENERATED_SECRET=%%S"
  )
  if not defined GENERATED_SECRET (
    echo Could not generate a fallback application secret.
    exit /B 1
  )

  call :checkjava
  if errorlevel 1 (
    exit /B 1
  )

  if not exist "%JATOS_HOME%\logs" (
    mkdir "%JATOS_HOME%\logs" || (
      echo Could not create "%JATOS_HOME%\logs".
      exit /B 1
    )
  )

  set "JATOS_OPTS=-Dconfig.file="%JATOS_HOME%\conf\jatos.conf" -Dfile.encoding=UTF-8"
  if defined DOUBLECLICKED (
    set "JATOS_OPTS=-Dpidfile.path=NUL !JATOS_OPTS!"
  ) else (
    set "JATOS_OPTS=-Dpidfile.path="%JATOS_HOME%\RUNNING_PID" !JATOS_OPTS!"
  )

  set "APP_CLASSPATH=%JATOS_HOME%\lib\*"

  call :process_args %SCRIPT_CONF_ARGS% %*

  set "_JAVA_OPTS=--add-opens java.base/java.lang=ALL-UNNAMED !_JAVA_OPTS! !_JAVA_PARAMS!"

  set "APP_MAIN_CLASS=play.core.server.ProdServerStart"

  cd /D "%JATOS_HOME%" || (
    echo Could not change to "%JATOS_HOME%".
    exit /B 1
  )

  rem Start JATOS
  "%_JAVACMD%" !_JAVA_OPTS! !JATOS_OPTS! -cp "%APP_CLASSPATH%" %APP_MAIN_CLASS% !_APP_ARGS! 2>&1
  set "EXIT_CODE=!ERRORLEVEL!"

  set "GENERATED_SECRET="
  exit /B !EXIT_CODE!

:is_running
  set "PID="
  if not exist "%JATOS_HOME%\RUNNING_PID" exit /B 1

  set /p "PID="<"%JATOS_HOME%\RUNNING_PID"
  if not defined PID exit /B 1

  echo(!PID!| findstr /R /X "[0-9][0-9]*" >NUL || exit /B 1

  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
    "$p = Get-CimInstance Win32_Process -Filter 'ProcessId = !PID!' 2>$null; " ^
    "if ($p -and $p.Name -ieq 'java.exe' -and $p.CommandLine -match 'jatos') { exit 0 } else { exit 1 }"

  exit /B %ERRORLEVEL%

:pid_exists
  powershell.exe -NoProfile -Command "if (Get-Process -Id %~1 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
  exit /B %ERRORLEVEL%

:stop
  call :is_running
  if errorlevel 1 (
    echo This JATOS was not running.
    del /Q "%JATOS_HOME%\RUNNING_PID" 2>NUL
    exit /B 1
  )

  echo Stopping JATOS.
  taskkill /PID !PID! >NUL 2>&1

  for /L %%I in (1,1,20) do (
    call :pid_exists !PID!
    if errorlevel 1 goto stopped

    timeout /T 1 /NOBREAK >NUL
  )

  echo JATOS did not stop gracefully. Forcing termination.
  taskkill /PID !PID! /F >NUL 2>&1

:stopped
  del /Q "%JATOS_HOME%\RUNNING_PID" 2>NUL
  echo JATOS stopped.
  exit /B 0
  
:checkjava
  set "_JAVACMD=%JAVACMD%"
  set "JAVA_VERSION="
  set "JAVA_MAJOR="

  rem Prefer bundled Java.
  if exist "%JATOS_HOME%\%LOCAL_JRE%\bin\java.exe" (
    set "JAVA_HOME=%JATOS_HOME%\%LOCAL_JRE%"
    set "_JAVACMD=%JATOS_HOME%\%LOCAL_JRE%\bin\java.exe"
    echo JATOS uses bundled Java
  )

  rem Then JAVA_HOME.
  if not defined _JAVACMD (
    if defined JAVA_HOME (
      if exist "%JAVA_HOME%\bin\java.exe" (
        set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
        echo JATOS uses Java from JAVA_HOME
      )
    )
  )

  rem Finally PATH.
  if not defined _JAVACMD (
    where java.exe >NUL 2>&1 || (
      echo.
      echo Java was not found. JATOS requires Java 21 or newer.
      exit /B 1
    )

    set "_JAVACMD=java.exe"
    echo JATOS uses Java from PATH
  )

  "%_JAVACMD%" -version >NUL 2>&1 || (
    echo Could not execute Java at "%_JAVACMD%".
    exit /B 1
  )

  rem Determine Java version.
  for /F "tokens=1,* delims==" %%A in ('
    ^""%_JAVACMD%" -XshowSettings:properties -version 2^>^&1^"
  ') do (
    if "%%A"=="    java.version " set "JAVA_VERSION=%%B"
  )

  rem Trim leading space.
  for /F "tokens=* delims= " %%V in ("!JAVA_VERSION!") do set "JAVA_VERSION=%%V"

  if not defined JAVA_VERSION (
    echo Could not determine the Java version.
    exit /B 1
  )

  for /F "tokens=1 delims=." %%V in ("!JAVA_VERSION!") do set "JAVA_MAJOR=%%V"

  rem Old version scheme, such as 1.8.
  if "!JAVA_MAJOR!"=="1" (
    for /F "tokens=2 delims=." %%V in ("!JAVA_VERSION!") do set "JAVA_MAJOR=%%V"
  )

  echo(!JAVA_MAJOR!| findstr /R /X "[0-9][0-9]*" >NUL || (
    echo Could not parse Java version "!JAVA_VERSION!".
    exit /B 1
  )

  if !JAVA_MAJOR! LSS 21 (
    echo JATOS requires Java 21 or newer.
    echo Found Java !JAVA_VERSION! at "%_JAVACMD%".
    exit /B 1
  )

  echo Using Java !JAVA_VERSION! from "%_JAVACMD%"
  exit /B 0

rem Processes incoming arguments and places them in appropriate global variables
:process_args
if /I "%~1"=="start" shift

:param_loop
if "%~1"=="" goto param_afterloop

  set "_TEST_PARAM=%~1"

  rem -J options go to the JVM without the -J prefix
  if /I "!_TEST_PARAM:~0,2!"=="-J" (
      set "_JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!"
      shift
      goto param_loop
  )

  rem -D options go to the JVM unchanged
  if /I "!_TEST_PARAM:~0,2!"=="-D" (
      set "_JAVA_PARAMS=!_JAVA_PARAMS! %~1"
      shift
      goto param_loop
  )

  rem Everything else is passed to the application
  set "_APP_ARGS=!_APP_ARGS! %~1"
  shift
  goto param_loop

:param_afterloop
  exit /B 0

