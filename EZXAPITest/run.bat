echo off
if "%JAVA_HOME%" == "" goto nojava
set CLASSPATH=bin
for %%f in (lib/*.jar) do call :setlib %%f

echo JAVA_HOME=%JAVA_HOME%
echo CLASSPATH=%CLASSPATH%
%JAVA_HOME%\bin\java test.TestSockets %1
goto done

:nojava
echo Must set JAVA_HOME environment variable

:done

:setlib
set CLASSPATH=lib\%1;%CLASSPATH%
