echo off
set CLASSPATH=lib/EZX.jar;lib/log4j-1.2.15.jar;lib/clipc.jar;bin
set PATH=lib;%PATH%
echo CLASSPATH=%CLASSPATH%
echo PATH=%PATH%
java clipc.Receiver
