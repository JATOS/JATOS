@REM JATOS loader for Windows
@setlocal enabledelayedexpansion

@echo off

rem IP address and port (DEPRECATED - use 'conf/jatos.conf' instead)
set address=127.0.0.1
set port=9000

rem Don't change after here unless you know what you're doing
rem ###################################

if "%JATOS_HOME%"=="" (
  set "APP_HOME=%~dp0"

  rem Also set the old env name for backwards compatibility
  set "JATOS_HOME=%~dp0"
) else (
  set "APP_HOME=%JATOS_HOME%"
)

set "APP_LIB_DIR=%APP_HOME%\lib\"

set LOCAL_JRE=jre\win64_jre

rem Detect if we were double clicked, although theoretically A user could
rem manually run cmd /c
for %%x in (!cmdcmdline!) do if %%~x==/c set DOUBLECLICKED=1

if _%DOUBLECLICKED%_==_1_ (
  call :start
  exit
)

rem # If we were started from CMD, evaluate start parameter
if "%1"=="start" (
  call :start %*
  exit /B 0
) else if "%1"=="stop" (
  call :stop
  exit /B 0
) else (
  @echo "Usage: loader.bat start|stop"
  exit /B 0
)

exit /B 0

rem ### Functions ###

:start
  rem # Check if JATOS is already running
  wmic process where "name like '%%java%%'" get commandline | findstr /i /c:"jatos" > NUL && (
    echo JATOS already running
    if _%DOUBLECLICKED%_==_1_ pause
    goto:eof
  )
  rem # If there is an old RUNNING_PID file of an earlier JATOS run that wasn't orderly closed delete it
  IF EXIST "%JATOS_HOME%\RUNNING_PID" (
    del "%JATOS_HOME%\RUNNING_PID"
  )

  echo Starting JATOS ... please wait

  rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
  set _JAVA_OPTS=%JAVA_OPTS%
  if "!_JAVA_OPTS!"=="" set _JAVA_OPTS=!CFG_OPTS!

  rem We keep in _JAVA_PARAMS all -J-prefixed and -D-prefixed arguments
  rem "-J" is stripped, "-D" is left as is, and everything is appended to JAVA_OPTS
  set _JAVA_PARAMS=
  set _APP_ARGS=

  rem # Generate application secret for the Play framework
  rem # If it's the first start, create a new secret, otherwise load it from the file.
  IF NOT EXIST "%JATOS_HOME%\play.http.secret.key" (
    set rand=%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%%RANDOM%
    echo !rand!>"%JATOS_HOME%\play.http.secret.key"
  )
  set /p SECRET=<"%JATOS_HOME%\play.http.secret.key"
  
  call :checkjava
  if errorlevel 1 (
    exit /B 1
  )

  if not exist "%JATOS_HOME%\logs" mkdir "%JATOS_HOME%\logs"

  rem # Start JATOS with configuration file and application secret
  set JATOS_OPTS=-Dconfig.file="conf/jatos.conf" -Djatos.secret=!SECRET! -Djatos.http.port=%port% -Djatos.http.address=%address% -Dfile.encoding="UTF-8"
  if _%DOUBLECLICKED%_==_1_ (
    set JATOS_OPTS=-Dpidfile.path="NUL" %JATOS_OPTS%
  ) else (
    set JATOS_OPTS=-Dpidfile.path="%JATOS_HOME%\RUNNING_PID" %JATOS_OPTS%
  )

  set "APP_CLASSPATH=%JATOS_HOME%\lib\*"

  call :process_args %SCRIPT_CONF_ARGS% %%*

  set _JAVA_OPTS=--illegal-access=permit --add-opens java.base/java.lang=ALL-UNNAMED !_JAVA_OPTS! !_JAVA_PARAMS!

  set "APP_MAIN_CLASS=play.core.server.ProdServerStart"

  set CMD="%_JAVACMD%" !_JAVA_OPTS! !JATOS_OPTS! -cp "%APP_CLASSPATH%" %APP_MAIN_CLASS% !_APP_ARGS!
  cd %JATOS_HOME%
  %CMD% 2>&1

  goto:eof

:stop
  if not exist "%JATOS_HOME%\RUNNING_PID" (
    echo This JATOS was not running
    goto:eof
  )
  echo Stopping JATOS
  set /p PID=<"%JATOS_HOME%\RUNNING_PID"
  taskkill /pid %PID% /f
  if errorlevel 1 (
    del "%JATOS_HOME%\RUNNING_PID"
	echo ...failed
  ) else (
    del "%JATOS_HOME%\RUNNING_PID"
    echo ...stopped
  )
  goto:eof
  
:checkjava
  rem We use the value of the JAVACMD environment variable if defined
  set _JAVACMD=%JAVACMD%

  rem # Java's path can be defined in PATH or JAVA_HOME
  rem # Don't confuse JAVA_HOME with JATOS_HOME
  if exist "%JATOS_HOME%\%LOCAL_JRE%" (
    set "JAVA_HOME=%JATOS_HOME%\%LOCAL_JRE%"
	echo JATOS uses local JRE
  )
  if "%_JAVACMD%"=="" (
    if not "%JAVA_HOME%"=="" (
      if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
    )
  )
  
  if "%_JAVACMD%"=="" set _JAVACMD=java

  rem Detect if this java is ok to use.
  for /F %%j in ('"%_JAVACMD%" -version  2^>^&1') do (
    if %%~j==java set JAVAINSTALLED=1
    if %%~j==openjdk set JAVAINSTALLED=1
  )
  
  rem BAT has no logical or, so we do it OLD SCHOOL! Oppan Redmond Style
  set JAVAOK=true
  if not defined JAVAINSTALLED set JAVAOK=false

  if "%JAVAOK%"=="false" (
    echo.
    echo A Java JDK or JRE is not installed or cannot be found.
    echo.
    echo Please go to
    echo   http://www.oracle.com/technetwork/java/javase/downloads/index.html
    echo and download a valid Java JRE and install before running JATOS.
    echo.
    echo If you think this message is in error, please check
    echo your environment variables to see if "java.exe" is
    echo available via JAVA_HOME or PATH.
    echo.

    if defined DOUBLECLICKED pause
      exit /B 1
    )
  exit /b 0

rem Processes incoming arguments and places them in appropriate global variables
:process_args
  :param_loop
  call set _PARAM1=%%1
  set "_TEST_PARAM=%~1"

  if ["!_PARAM1!"]==[""] goto param_afterloop


  rem ignore arguments that do not start with '-'
  if "%_TEST_PARAM:~0,1%"=="-" goto param_java_check
  set _APP_ARGS=!_APP_ARGS! !_PARAM1!
  shift
  goto param_loop

  :param_java_check
  if "!_TEST_PARAM:~0,2!"=="-J" (
    rem strip -J prefix
    set _JAVA_PARAMS=!_JAVA_PARAMS! !_TEST_PARAM:~2!
    shift
    goto param_loop
  )

  if "!_TEST_PARAM:~0,2!"=="-D" (
    rem test if this was double-quoted property "-Dprop=42"
    for /F "delims== tokens=1,*" %%G in ("!_TEST_PARAM!") DO (
      if not ["%%H"] == [""] (
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
      ) else if [%2] neq [] (
        rem it was a normal property: -Dprop=42 or -Drop="42"
        call set _PARAM1=%%1=%%2
        set _JAVA_PARAMS=!_JAVA_PARAMS! !_PARAM1!
        rem # Overwrite global variables address and port if we have them
        if "%1%" == "-Djatos.http.port" (
          set port=%2%
        ) else if "%1%" == "-Djatos.http.address" (
          set address=%2%
        )
        shift
      )
    )
  ) else (
    if "!_TEST_PARAM!"=="-main" (
      call set CUSTOM_MAIN_CLASS=%%2
      shift
    ) else (
      set _APP_ARGS=!_APP_ARGS! !_PARAM1!
    )
  )
  shift
  goto param_loop
  :param_afterloop

exit /B 0

