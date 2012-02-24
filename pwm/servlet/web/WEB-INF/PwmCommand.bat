@echo off
setLocal EnableDelayedExpansion

if NOT DEFINED JAVA_HOME goto err

set JAVA_OPTS="-Xmx1024m"
set CLASSPATH="
set CLASSPATH=!CLASSPATH!;.\classes
for /R ./lib %%a in (*.jar) do (
  set CLASSPATH=!CLASSPATH!;%%a
)
set CLASSPATH=!CLASSPATH!"

%JAVA_HOME%\bin\java %JAVA_OPTS% -classpath !CLASSPATH! password.pwm.util.MainClass %1 %2 %3 %4 %5 %6 %7 %8 %9
goto finally

:err
echo JAVA_HOME variable must be set to a valid Java JDK or JRE

:finally
endLocal