@echo off
cd %~dp0
set ROOT_DIR=%~dp0

REM Third-party libraries sometimes have to create and write to temporary files.
REM By default those are created in your system "temp" folder 
REM (usually defined under %TEMP% variable in Windows).
REM To change the temporary location those libraries will use, add the
REM following to the java command below (replacing the path):
REM
REM     -Djava.io.tmpdir="C:\temp"

REM If you are experiencing memory problems or simply wish to increase crawling
REM performance you can specify the amount of memory allocated by increasing
REM the Java heap space. You can do so by adding the following to the Java
REM command below (using 2G as an example):  
REM
REM     -Xmx2G

REM For advanced users, JMX monitoring can be enabled by adding the following 
REM to the java command below: 
REM 
REM     -DenableJMX=true


java -Dlog4j.configuration="file:///%ROOT_DIR%log4j.properties" -Dfile.encoding=UTF8 -cp "./lib/*;./classes" com.norconex.collector.http.HttpCollector %*
