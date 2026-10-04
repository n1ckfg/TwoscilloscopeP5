@echo off
rem Builds ..\library\TwoscilloscopeP5.jar from twoscilloscopeP5\*.java, the
rem Hershey fonts and the beam shader. Set CORE_JAR to Processing's core.jar
rem (and JAVA_HOME to its JDK) if they aren't in the default install folder.

setlocal
cd /d "%~dp0"

if not defined CORE_JAR (
  for %%f in ("C:\Program Files\Processing\app\resources\core\library\core-*.jar") do set "CORE_JAR=%%~f"
)
if not defined CORE_JAR (
  if exist "C:\Program Files\Processing\core\library\core.jar" set "CORE_JAR=C:\Program Files\Processing\core\library\core.jar"
)
if not defined CORE_JAR (
  echo Couldn't find Processing's core.jar: set CORE_JAR=C:\path\to\core.jar
  exit /b 1
)

if not defined JAVA_HOME (
  if exist "C:\Program Files\Processing\app\resources\jdk\bin\javac.exe" set "JAVA_HOME=C:\Program Files\Processing\app\resources\jdk"
)
if not defined JAVA_HOME (
  if exist "C:\Program Files\Processing\java\bin\javac.exe" set "JAVA_HOME=C:\Program Files\Processing\java"
)
set "JAVAC=javac"
set "JAR=jar"
if defined JAVA_HOME set "JAVAC=%JAVA_HOME%\bin\javac"
if defined JAVA_HOME set "JAR=%JAVA_HOME%\bin\jar"

echo core: %CORE_JAR%
if exist build\classes rmdir /s /q build\classes
mkdir build\classes\twoscilloscopeP5\hershey_fonts
mkdir build\classes\twoscilloscopeP5\shaders
if not exist ..\library mkdir ..\library
"%JAVAC%" --release 17 -encoding UTF-8 -cp "%CORE_JAR%" -d build\classes twoscilloscopeP5\*.java || exit /b 1
copy /y twoscilloscopeP5\hershey_fonts\* build\classes\twoscilloscopeP5\hershey_fonts\ > nul
copy /y twoscilloscopeP5\shaders\* build\classes\twoscilloscopeP5\shaders\ > nul
"%JAR%" cfm ..\library\TwoscilloscopeP5.jar build\manifest.txt -C build\classes . || exit /b 1
rmdir /s /q build\classes
echo built ..\library\TwoscilloscopeP5.jar
