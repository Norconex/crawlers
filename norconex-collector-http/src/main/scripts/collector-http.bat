@echo off
cd %~dp0
set ROOT_DIR=%~dp0

java -Dlog4j.configuration="file:///%ROOT_DIR%log4j.properties" -Dfile.encoding=UTF8 -cp "./lib/*;./classes" com.norconex.collector.http.HttpCollector %*
